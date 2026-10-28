package com.example.demo.services;

import com.example.demo.Model.*;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas específicas para BLOQUE 6:
 * 6.1 Descuento atómico condicionado de stock y transición atómica PENDIENTE -> CONFIRMADO vía PedidoEstadoService.
 * Idempotencia ante reintentos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagoConfirmacionBloque6Test {

    private static final String REFERENCIA = "CUL-BLOQUE6-TEST";
    private static final String TX_ID = "tx_bloque6_999";

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
    private MongoTemplate mongoTemplate;

    @Mock
    private PedidoEstadoService pedidoEstadoService;

    private PagoConfirmacionService confirmacionService;

    @BeforeEach
    void setUp() {
        confirmacionService = new PagoConfirmacionService(
                pedidoRepository,
                ofertaRepository,
                catalogoService,
                notificacionService,
                carritoRepository,
                compraRepository
        );
        confirmacionService.setMongoTemplate(mongoTemplate);
        confirmacionService.setPedidoEstadoService(pedidoEstadoService);

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pedido crearPedido(String id, String ofertaId, double cant, long totalCentavos) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setCompraId("compra_1");
        p.setEstadoEnum(EstadoPedido.PENDIENTE);
        p.setTotal(cant * 1000.0);
        p.setSubtotalCentavos(totalCentavos);

        DatosComprador comp = new DatosComprador("user_comp", "Comprador Bloque6");
        p.setComprador(comp);

        DatosVendedor vend = new DatosVendedor("vend_1", "Finca Bloque6", true);
        p.setVendedor(vend);

        ProductoPedido item = new ProductoPedido();
        item.setProductoId(ofertaId);
        item.setNombreSnapshot("Yuca Fresca");
        item.setCantidad(cant);
        item.setPrecioUnitario(1000.0);
        item.setSubtotal(cant * 1000.0);
        p.setItems(List.of(item));

        DatosPago pago = new DatosPago("WOMPI", p.getTotal());
        pago.setEstado("PENDIENTE");
        pago.setReferencia(REFERENCIA);
        pago.setMontoCentavosFirmado(totalCentavos);
        p.setPago(pago);

        return p;
    }

    @Test
    @DisplayName("6.1 Descuento atómico de stock vía MongoTemplate findAndModify con query stock >= cant")
    void testDescuentoAtomicoExitosoConMongoTemplate() {
        long totalCentavos = 100_000L;
        Compra compra = new Compra(new DatosComprador("user_comp", "Comprador"), REFERENCIA, totalCentavos, 0L);
        compra.setId("compra_1");
        compra.setPedidoIds(List.of("ped_1"));

        Pedido p1 = crearPedido("ped_1", "of_yuca", 5.0, totalCentavos);

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1));

        OfertaVendedor ofertaModificada = new OfertaVendedor();
        ofertaModificada.setId("of_yuca");
        ofertaModificada.setStock(15.0); // antes era 20.0, descontó 5.0

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OfertaVendedor.class)))
                .thenReturn(ofertaModificada);

        PagoConfirmacionService.Resultado res = confirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", totalCentavos
        );

        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);
        assertTrue(p1.isStockDescontado());

        // Verificar que findAndModify fue llamado
        verify(mongoTemplate, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OfertaVendedor.class));

        // Verificar transición centralizada vía PedidoEstadoService
        verify(pedidoEstadoService, times(1)).transicionar(
                eq(p1),
                eq(EstadoPedido.CONFIRMADO),
                eq(ActorTransicion.SISTEMA),
                contains("Pago confirmado")
        );
    }

    @Test
    @DisplayName("6.1 Stock insuficiente en MongoTemplate: findAndModify retorna null -> pasa a EN_REVISION y notifica admin")
    void testStockInsuficienteConMongoTemplateMarcaRevision() {
        long totalCentavos = 100_000L;
        Compra compra = new Compra(new DatosComprador("user_comp", "Comprador"), REFERENCIA, totalCentavos, 0L);
        compra.setId("compra_1");
        compra.setPedidoIds(List.of("ped_1"));

        Pedido p1 = crearPedido("ped_1", "of_yuca", 10.0, totalCentavos);

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1));

        // findAndModify retorna null simulando que { stock: { $gte: 10.0 } } no hizo match
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(OfertaVendedor.class)))
                .thenReturn(null);

        PagoConfirmacionService.Resultado res = confirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", totalCentavos
        );

        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);
        assertFalse(p1.isStockDescontado());
        assertTrue(p1.isRequiereRevision());

        // Transición a EN_REVISION vía PedidoEstadoService
        verify(pedidoEstadoService, times(1)).transicionar(
                eq(p1),
                eq(EstadoPedido.EN_REVISION),
                eq(ActorTransicion.SISTEMA),
                anyString()
        );

        // Notificación al admin
        verify(notificacionService, times(1)).enviarAAdmins(
                contains("Revisión requerida: Pedido #ped_1"),
                contains("Requiere reembolso manual"),
                eq("DANGER")
        );
    }

    @Test
    @DisplayName("6.1 Idempotencia: pedido ya en CONFIRMADO no descuenta stock de nuevo")
    void testIdempotenciaNoDescuentaStockSiYaConfirmado() {
        long totalCentavos = 100_000L;
        Compra compra = new Compra(new DatosComprador("user_comp", "Comprador"), REFERENCIA, totalCentavos, 0L);
        compra.setId("compra_1");
        compra.setPedidoIds(List.of("ped_1"));

        Pedido p1 = crearPedido("ped_1", "of_yuca", 5.0, totalCentavos);
        p1.setEstadoEnum(EstadoPedido.CONFIRMADO);
        p1.setStockDescontado(true);

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1));

        PagoConfirmacionService.Resultado res = confirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", totalCentavos
        );

        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);

        // No debe haber llamado a findAndModify ni a transicionar porque ya estaba confirmado
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), eq(OfertaVendedor.class));
        verify(pedidoEstadoService, never()).transicionar(eq(p1), any(), any(), any());
    }

    @Test
    @DisplayName("6.1 Pago rechazado transiciona PENDIENTE a CANCELADO vía PedidoEstadoService")
    void testPagoRechazadoTransicionaCancelado() {
        long totalCentavos = 100_000L;
        Compra compra = new Compra(new DatosComprador("user_comp", "Comprador"), REFERENCIA, totalCentavos, 0L);
        compra.setId("compra_1");
        compra.setPedidoIds(List.of("ped_1"));

        Pedido p1 = crearPedido("ped_1", "of_yuca", 5.0, totalCentavos);

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1));

        PagoConfirmacionService.Resultado res = confirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "DECLINED", totalCentavos
        );

        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);

        verify(pedidoEstadoService, times(1)).transicionar(
                eq(p1),
                eq(EstadoPedido.CANCELADO),
                eq(ActorTransicion.SISTEMA),
                contains("Pago no aprobado"),
                eq(CanceladoPor.SISTEMA_PAGO)
        );
        assertFalse(p1.isStockDescontado());
    }
}
