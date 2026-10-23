package com.example.demo.services;

import com.example.demo.Model.Compra;
import com.example.demo.Model.Envio;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.embebidos.*;
import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagoConfirmacionServiceLogisticaTest {

    private static final String COMPRADOR_ID = "usr_comprador_99";
    private static final String REFERENCIA = "CUL-LOGISTICA-TEST01";
    private static final String TX_ID = "wompi_tx_logistica_123";

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private CatalogoService catalogoService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private EnvioRepository envioRepository;

    @InjectMocks
    private PagoConfirmacionService pagoConfirmacionService;

    @BeforeEach
    void setUp() {
        pagoConfirmacionService.setEnvioRepository(envioRepository);
        when(envioRepository.save(any(Envio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(compraRepository.save(any(Compra.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Compra crearCompra(String compraId, long totalCentavos, List<String> pedidoIds) {
        Compra c = new Compra(
                new DatosComprador(COMPRADOR_ID, "Comprador Test"),
                REFERENCIA,
                totalCentavos - 2_000_000L,
                2_000_000L // $20.000 COP envío
        );
        c.setId(compraId);
        c.setTotalCentavos(totalCentavos);
        c.setPedidoIds(pedidoIds);
        return c;
    }

    private Pedido crearPedido(String id, String compraId, String vendedorId, String vendedorNombre,
                               double stockOferta, double cantidadPedida, long envioCentavos) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setCompraId(compraId);
        p.setEstado("PENDIENTE");

        DatosVendedor dv = new DatosVendedor(vendedorId, vendedorNombre, true);
        dv.setTelefonoContacto("310000000" + id);
        p.setVendedor(dv);

        p.setDireccionEntrega(new DireccionPedido("Calle 10 # 5-20", "Cartagena", "Bolívar"));
        p.setEnvioCentavos(envioCentavos);

        DatosPago pago = new DatosPago();
        pago.setEstado("PENDIENTE");
        pago.setReferencia(REFERENCIA);
        pago.setMontoCentavosFirmado(1_000_000L + envioCentavos);
        p.setPago(pago);

        String ofertaId = "of_" + id;
        ProductoPedido item = new ProductoPedido();
        item.setProductoId(ofertaId);
        item.setNombreSnapshot("Producto " + id);
        item.setCantidad(cantidadPedida);
        item.setPrecioUnitario(10000.0);
        item.setSubtotal(cantidadPedida * 10000.0);
        p.setItems(List.of(item));

        OfertaVendedor ov = new OfertaVendedor();
        ov.setId(ofertaId);
        ov.setStock(stockOferta);
        ov.setDisponible(stockOferta > 0);
        when(ofertaRepository.findById(ofertaId)).thenReturn(Optional.of(ov));

        return p;
    }

    @Test
    @DisplayName("Aprobación de pago crea un único Envio en estado PENDIENTE_ASIGNACION")
    void testAprobacionCreaUnicoEnvio() {
        String compraId = "compra_001";
        long totalCentavos = 4_000_000L;
        Pedido p1 = crearPedido("ped_1", compraId, "vend_1", "Finca El Sol", 10.0, 2.0, 1_000_000L);
        Pedido p2 = crearPedido("ped_2", compraId, "vend_2", "Finca La Luna", 10.0, 1.0, 1_000_000L);
        Compra compra = crearCompra(compraId, totalCentavos, List.of("ped_1", "ped_2"));

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1, p2));
        when(pedidoRepository.findByCompraId(compraId)).thenReturn(List.of(p1, p2));
        when(envioRepository.existsByCompraId(compraId)).thenReturn(false);

        PagoConfirmacionService.Resultado res = pagoConfirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", totalCentavos);

        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);

        // Verificar que se guardó exactamente un Envio
        ArgumentCaptor<Envio> envioCaptor = ArgumentCaptor.forClass(Envio.class);
        verify(envioRepository, times(1)).save(envioCaptor.capture());

        Envio envioCreado = envioCaptor.getValue();
        assertNotNull(envioCreado);
        assertEquals(compraId, envioCreado.getCompraId());
        assertEquals(Envio.PENDIENTE_ASIGNACION, envioCreado.getEstado());
        assertNull(envioCreado.getTransportistaId());
        assertEquals(2, envioCreado.getParadas().size(), "Debe contener una parada por cada pedido aprobado");
        assertEquals(2_000_000L, envioCreado.getCostoTotalCentavos());
        assertEquals("Cartagena", envioCreado.getPuntoEntrega().getCiudad());
    }

    @Test
    @DisplayName("Idempotencia: si el Envio ya existe para la compra, no se duplica")
    void testEnvioIdempotenteNoDuplica() {
        String compraId = "compra_002";
        long totalCentavos = 2_000_000L;
        Pedido p1 = crearPedido("ped_3", compraId, "vend_1", "Finca El Sol", 10.0, 1.0, 1_000_000L);
        Compra compra = crearCompra(compraId, totalCentavos, List.of("ped_3"));

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1));
        when(pedidoRepository.findByCompraId(compraId)).thenReturn(List.of(p1));
        // Ya existe en la base de datos
        when(envioRepository.existsByCompraId(compraId)).thenReturn(true);

        PagoConfirmacionService.Resultado res = pagoConfirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", totalCentavos);

        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);
        // NO debe volver a invocar envioRepository.save(...)
        verify(envioRepository, never()).save(any(Envio.class));
    }

    @Test
    @DisplayName("Exclusión de pedidos EN_REVISION: el Envio solo incluye pedidos con stock suficiente y aprobados")
    void testEnvioExcluyePedidosEnRevision() {
        String compraId = "compra_003";
        long totalCentavos = 5_000_000L;
        // p1 tiene stock (disponible 10, pide 2) -> APROBADO
        Pedido p1 = crearPedido("ped_ok", compraId, "vend_1", "Finca Buena", 10.0, 2.0, 1_000_000L);
        // p2 NO tiene stock suficiente (disponible 1, pide 5) -> Pasará a EN_REVISION
        Pedido p2 = crearPedido("ped_sin_stock", compraId, "vend_2", "Finca Agotada", 1.0, 5.0, 1_000_000L);

        Compra compra = crearCompra(compraId, totalCentavos, List.of("ped_ok", "ped_sin_stock"));

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1, p2));
        when(pedidoRepository.findByCompraId(compraId)).thenReturn(List.of(p1, p2));
        when(envioRepository.existsByCompraId(compraId)).thenReturn(false);

        PagoConfirmacionService.Resultado res = pagoConfirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", totalCentavos);

        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);

        // Verificar el envío creado
        ArgumentCaptor<Envio> envioCaptor = ArgumentCaptor.forClass(Envio.class);
        verify(envioRepository, times(1)).save(envioCaptor.capture());

        Envio envioCreado = envioCaptor.getValue();
        assertNotNull(envioCreado);
        assertEquals(1, envioCreado.getParadas().size(), "Solo debe contener la parada del pedido aprobado");
        assertEquals("ped_ok", envioCreado.getParadas().get(0).getPedidoId());
        assertEquals("Finca Buena", envioCreado.getParadas().get(0).getVendedorNombre());
    }
}
