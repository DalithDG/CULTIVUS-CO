package com.example.demo.services;

import com.example.demo.Model.ActorTransicion;
import com.example.demo.Model.CanceladoPor;
import com.example.demo.Model.EstadoPedido;
import com.example.demo.Model.ModalidadEntrega;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.EnvioRepository;
import com.example.demo.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendedorServiceBloque2Test {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private PedidoEstadoService pedidoEstadoService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private EnvioRepository envioRepository;

    @Mock
    private TransportistaService transportistaService;

    @InjectMocks
    private VendedorService vendedorService;

    private static final String VENDEDOR_ID = "vend_123";
    private static final String OTRO_VENDEDOR_ID = "vend_impostor";

    private Pedido crearPedido(String id, EstadoPedido estado, ModalidadEntrega modalidad, String vendedorId) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setEstadoEnum(estado);
        p.setModalidadEntrega(modalidad);
        DatosVendedor dv = new DatosVendedor();
        dv.setId(vendedorId);
        dv.setNombre("Finca Orgánica");
        p.setVendedor(dv);
        return p;
    }

    @Test
    @DisplayName("2.5 Visibilidad: PENDIENTE y CANCELADO por SISTEMA_PAGO no son visibles, CANCELADO por VENDEDOR sí")
    void testVisibilidadVentas() {
        Pedido pendiente = crearPedido("p1", EstadoPedido.PENDIENTE, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        assertFalse(vendedorService.esVentaVisible(pendiente), "PENDIENTE no debe ser visible");

        Pedido canceladoPago = crearPedido("p2", EstadoPedido.CANCELADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        canceladoPago.setCanceladoPor(CanceladoPor.SISTEMA_PAGO);
        assertFalse(vendedorService.esVentaVisible(canceladoPago), "CANCELADO por SISTEMA_PAGO debe ser oculto");

        Pedido canceladoVendedor = crearPedido("p3", EstadoPedido.CANCELADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        canceladoVendedor.setCanceladoPor(CanceladoPor.VENDEDOR);
        assertTrue(vendedorService.esVentaVisible(canceladoVendedor), "CANCELADO por VENDEDOR sí debe ser visible");

        Pedido confirmado = crearPedido("p4", EstadoPedido.CONFIRMADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        assertTrue(vendedorService.esVentaVisible(confirmado), "CONFIRMADO debe ser visible");

        Pedido entregado = crearPedido("p5", EstadoPedido.ENTREGADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        assertTrue(vendedorService.esVentaVisible(entregado), "ENTREGADO debe ser visible");
    }

    @Test
    @DisplayName("2.6 Seguridad: Falla cerrado si el vendedor no es el dueño del pedido")
    void testSeguridadActualizarEstado() {
        Pedido p = crearPedido("p10", EstadoPedido.CONFIRMADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        when(pedidoRepository.findById("p10")).thenReturn(Optional.of(p));

        assertThrows(SecurityException.class, () ->
                vendedorService.actualizarEstadoPedido("p10", "EN_PROCESO", OTRO_VENDEDOR_ID)
        );
        verify(pedidoEstadoService, never()).transicionar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("2.6 Transición exitosa del vendedor: CONFIRMADO a EN_PROCESO a LISTO_PARA_RECOGER")
    void testTransicionVendedorExitosa() {
        Pedido p = crearPedido("p20", EstadoPedido.CONFIRMADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        when(pedidoRepository.findById("p20")).thenReturn(Optional.of(p));
        doAnswer(inv -> {
            p.setEstadoEnum(inv.getArgument(1));
            return p;
        }).when(pedidoEstadoService).transicionar(eq(p), any(), any(), any());

        vendedorService.actualizarEstadoPedido("p20", "EN_PROCESO", VENDEDOR_ID);
        verify(pedidoEstadoService).transicionar(p, EstadoPedido.EN_PROCESO, ActorTransicion.VENDEDOR, "Actualización realizada por el vendedor");

        vendedorService.actualizarEstadoPedido("p20", Pedido.LISTO_PARA_RECOGER, VENDEDOR_ID);
        verify(pedidoEstadoService).transicionar(p, EstadoPedido.LISTO_PARA_RECOGER, ActorTransicion.VENDEDOR, "Actualización realizada por el vendedor");
    }

    @Test
    @DisplayName("2.2 Validación de código de retiro rechazada: modalidad retirada")
    void testValidacionCodigoRetiroRechazada() {
        assertThrows(UnsupportedOperationException.class, () ->
                vendedorService.validarCodigoRetiroYEntregar("p30", "458921", VENDEDOR_ID));
    }

    @Test
    @DisplayName("2.3 Total ventas: se calcula desde subtotalProductos (sin envío) y excluye CANCELADO y EN_REVISION")
    void testCalculoTotalVentas() {
        Pedido p1 = crearPedido("p41", EstadoPedido.CONFIRMADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        p1.setSubtotalCentavos(5000000L); // $50,000 COP
        p1.setEnvioCentavos(1200000L);    // $12,000 COP envío
        p1.setTotal(62000.0);

        Pedido p2 = crearPedido("p42", EstadoPedido.ENTREGADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        p2.setSubtotalCentavos(3000000L); // $30,000 COP
        p2.setEnvioCentavos(1000000L);    // $10,000 COP envío
        p2.setTotal(40000.0);

        Pedido pRevision = crearPedido("p43", EstadoPedido.EN_REVISION, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        pRevision.setSubtotalCentavos(2000000L); // no debe sumar

        Pedido pCancelado = crearPedido("p44", EstadoPedido.CANCELADO, ModalidadEntrega.DOMICILIO, VENDEDOR_ID);
        pCancelado.setCanceladoPor(CanceladoPor.VENDEDOR);
        pCancelado.setSubtotalCentavos(1500000L); // no debe sumar

        when(pedidoRepository.findByVendedor_IdOrderByFechaPedidoDesc(VENDEDOR_ID))
                .thenReturn(List.of(p1, p2, pRevision, pCancelado));

        double total = vendedorService.calcularTotalVentas(VENDEDOR_ID);
        // Debe ser 50,000 + 30,000 = 80,000 (sin los fletes de envío y sin cancelados ni revisión)
        assertEquals(80000.0, total, 0.001);

        double totalEntregadas = vendedorService.calcularTotalVentasEntregadas(VENDEDOR_ID);
        assertEquals(30000.0, totalEntregadas, 0.001);

        double totalEnCurso = vendedorService.calcularTotalVentasEnCurso(VENDEDOR_ID);
        assertEquals(50000.0, totalEnCurso, 0.001);
    }
}
