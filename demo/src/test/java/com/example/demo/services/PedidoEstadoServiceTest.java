package com.example.demo.services;

import com.example.demo.Model.*;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.EnvioRepository;
import com.example.demo.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoEstadoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private EnvioRepository envioRepository;

    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private PedidoEstadoService pedidoEstadoService;

    @BeforeEach
    void setUp() {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pedido crearPedido(String id, EstadoPedido estado, ModalidadEntrega modalidad) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setEstadoEnum(estado);
        p.setModalidadEntrega(modalidad);
        p.setCompraId("compra_1");
        p.setComprador(new DatosComprador("comp_1", "Ana Cliente"));
        p.setVendedor(new DatosVendedor("vend_1", "Finca Verde", true));
        return p;
    }

    @Test
    @DisplayName("1.1 y 1.2: Normalización de legados en EstadoPedido y Pedido")
    void testNormalizacionLegados() {
        assertEquals(EstadoPedido.EN_PROCESO, EstadoPedido.desde("PROCESANDO"));
        assertEquals(EstadoPedido.ENTREGADO, EstadoPedido.desde("COMPLETADO"));
        assertEquals(EstadoPedido.PENDIENTE, EstadoPedido.desde("PENDIENTE_PAGO"));

        Pedido p = new Pedido();
        p.setEstado("PROCESANDO");
        assertEquals("EN_PROCESO", p.getEstado());
        assertEquals(EstadoPedido.EN_PROCESO, p.getEstadoEnum());

        p.setEstado("COMPLETADO");
        assertEquals("ENTREGADO", p.getEstado());
        assertEquals(EstadoPedido.ENTREGADO, p.getEstadoEnum());

        p.setEstado("PENDIENTE_PAGO");
        assertEquals("PENDIENTE", p.getEstado());
        assertEquals(EstadoPedido.PENDIENTE, p.getEstadoEnum());
    }

    @Test
    @DisplayName("1.1: Transiciones VENDEDOR en modalidad DOMICILIO")
    void testTransicionesVendedorDomicilio() {
        Pedido p = crearPedido("p1", EstadoPedido.CONFIRMADO, ModalidadEntrega.DOMICILIO);

        // CONFIRMADO -> EN_PROCESO (válido)
        pedidoEstadoService.transicionar(p, EstadoPedido.EN_PROCESO, ActorTransicion.VENDEDOR, null, null);
        assertEquals(EstadoPedido.EN_PROCESO, p.getEstadoEnum());

        // EN_PROCESO -> LISTO_PARA_RECOGER (válido)
        pedidoEstadoService.transicionar(p, EstadoPedido.LISTO_PARA_RECOGER, ActorTransicion.VENDEDOR, null, null);
        assertEquals(EstadoPedido.LISTO_PARA_RECOGER, p.getEstadoEnum());

        // Vendedor NO puede marcar ENVIADO ni ENTREGADO en DOMICILIO
        assertThrows(IllegalStateException.class, () ->
                pedidoEstadoService.transicionar(p, EstadoPedido.ENVIADO, ActorTransicion.VENDEDOR, null, null));
        assertThrows(IllegalStateException.class, () ->
                pedidoEstadoService.transicionar(p, EstadoPedido.ENTREGADO, ActorTransicion.VENDEDOR, null, null));
    }

    @Test
    @DisplayName("1.1: El vendedor NO puede marcar ENTREGADO directamente en DOMICILIO")
    void testVendedorNoPuedeMarcarEntregadoDirecto() {
        Pedido p = crearPedido("p2", EstadoPedido.CONFIRMADO, ModalidadEntrega.DOMICILIO);

        // CONFIRMADO -> EN_PROCESO -> LISTO_PARA_RECOGER
        pedidoEstadoService.transicionar(p, EstadoPedido.EN_PROCESO, ActorTransicion.VENDEDOR, null, null);
        pedidoEstadoService.transicionar(p, EstadoPedido.LISTO_PARA_RECOGER, ActorTransicion.VENDEDOR, null, null);
        assertEquals(EstadoPedido.LISTO_PARA_RECOGER, p.getEstadoEnum());

        // Vendedor intenta pasar a ENVIADO -> debe fallar (lo hace el transportista)
        assertThrows(IllegalStateException.class, () ->
                pedidoEstadoService.transicionar(p, EstadoPedido.ENVIADO, ActorTransicion.VENDEDOR, null, null));

        // Vendedor intenta pasar a ENTREGADO -> debe fallar (lo hace el transportista)
        assertThrows(IllegalStateException.class, () ->
                pedidoEstadoService.transicionar(p, EstadoPedido.ENTREGADO, ActorTransicion.VENDEDOR, "Entrega", null));
    }

    @Test
    @DisplayName("1.1: Transiciones TRANSPORTISTA en DOMICILIO")
    void testTransicionesTransportista() {
        Pedido pDom = crearPedido("p3", EstadoPedido.LISTO_PARA_RECOGER, ModalidadEntrega.DOMICILIO);

        // LISTO_PARA_RECOGER -> ENVIADO (válido)
        pedidoEstadoService.transicionar(pDom, EstadoPedido.ENVIADO, ActorTransicion.TRANSPORTISTA, null, null);
        assertEquals(EstadoPedido.ENVIADO, pDom.getEstadoEnum());

        // ENVIADO -> ENTREGADO (válido)
        pedidoEstadoService.transicionar(pDom, EstadoPedido.ENTREGADO, ActorTransicion.TRANSPORTISTA, null, null);
        assertEquals(EstadoPedido.ENTREGADO, pDom.getEstadoEnum());
    }

    @Test
    @DisplayName("1.4: Cancelación registra canceladoPor y motivoCancelacion")
    void testCancelacionConActorYMotivo() {
        Pedido p = crearPedido("p5", EstadoPedido.CONFIRMADO, ModalidadEntrega.DOMICILIO);

        pedidoEstadoService.transicionar(p, EstadoPedido.CANCELADO, ActorTransicion.VENDEDOR,
                "Sin cosecha disponible", CanceladoPor.VENDEDOR);

        assertEquals(EstadoPedido.CANCELADO, p.getEstadoEnum());
        assertEquals(CanceladoPor.VENDEDOR, p.getCanceladoPor());
        assertEquals("Sin cosecha disponible", p.getMotivoCancelacion());
        assertNotNull(p.getFechaCancelacion());
    }

    @Test
    @DisplayName("1.5: Compra pasa a COMPLETADA cuando todos sus pedidos están ENTREGADO o CANCELADO")
    void testCompraPasaACompletada() {
        Compra compra = new Compra();
        compra.setId("compra_100");
        compra.setEstado(Compra.PAGADA);

        Pedido p1 = crearPedido("ped_1", EstadoPedido.ENTREGADO, ModalidadEntrega.DOMICILIO);
        p1.setCompraId("compra_100");
        Pedido p2 = crearPedido("ped_2", EstadoPedido.CANCELADO, ModalidadEntrega.DOMICILIO);
        p2.setCompraId("compra_100");

        when(compraRepository.findById("compra_100")).thenReturn(Optional.of(compra));
        when(pedidoRepository.findByCompraId("compra_100")).thenReturn(List.of(p1, p2));

        pedidoEstadoService.actualizarEstadoCompraSiAplica("compra_100");

        assertEquals(Compra.COMPLETADA, compra.getEstado());
        verify(compraRepository, times(1)).save(compra);
    }

    @Test
    @DisplayName("1.6: getPasoTracking oculta stepper en CANCELADO y ubica EN_REVISION en paso 2")
    void testPasoTracking() {
        Pedido cancelado = crearPedido("pc", EstadoPedido.CANCELADO, ModalidadEntrega.DOMICILIO);
        assertEquals(0, cancelado.getPasoTracking());
        assertFalse(cancelado.isMostrarStepper());

        Pedido revision = crearPedido("pr", EstadoPedido.EN_REVISION, ModalidadEntrega.DOMICILIO);
        assertEquals(2, revision.getPasoTracking());
        assertTrue(revision.isMostrarStepper());

        Pedido confirmado = crearPedido("pconf", EstadoPedido.CONFIRMADO, ModalidadEntrega.DOMICILIO);
        assertEquals(2, confirmado.getPasoTracking());

        Pedido listo = crearPedido("plt", EstadoPedido.LISTO_PARA_RECOGER, ModalidadEntrega.DOMICILIO);
        assertEquals(4, listo.getPasoTracking());

        Pedido enviado = crearPedido("penv", EstadoPedido.ENVIADO, ModalidadEntrega.DOMICILIO);
        assertEquals(5, enviado.getPasoTracking());

        Pedido entregado = crearPedido("pet", EstadoPedido.ENTREGADO, ModalidadEntrega.DOMICILIO);
        assertEquals(6, entregado.getPasoTracking());
    }
}
