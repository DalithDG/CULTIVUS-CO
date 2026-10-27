package com.example.demo.services;

import com.example.demo.Model.*;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceBloque5Test {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private PedidoCancelacionService pedidoCancelacionService;

    @Mock
    private PedidoEstadoService pedidoEstadoService;

    @InjectMocks
    private AdminService adminService;

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pedido = new Pedido();
        pedido.setId("ped_adm_1");
        pedido.setComprador(new DatosComprador("comp_1", "Ana"));
        pedido.setVendedor(new DatosVendedor("vend_1", "Finca Sol", true));
        pedido.setEstadoEnum(EstadoPedido.PENDIENTE);

        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pedidoRepository.findById("ped_adm_1")).thenReturn(Optional.of(pedido));
    }

    @Test
    @DisplayName("5.1: Admin actualizando estado a CANCELADO delega en PedidoCancelacionService")
    void testAdminCancelarPedidoDelegaEnCancelacionService() {
        adminService.actualizarEstadoPedidoAdmin("ped_adm_1", "CANCELADO");

        verify(pedidoCancelacionService).cancelarPedido(
                eq(pedido),
                eq("Cancelado por el administrador"),
                eq(ActorTransicion.ADMIN),
                eq(CanceladoPor.ADMIN)
        );
    }

    @Test
    @DisplayName("5.1: Admin actualizando a estado válido usa PedidoEstadoService con actor ADMIN")
    void testAdminActualizarEstadoValidoUsaEstadoService() {
        pedido.setEstadoEnum(EstadoPedido.CONFIRMADO);
        adminService.actualizarEstadoPedidoAdmin("ped_adm_1", "EN_PROCESO");

        verify(pedidoEstadoService).transicionar(
                eq(pedido),
                eq(EstadoPedido.EN_PROCESO),
                eq(ActorTransicion.ADMIN),
                anyString()
        );
    }

    @Test
    @DisplayName("5.2: Aceptar pedido falla si el pago no está completado")
    void testAceptarPedidoFallaSinPagoCompletado() {
        // Sin datos de pago
        assertThrows(IllegalStateException.class, () ->
                adminService.aceptarPedidoAdmin("ped_adm_1")
        );

        // Con pago FALLIDO
        DatosPago pagoFallido = new DatosPago();
        pagoFallido.setEstado("FALLIDO");
        pedido.setPago(pagoFallido);

        assertThrows(IllegalStateException.class, () ->
                adminService.aceptarPedidoAdmin("ped_adm_1")
        );
    }

    @Test
    @DisplayName("5.2: Aceptar pedido exitoso si el pago está COMPLETADO")
    void testAceptarPedidoExitosoConPagoCompletado() {
        DatosPago pago = new DatosPago();
        pago.setEstado("COMPLETADO");
        pedido.setPago(pago);

        adminService.aceptarPedidoAdmin("ped_adm_1");

        verify(pedidoEstadoService).transicionar(
                eq(pedido),
                eq(EstadoPedido.CONFIRMADO),
                eq(ActorTransicion.ADMIN),
                anyString()
        );
    }

    @Test
    @DisplayName("5.3: Reembolso realizado sobre pedido EN_REVISION pasa a CANCELADO sin tocar billetera ni stock")
    void testReembolsoRealizadoEnRevision() {
        pedido.setEstadoEnum(EstadoPedido.EN_REVISION);
        pedido.setRequiereReembolso(true);

        Pedido resultado = adminService.marcarReembolsoRealizadoAdmin("ped_adm_1", "Transferencia bancaria efectuada");

        assertFalse(resultado.isRequiereReembolso(), "requiereReembolso debe quedar en false");
        assertNotNull(resultado.getReembolsoRealizadoEn(), "reembolsoRealizadoEn debe tener timestamp");
        assertEquals(CanceladoPor.ADMIN, resultado.getCanceladoPor());
        assertEquals("Transferencia bancaria efectuada", resultado.getMotivoCancelacion());

        verify(pedidoEstadoService).transicionar(
                eq(pedido),
                eq(EstadoPedido.CANCELADO),
                eq(ActorTransicion.ADMIN),
                eq("Transferencia bancaria efectuada"),
                eq(CanceladoPor.ADMIN)
        );
        verify(pedidoRepository).save(pedido);
    }

    @Test
    @DisplayName("5.3: Reembolso realizado sobre pedido que no lo requiere falla con IllegalStateException")
    void testReembolsoRealizadoFallaSiNoAplica() {
        pedido.setEstadoEnum(EstadoPedido.ENTREGADO);
        pedido.setRequiereReembolso(false);

        assertThrows(IllegalStateException.class, () ->
                adminService.marcarReembolsoRealizadoAdmin("ped_adm_1", "Motivo")
        );
    }
}
