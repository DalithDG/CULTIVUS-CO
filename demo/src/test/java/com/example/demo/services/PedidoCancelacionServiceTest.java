package com.example.demo.services;

import com.example.demo.Model.*;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoCancelacionServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private PedidoEstadoService pedidoEstadoService;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private CatalogoService catalogoService;

    @Mock
    private BilleteraService billeteraService;

    @Mock
    private TransportistaService transportistaService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PedidoCancelacionService pedidoCancelacionService;

    private Pedido pedido;
    private OfertaVendedor oferta;

    @BeforeEach
    void setUp() {
        pedido = new Pedido();
        pedido.setId("ped_100");
        pedido.setModalidadEntrega(ModalidadEntrega.DOMICILIO);
        pedido.setEstadoEnum(EstadoPedido.CONFIRMADO);
        pedido.setStockDescontado(true);
        pedido.setSubtotalCentavos(5000000L); // 50.000 COP
        pedido.setEnvioCentavos(800000L);    // 8.000 COP

        DatosComprador comprador = new DatosComprador("comp_1", "Comprador Prueba");
        pedido.setComprador(comprador);

        DatosVendedor vendedor = new DatosVendedor();
        vendedor.setId("vend_1");
        vendedor.setNombre("Finca La Esperanza");
        pedido.setVendedor(vendedor);

        ProductoPedido item = new ProductoPedido();
        item.setProductoId("oferta_1");
        item.setCantidad(5.0);
        item.setPrecioUnitario(10000.0);
        item.setSubtotal(50000.0);
        pedido.setItems(List.of(item));

        oferta = new OfertaVendedor();
        oferta.setId("oferta_1");
        oferta.setStock(10.0);
        oferta.setDisponible(true);
        oferta.setProductoCatalogoId("cat_1");

        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Cancelación exitosa restaura stock, revierte billetera, programa reembolso y retira parada logística")
    void testCancelacionCompletaExito() {
        when(ofertaRepository.findById("oferta_1")).thenReturn(Optional.of(oferta));

        Pedido cancelado = pedidoCancelacionService.cancelarPedido(
                pedido,
                "Falta de inventario por helada",
                ActorTransicion.VENDEDOR,
                CanceladoPor.VENDEDOR
        );

        assertNotNull(cancelado);
        assertEquals(CanceladoPor.VENDEDOR, cancelado.getCanceladoPor());
        assertEquals("Falta de inventario por helada", cancelado.getMotivoCancelacion());
        assertFalse(cancelado.isStockDescontado(), "Stock descontado debe marcarse en false tras restauración");
        assertTrue(cancelado.isRequiereReembolso());
        assertEquals(5800000L, cancelado.getMontoReembolsoCentavos(), "Reembolso debe ser subtotal + envío (58.000 COP)");

        // Verificar restauración de stock
        assertEquals(15.0, oferta.getStock());
        verify(ofertaRepository).save(oferta);
        verify(catalogoService).actualizarEstadisticas("cat_1");

        // Verificar reversión de billetera
        verify(billeteraService).revertirVentaCancelada(pedido);

        // Verificar que retira la parada de transportista porque era a DOMICILIO
        verify(transportistaService).removerParadaPorPedidoCancelado("ped_100");

        // Verificar llamada a máquina de estados
        verify(pedidoEstadoService).transicionar(
                eq(pedido),
                eq(EstadoPedido.CANCELADO),
                eq(ActorTransicion.VENDEDOR),
                eq("Falta de inventario por helada"),
                eq(CanceladoPor.VENDEDOR)
        );
    }

    @Test
    @DisplayName("Cancelación de pedido no restaura stock si stockDescontado era false")
    void testCancelacionNoRestauraStockSiNoFueDescontado() {
        pedido.setStockDescontado(false);

        pedidoCancelacionService.cancelarPedido(
                pedido,
                "Cancelado por administrador",
                ActorTransicion.ADMIN,
                CanceladoPor.ADMIN
        );

        // Debe remover parada logística del pedido
        verify(transportistaService, times(1)).removerParadaPorPedidoCancelado(pedido.getId());
        // No debe restaurar stock si stockDescontado era false
        verify(ofertaRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("No permite cancelar si el pedido ya fue enviado o entregado")
    void testNoPermiteCancelarEnviadoOEntregado() {
        pedido.setEstadoEnum(EstadoPedido.ENVIADO);
        assertThrows(IllegalStateException.class, () ->
                pedidoCancelacionService.cancelarPedido(pedido, "Motivo", ActorTransicion.VENDEDOR, CanceladoPor.VENDEDOR)
        );

        pedido.setEstadoEnum(EstadoPedido.ENTREGADO);
        assertThrows(IllegalStateException.class, () ->
                pedidoCancelacionService.cancelarPedido(pedido, "Motivo", ActorTransicion.VENDEDOR, CanceladoPor.VENDEDOR)
        );
    }

    @Test
    @DisplayName("Cancelación es idempotente si el pedido ya estaba en CANCELADO")
    void testCancelacionIdempotente() {
        pedido.setEstadoEnum(EstadoPedido.CANCELADO);

        Pedido resultado = pedidoCancelacionService.cancelarPedido(
                pedido,
                "Nuevo motivo",
                ActorTransicion.VENDEDOR,
                CanceladoPor.VENDEDOR
        );

        assertSame(pedido, resultado);
        verify(pedidoEstadoService, never()).transicionar(any(), any(), any(), any(), any());
        verify(billeteraService, never()).revertirVentaCancelada(any());
    }
}
