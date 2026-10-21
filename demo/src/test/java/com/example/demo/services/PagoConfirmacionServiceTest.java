package com.example.demo.services;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.Compra;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para US-03 y US-21:
 * - US-03:
 *   1. Stock solo se descuenta al aprobar (APPROVED) en una sola operación.
 *   2. Pago rechazado/fallido deja pedidos CANCELADO sin tocar stock y envía una sola notificación al comprador.
 *   3. Si al confirmar el stock ya no alcanza, marca EN_REVISION (requiereRevision=true), no descuenta stock en silencio y notifica a admin.
 *   4. Una sola notificación al comprador por compra en pago fallido.
 * - US-21:
 *   5. Compra agrupada multi-vendedor: una Compra, un Pedido por vendedor, un solo total firmado en centavos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagoConfirmacionServiceTest {

    private static final String COMPRADOR_ID = "usr_comprador_99";
    private static final String REFERENCIA = "CUL-1710000000000-TEST01";
    private static final String TX_ID = "wompi_tx_987654";

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

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ofertaRepository.save(any(OfertaVendedor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OfertaVendedor crearOferta(String id, double stock) {
        OfertaVendedor of = new OfertaVendedor();
        of.setId(id);
        of.setStock(stock);
        of.setDisponible(stock > 0);
        return of;
    }

    private Pedido crearSubPedido(String pedidoId, String vendedorId, String compraId,
                                  String ofertaId, String nombreProd, double cantidad,
                                  double precioUnitario, long subtotalCentavos, long montoFirmadoTotal) {
        Pedido p = new Pedido();
        p.setId(pedidoId);
        p.setCompraId(compraId);
        p.setSubtotalCentavos(subtotalCentavos);
        p.setTotal(cantidad * precioUnitario);
        p.setEstado("PENDIENTE");

        DatosComprador comprador = new DatosComprador(COMPRADOR_ID, "Juan Perez");
        p.setComprador(comprador);

        DatosVendedor vendedor = new DatosVendedor(vendedorId, "Finca " + vendedorId, true);
        p.setVendedor(vendedor);

        ProductoPedido item = new ProductoPedido();
        item.setProductoId(ofertaId);
        item.setNombreSnapshot(nombreProd);
        item.setCantidad(cantidad);
        item.setPrecioUnitario(precioUnitario);
        item.setSubtotal(cantidad * precioUnitario);
        p.setItems(List.of(item));

        DatosPago pago = new DatosPago("WOMPI", p.getTotal());
        pago.setEstado("PENDIENTE");
        pago.setReferencia(REFERENCIA);
        pago.setMontoCentavosFirmado(montoFirmadoTotal);
        p.setPago(pago);

        return p;
    }

    @Test
    @DisplayName("US-03 #1: Stock solo se descuenta al aprobar (APPROVED) en una sola operación")
    void testStockSoloSeDescuentaAlAprobar() {
        // Given: 2 ofertas de distintos vendedores
        OfertaVendedor ofertaA = crearOferta("of_a", 10.0);
        OfertaVendedor ofertaB = crearOferta("of_b", 8.0);
        when(ofertaRepository.findById("of_a")).thenReturn(Optional.of(ofertaA));
        when(ofertaRepository.findById("of_b")).thenReturn(Optional.of(ofertaB));

        // Subtotal A = 20,000 COP (2,000,000 centavos); Subtotal B = 10,000 COP (1,000,000 centavos)
        // Envío = 5,000 COP (500,000 centavos) -> Total firmado = 3,500,000 centavos
        long totalCentavos = 3_500_000L;
        String compraId = "compra_123";

        Compra compra = new Compra(
                new DatosComprador(COMPRADOR_ID, "Juan Perez"),
                REFERENCIA,
                3_000_000L,
                500_000L
        );
        compra.setId(compraId);
        compra.setPedidoIds(List.of("ped_1", "ped_2"));

        Pedido ped1 = crearSubPedido("ped_1", "vend_1", compraId, "of_a", "Tomate Chonto", 2.0, 10000.0, 2_000_000L, totalCentavos);
        Pedido ped2 = crearSubPedido("ped_2", "vend_2", compraId, "of_b", "Papa Criolla", 3.0, 3333.33, 1_000_000L, totalCentavos);

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(ped1, ped2));
        when(carritoRepository.findByUsuarioId(COMPRADOR_ID)).thenReturn(Optional.of(new Carrito(COMPRADOR_ID)));

        // When: Pasarela confirma APPROVED
        PagoConfirmacionService.Resultado res = confirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", totalCentavos
        );

        // Then:
        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);

        // Stock descontado correctamente
        assertEquals(8.0, ofertaA.getStock(), 0.001);
        assertEquals(5.0, ofertaB.getStock(), 0.001);
        verify(ofertaRepository).save(ofertaA);
        verify(ofertaRepository).save(ofertaB);

        // Pedidos confirmados
        assertEquals("CONFIRMADO", ped1.getEstado());
        assertFalse(ped1.isRequiereRevision());
        assertEquals("CONFIRMADO", ped2.getEstado());
        assertFalse(ped2.isRequiereRevision());

        // Compra pagada
        assertEquals(Compra.PAGADA, compra.getEstado());
        assertEquals(TX_ID, compra.getTransaccionId());
        assertFalse(compra.isRequiereRevision());

        // Carrito eliminado
        verify(carritoRepository).delete(any(Carrito.class));

        // Notificaciones: 1 a cada vendedor por su venta, 1 al comprador por la compra
        verify(notificacionService).enviar(eq("vend_1"), anyString(), anyString(), eq("SUCCESS"));
        verify(notificacionService).enviar(eq("vend_2"), anyString(), anyString(), eq("SUCCESS"));
        verify(notificacionService).enviar(eq(COMPRADOR_ID), eq("Pedido Confirmado"), anyString(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("US-03 #2 & #4: Rechazado (DECLINED/ERROR) deja CANCELADO sin tocar stock y envía una sola notificación al comprador")
    void testRechazadoDejaCanceladoSinTocarStockYNotificacionUnica() {
        // Given: Ofertas con stock inicial
        OfertaVendedor ofertaA = crearOferta("of_a", 15.0);
        OfertaVendedor ofertaB = crearOferta("of_b", 12.0);
        when(ofertaRepository.findById("of_a")).thenReturn(Optional.of(ofertaA));
        when(ofertaRepository.findById("of_b")).thenReturn(Optional.of(ofertaB));

        long totalCentavos = 2_500_000L;
        String compraId = "compra_456";

        Compra compra = new Compra(
                new DatosComprador(COMPRADOR_ID, "Maria Gomez"),
                REFERENCIA,
                2_000_000L,
                500_000L
        );
        compra.setId(compraId);
        compra.setPedidoIds(List.of("ped_a", "ped_b"));

        Pedido ped1 = crearSubPedido("ped_a", "vend_1", compraId, "of_a", "Cebolla", 4.0, 2500.0, 1_000_000L, totalCentavos);
        Pedido ped2 = crearSubPedido("ped_b", "vend_2", compraId, "of_b", "Lechuga", 5.0, 2000.0, 1_000_000L, totalCentavos);

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(ped1, ped2));

        // When: Pasarela responde DECLINED
        PagoConfirmacionService.Resultado res = confirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "DECLINED", totalCentavos
        );

        // Then:
        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);

        // NO se toca el stock bajo ninguna circunstancia
        verify(ofertaRepository, never()).save(any(OfertaVendedor.class));
        assertEquals(15.0, ofertaA.getStock());
        assertEquals(12.0, ofertaB.getStock());

        // Pedidos cancelados
        assertEquals("CANCELADO", ped1.getEstado());
        assertEquals("CANCELADO", ped2.getEstado());
        assertEquals("FALLIDO", ped1.getPago().getEstado());
        assertEquals("FALLIDO", ped2.getPago().getEstado());

        // Compra rechazada
        assertEquals(Compra.RECHAZADA, compra.getEstado());

        // Carrito NO se borra
        verify(carritoRepository, never()).delete(any());

        // UNA SOLA notificación al comprador por compra (no una por cada sub-pedido)
        verify(notificacionService, times(1)).enviar(
                eq(COMPRADOR_ID),
                eq("Pago No Aprobado"),
                contains("no fue aprobado"),
                eq("WARNING")
        );
        // Vendedores no reciben notificación de venta
        verify(notificacionService, never()).enviar(eq("vend_1"), anyString(), anyString(), anyString());
        verify(notificacionService, never()).enviar(eq("vend_2"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("US-03 #3: Stock insuficiente al confirmar marca EN_REVISION, no deja en 0 y notifica admin")
    void testStockInsuficienteAlConfirmarMarcaEnRevisionYNotificaAdmin() {
        // Given: Oferta A con solo 1 unidad disponible, pero el pedido requiere 3!
        // Oferta B con suficiente stock (10 unidades, requiere 2)
        OfertaVendedor ofertaA = crearOferta("of_insuficiente", 1.0);
        OfertaVendedor ofertaB = crearOferta("of_ok", 10.0);
        when(ofertaRepository.findById("of_insuficiente")).thenReturn(Optional.of(ofertaA));
        when(ofertaRepository.findById("of_ok")).thenReturn(Optional.of(ofertaB));

        long totalCentavos = 4_000_000L;
        String compraId = "compra_stock_fail";

        Compra compra = new Compra(
                new DatosComprador(COMPRADOR_ID, "Carlos Ruiz"),
                REFERENCIA,
                3_500_000L,
                500_000L
        );
        compra.setId(compraId);
        compra.setPedidoIds(List.of("ped_insuf", "ped_suf"));

        // Pedido 1: requiere 3 unidades de ofertaA (solo hay 1!)
        Pedido pedInsuf = crearSubPedido("ped_insuf", "vend_1", compraId, "of_insuficiente", "Fresas Orgánicas", 3.0, 5000.0, 1_500_000L, totalCentavos);
        // Pedido 2: requiere 2 unidades de ofertaB (hay 10)
        Pedido pedSuf = crearSubPedido("ped_suf", "vend_2", compraId, "of_ok", "Zanahorias", 2.0, 10000.0, 2_000_000L, totalCentavos);

        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(pedInsuf, pedSuf));

        // When: Wompi aprueba el cobro (APPROVED)
        PagoConfirmacionService.Resultado res = confirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", totalCentavos
        );

        // Then:
        assertEquals(PagoConfirmacionService.Resultado.APLICADO, res);

        // 1. Pedido insuficiente: marcado EN_REVISION, requiereRevision=true, NO deja en 0
        assertEquals(Pedido.EN_REVISION, pedInsuf.getEstado());
        assertTrue(pedInsuf.isRequiereRevision());
        assertNotNull(pedInsuf.getMotivoRevision());
        assertTrue(pedInsuf.getMotivoRevision().contains("Stock insuficiente"));
        // El stock de Oferta A NO se modificó (permanece en 1.0, no se puso en 0 en silencio)
        assertEquals(1.0, ofertaA.getStock());

        // 2. Pedido suficiente: CONFIRMADO y stock descontado
        assertEquals("CONFIRMADO", pedSuf.getEstado());
        assertFalse(pedSuf.isRequiereRevision());
        assertEquals(8.0, ofertaB.getStock());
        verify(ofertaRepository).save(ofertaB);

        // 3. Admin fue notificado para revisión y reembolso manual
        verify(notificacionService, times(1)).enviarAAdmins(
                contains("Revisión requerida: Pedido #ped_insuf"),
                contains("Requiere reembolso manual"),
                eq("DANGER")
        );

        // 4. Compra queda PAGADA pero con bandera de revisión activa
        assertEquals(Compra.PAGADA, compra.getEstado());
        assertTrue(compra.isRequiereRevision());

        // 5. Comprador notificado de compra aprobada con revisión
        verify(notificacionService).enviar(
                eq(COMPRADOR_ID),
                eq("Compra Aprobada con Revisión"),
                anyString(),
                eq("WARNING")
        );
    }

    @Test
    @DisplayName("US-21 #5: Compra multi-vendedor agrupada con una Compra, un Pedido por vendedor y un solo total firmado")
    void testCompraAgrupadaMultiVendedor() {
        String compraId = "compra_multi_vend_999";
        long subtotal1 = 1_500_000L;
        long subtotal2 = 2_000_000L;
        long subtotal3 = 1_000_000L;
        long envio = 500_000L;
        long totalFirmado = subtotal1 + subtotal2 + subtotal3 + envio; // 5,000,000 centavos ($50,000 COP)

        DatosComprador comprador = new DatosComprador(COMPRADOR_ID, "Ana Torres");
        Compra compra = new Compra(comprador, REFERENCIA, subtotal1 + subtotal2 + subtotal3, envio);
        compra.setId(compraId);

        Pedido pedVendedor1 = crearSubPedido("ped_v1", "vendedor_1", compraId, "of_1", "Yuca", 5.0, 3000.0, subtotal1, totalFirmado);
        Pedido pedVendedor2 = crearSubPedido("ped_v2", "vendedor_2", compraId, "of_2", "Plátano", 10.0, 2000.0, subtotal2, totalFirmado);
        Pedido pedVendedor3 = crearSubPedido("ped_v3", "vendedor_3", compraId, "of_3", "Aguacate", 2.0, 5000.0, subtotal3, totalFirmado);

        compra.setPedidoIds(List.of("ped_v1", "ped_v2", "ped_v3"));

        // Verificaciones de estructura US-21
        assertEquals(compraId, pedVendedor1.getCompraId());
        assertEquals(compraId, pedVendedor2.getCompraId());
        assertEquals(compraId, pedVendedor3.getCompraId());

        assertEquals(subtotal1, pedVendedor1.getSubtotalCentavos());
        assertEquals(subtotal2, pedVendedor2.getSubtotalCentavos());
        assertEquals(subtotal3, pedVendedor3.getSubtotalCentavos());

        assertEquals(totalFirmado, compra.getTotalCentavos());
        assertEquals(50000.0, compra.getTotal());

        // Todas comparten la misma referencia de Wompi y el mismo total firmado
        assertEquals(compra.getReferenciaPago(), pedVendedor1.getPago().getReferencia());
        assertEquals(compra.getReferenciaPago(), pedVendedor2.getPago().getReferencia());
        assertEquals(compra.getReferenciaPago(), pedVendedor3.getPago().getReferencia());

        assertEquals(totalFirmado, pedVendedor1.getPago().getMontoCentavosFirmado());
        assertEquals(totalFirmado, pedVendedor2.getPago().getMontoCentavosFirmado());
        assertEquals(totalFirmado, pedVendedor3.getPago().getMontoCentavosFirmado());
    }

    @Test
    @DisplayName("US-03: Rechazo si el monto recibido no coincide con el total firmado de la compra")
    void testRechazaSiMontoEnCentavosNoCoincide() {
        long totalEsperado = 3_000_000L;
        long montoAlterado = 2_000_000L; // Monto menor

        Compra compra = new Compra(
                new DatosComprador(COMPRADOR_ID, "Pedro"),
                REFERENCIA,
                2_500_000L,
                500_000L
        );
        compra.setTotalCentavos(totalEsperado);
        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(compra));

        Pedido p = crearSubPedido("p_mismatch", "v_1", "c_1", "of_1", "Mango", 1.0, 25000.0, 2_500_000L, totalEsperado);
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p));

        PagoConfirmacionService.Resultado res = confirmacionService.aplicarResultado(
                REFERENCIA, TX_ID, "APPROVED", montoAlterado
        );

        assertEquals(PagoConfirmacionService.Resultado.MONTO_NO_COINCIDE, res);
        // Pedido no fue aprobado
        assertEquals("PENDIENTE", p.getEstado());
        assertEquals(Compra.PENDIENTE, compra.getEstado());
    }
}
