package com.example.demo.services;

import com.example.demo.Model.*;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.ParadaEnvio;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.EnvioRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransportistaFase2Test {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private EnvioRepository envioRepository;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private PedidoEstadoService pedidoEstadoService;

    @InjectMocks
    private TransportistaService transportistaService;

    @InjectMocks
    private VendedorService vendedorService;

    private Usuario transportista;
    private Usuario vendedor;
    private Usuario comprador;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        transportista = new Usuario();
        transportista.setId("trans_1");
        transportista.setNombre("Carlos Transportista");
        transportista.setEmail("carlos@logistica.com");
        transportista.addRole(Role.TRANSPORTISTA);

        vendedor = new Usuario();
        vendedor.setId("vend_1");
        vendedor.setNombre("Juan Agricultor");
        vendedor.setEmail("juan@finca.com");
        vendedor.addRole(Role.VENDEDOR);

        comprador = new Usuario();
        comprador.setId("comp_1");
        comprador.setNombre("Ana Compradora");
        comprador.setEmail("ana@cliente.com");
        comprador.addRole(Role.COMPRADOR);

        admin = new Usuario();
        admin.setId("admin_1");
        admin.setNombre("Super Admin");
        admin.setEmail("admin@cultivus.com");
        admin.addRole(Role.ADMIN);

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(envioRepository.save(any(Envio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        pedidoEstadoService = new PedidoEstadoService();
        org.springframework.test.util.ReflectionTestUtils.setField(pedidoEstadoService, "pedidoRepository", pedidoRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(pedidoEstadoService, "compraRepository", compraRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(vendedorService, "pedidoEstadoService", pedidoEstadoService);
        org.springframework.test.util.ReflectionTestUtils.setField(transportistaService, "pedidoEstadoService", pedidoEstadoService);
    }

    // =========================================================================
    // 1. REGLAS DE CICLO DE VIDA PARA EL VENDEDOR (VendedorService)
    // =========================================================================

    @Test
    @DisplayName("Vendedor puede avanzar de CONFIRMADO a EN_PROCESO")
    void testVendedorAvanzarAEnProcesoExitoso() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_1");
        pedido.setEstado(Pedido.CONFIRMADO);
        pedido.setVendedor(new DatosVendedor("vend_1", "Finca El Porvenir", true));
        pedido.setComprador(new DatosComprador("comp_1", "Ana Compradora"));

        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));

        vendedorService.actualizarEstadoPedido("ped_1", Pedido.EN_PROCESO, "vend_1");

        assertEquals(Pedido.EN_PROCESO, pedido.getEstado());
        verify(pedidoRepository, times(1)).save(pedido);
        verify(notificacionService, times(1)).enviar(eq("comp_1"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Vendedor puede avanzar de EN_PROCESO a LISTO_PARA_RECOGER")
    void testVendedorAvanzarAListoParaRecogerExitoso() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_2");
        pedido.setEstado(Pedido.EN_PROCESO);
        pedido.setVendedor(new DatosVendedor("vend_1", "Finca El Porvenir", true));
        pedido.setComprador(new DatosComprador("comp_1", "Ana Compradora"));

        when(pedidoRepository.findById("ped_2")).thenReturn(Optional.of(pedido));

        vendedorService.actualizarEstadoPedido("ped_2", Pedido.LISTO_PARA_RECOGER, "vend_1");

        assertEquals(Pedido.LISTO_PARA_RECOGER, pedido.getEstado());
        verify(pedidoRepository, atLeastOnce()).save(pedido);
    }

    @Test
    @DisplayName("Vendedor NO puede marcar pedido como ENVIADO (exclusivo de logística)")
    void testVendedorIntentoEnviadoFalla() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_3");
        pedido.setEstado(Pedido.LISTO_PARA_RECOGER);
        pedido.setVendedor(new DatosVendedor("vend_1", "Finca El Porvenir", true));

        when(pedidoRepository.findById("ped_3")).thenReturn(Optional.of(pedido));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            vendedorService.actualizarEstadoPedido("ped_3", Pedido.ENVIADO, "vend_1");
        });

        assertTrue(ex.getMessage().contains("exclusivamente el transportista"),
                "Debe indicar que ENVIADO es reservado para el transportista");
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Vendedor NO puede marcar pedido como ENTREGADO (exclusivo de logística)")
    void testVendedorIntentoEntregadoFalla() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_4");
        pedido.setEstado(Pedido.LISTO_PARA_RECOGER);
        pedido.setVendedor(new DatosVendedor("vend_1", "Finca El Porvenir", true));

        when(pedidoRepository.findById("ped_4")).thenReturn(Optional.of(pedido));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            vendedorService.actualizarEstadoPedido("ped_4", Pedido.ENTREGADO, "vend_1");
        });

        assertTrue(ex.getMessage().contains("exclusivamente el transportista"));
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Vendedor no puede saltar directo de CONFIRMADO a LISTO_PARA_RECOGER")
    void testVendedorSaltoDirectoInvalidoFalla() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_5");
        pedido.setEstado(Pedido.CONFIRMADO);
        pedido.setVendedor(new DatosVendedor("vend_1", "Finca El Porvenir", true));

        when(pedidoRepository.findById("ped_5")).thenReturn(Optional.of(pedido));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            vendedorService.actualizarEstadoPedido("ped_5", Pedido.LISTO_PARA_RECOGER, "vend_1");
        });

        assertTrue(ex.getMessage().contains("solo se puede avanzar a 'En proceso'"));
    }

    @Test
    @DisplayName("Vendedor distinto al dueño del pedido es rechazado con SecurityException")
    void testVendedorOtroUsuarioFalla() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_6");
        pedido.setEstado(Pedido.CONFIRMADO);
        pedido.setVendedor(new DatosVendedor("vend_1", "Finca El Porvenir", true));

        when(pedidoRepository.findById("ped_6")).thenReturn(Optional.of(pedido));

        assertThrows(SecurityException.class, () -> {
            vendedorService.actualizarEstadoPedido("ped_6", Pedido.EN_PROCESO, "vend_impostor");
        });
    }

    // =========================================================================
    // 2. CICLO OPERATIVO DEL TRANSPORTISTA (TransportistaService)
    // =========================================================================

    @Test
    @DisplayName("Marcar parada recogida falla si el usuario no es el transportista asignado")
    void testMarcarParadaRecogidaFallaImpostor() {
        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_1");

        when(envioRepository.findById("env_1")).thenReturn(Optional.of(envio));

        assertThrows(SecurityException.class, () -> {
            transportistaService.marcarParadaRecogida("env_1", "ped_1", "trans_impostor");
        });
    }

    @Test
    @DisplayName("Marcar parada recogida falla si el sub-pedido NO está en LISTO_PARA_RECOGER")
    void testMarcarParadaRecogidaFallaSiSubPedidoNoListo() {
        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_1");

        ParadaEnvio parada = new ParadaEnvio();
        parada.setPedidoId("ped_1");
        parada.setRecogido(false);
        envio.setParadas(List.of(parada));

        Pedido pedido = new Pedido();
        pedido.setId("ped_1");
        pedido.setEstado(Pedido.EN_PROCESO); // Sigue en preparación

        when(envioRepository.findById("env_1")).thenReturn(Optional.of(envio));
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            transportistaService.marcarParadaRecogida("env_1", "ped_1", "trans_1");
        });

        assertTrue(ex.getMessage().contains("no está listo para recoger"));
        assertFalse(parada.isRecogido());
    }

    @Test
    @DisplayName("Marcar parada recogida exitoso: parada queda recogida, pedido pasa a ENVIADO, envío avanza de estado")
    void testMarcarParadaRecogidaExitoso() {
        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_1");

        ParadaEnvio p1 = new ParadaEnvio();
        p1.setPedidoId("ped_1");
        p1.setRecogido(false);

        ParadaEnvio p2 = new ParadaEnvio();
        p2.setPedidoId("ped_2");
        p2.setRecogido(false);

        envio.setParadas(new ArrayList<>(List.of(p1, p2)));

        Pedido pedido1 = new Pedido();
        pedido1.setId("ped_1");
        pedido1.setEstado(Pedido.LISTO_PARA_RECOGER);
        pedido1.setComprador(new DatosComprador("comp_1", "Ana"));

        when(envioRepository.findById("env_1")).thenReturn(Optional.of(envio));
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido1));

        Envio res = transportistaService.marcarParadaRecogida("env_1", "ped_1", "trans_1");

        assertNotNull(res);
        assertTrue(p1.isRecogido());
        assertNotNull(p1.getFechaRecogida());
        assertEquals(Pedido.ENVIADO, pedido1.getEstado(), "El pedido debe pasar a ENVIADO por el carrier");
        assertEquals(Envio.EN_RECOGIDA, res.getEstado(), "Falta p2 por recoger, debe quedar en EN_RECOGIDA");
        verify(pedidoRepository, times(1)).save(pedido1);
        verify(envioRepository, times(1)).save(envio);
        verify(notificacionService, times(1)).enviar(eq("comp_1"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Marcar parada recogida cuando es la última: envío pasa a EN_ENTREGA")
    void testMarcarParadaRecogidaUltimaPasaAEnEntrega() {
        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.EN_RECOGIDA);
        envio.setTransportistaId("trans_1");

        ParadaEnvio p1 = new ParadaEnvio();
        p1.setPedidoId("ped_1");
        p1.setRecogido(true);

        ParadaEnvio p2 = new ParadaEnvio();
        p2.setPedidoId("ped_2");
        p2.setRecogido(false);

        envio.setParadas(new ArrayList<>(List.of(p1, p2)));

        Pedido pedido2 = new Pedido();
        pedido2.setId("ped_2");
        pedido2.setEstado(Pedido.LISTO_PARA_RECOGER);

        when(envioRepository.findById("env_1")).thenReturn(Optional.of(envio));
        when(pedidoRepository.findById("ped_2")).thenReturn(Optional.of(pedido2));

        Envio res = transportistaService.marcarParadaRecogida("env_1", "ped_2", "trans_1");

        assertTrue(p2.isRecogido());
        assertEquals(Envio.EN_ENTREGA, res.getEstado(), "Todas las paradas recogidas: estado EN_ENTREGA");
    }

    @Test
    @DisplayName("Marcar entrega final falla si alguna parada no ha sido recogida")
    void testMarcarEnvioEntregadoFallaSiFaltanParadas() {
        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.EN_RECOGIDA);
        envio.setTransportistaId("trans_1");

        ParadaEnvio p1 = new ParadaEnvio();
        p1.setPedidoId("ped_1");
        p1.setRecogido(true);

        ParadaEnvio p2 = new ParadaEnvio();
        p2.setPedidoId("ped_2");
        p2.setRecogido(false); // No recogida

        envio.setParadas(List.of(p1, p2));

        when(envioRepository.findById("env_1")).thenReturn(Optional.of(envio));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            transportistaService.marcarEnvioEntregado("env_1", "trans_1");
        });

        assertTrue(ex.getMessage().contains("faltan paradas por recolectar"));
    }

    @Test
    @DisplayName("Marcar entrega final exitoso: pasa Envio a ENTREGADO y todos los sub-pedidos a ENTREGADO")
    void testMarcarEnvioEntregadoExitoso() {
        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.EN_ENTREGA);
        envio.setTransportistaId("trans_1");

        ParadaEnvio p1 = new ParadaEnvio();
        p1.setPedidoId("ped_1");
        p1.setRecogido(true);

        ParadaEnvio p2 = new ParadaEnvio();
        p2.setPedidoId("ped_2");
        p2.setRecogido(true);

        envio.setParadas(List.of(p1, p2));

        Pedido pedido1 = new Pedido();
        pedido1.setId("ped_1");
        pedido1.setEstado(Pedido.ENVIADO);
        pedido1.setVendedor(new DatosVendedor("vend_1", "Finca 1", true));
        pedido1.setComprador(new DatosComprador("comp_1", "Ana"));

        Pedido pedido2 = new Pedido();
        pedido2.setId("ped_2");
        pedido2.setEstado(Pedido.ENVIADO);
        pedido2.setVendedor(new DatosVendedor("vend_2", "Finca 2", true));
        pedido2.setComprador(new DatosComprador("comp_1", "Ana"));

        when(envioRepository.findById("env_1")).thenReturn(Optional.of(envio));
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido1));
        when(pedidoRepository.findById("ped_2")).thenReturn(Optional.of(pedido2));

        Envio res = transportistaService.marcarEnvioEntregado("env_1", "trans_1");

        assertNotNull(res);
        assertEquals(Envio.ENTREGADO, res.getEstado());
        assertNotNull(res.getFechaEntrega());
        assertEquals(Pedido.ENTREGADO, pedido1.getEstado());
        assertEquals(Pedido.ENTREGADO, pedido2.getEstado());
        verify(pedidoRepository, times(1)).save(pedido1);
        verify(pedidoRepository, times(1)).save(pedido2);
        verify(envioRepository, times(1)).save(envio);
    }

    // =========================================================================
    // 3. CANCELACIONES Y AJUSTES DE RUTA
    // =========================================================================

    @Test
    @DisplayName("Cancelar sub-pedido remueve parada y recalcula costoTotalCentavos")
    void testRemoverParadaPorPedidoCanceladoRecalculaFlete() {
        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_1");

        ParadaEnvio p1 = new ParadaEnvio();
        p1.setPedidoId("ped_1");
        p1.setTarifaCentavos(1500000L); // $15,000

        ParadaEnvio p2 = new ParadaEnvio();
        p2.setPedidoId("ped_2");
        p2.setTarifaCentavos(2000000L); // $20,000

        envio.setParadas(new ArrayList<>(List.of(p1, p2)));
        envio.setCostoTotalCentavos(3500000L); // $35,000 inicial

        when(envioRepository.findAll()).thenReturn(List.of(envio));

        Optional<Envio> optEnvio = transportistaService.removerParadaPorPedidoCancelado("ped_1");

        assertTrue(optEnvio.isPresent());
        Envio actualizado = optEnvio.get();
        assertEquals(1, actualizado.getParadas().size());
        assertEquals("ped_2", actualizado.getParadas().get(0).getPedidoId());
        assertEquals(2000000L, actualizado.getCostoTotalCentavos());
        verify(envioRepository, times(1)).save(actualizado);
        verify(notificacionService, times(1)).enviar(eq("trans_1"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Cancelar última parada marca el Envio como CANCELADO")
    void testRemoverUltimaParadaPorPedidoCanceladoCancelaEnvio() {
        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_1");

        ParadaEnvio p1 = new ParadaEnvio();
        p1.setPedidoId("ped_1");
        p1.setTarifaCentavos(1500000L);

        envio.setParadas(new ArrayList<>(List.of(p1)));
        envio.setCostoTotalCentavos(1500000L);

        when(envioRepository.findAll()).thenReturn(List.of(envio));

        Optional<Envio> optEnvio = transportistaService.removerParadaPorPedidoCancelado("ped_1");

        assertTrue(optEnvio.isPresent());
        Envio actualizado = optEnvio.get();
        assertTrue(actualizado.getParadas().isEmpty());
        assertEquals(0L, actualizado.getCostoTotalCentavos());
        assertEquals(Envio.CANCELADO, actualizado.getEstado());
    }

    // =========================================================================
    // 4. EXPIRACIÓN SIN TRANSPORTISTA (Scheduled)
    // =========================================================================

    @Test
    @DisplayName("Envío con más de 24h en PENDIENTE_ASIGNACION pasa a SIN_TRANSPORTISTA y alerta admin/comprador")
    void testVerificarEnviosSinTransportistaExpirados() {
        when(appConfigService.obtenerValorInt(eq("HORAS_MAX_SIN_TRANSPORTISTA"), anyInt())).thenReturn(24);

        Envio envioViejo = new Envio();
        envioViejo.setId("env_viejo");
        envioViejo.setEstado(Envio.PENDIENTE_ASIGNACION);
        envioViejo.setFechaCreacion(LocalDateTime.now().minusHours(25));
        envioViejo.setCompraId("compra_vieja");

        Envio envioReciente = new Envio();
        envioReciente.setId("env_reciente");
        envioReciente.setEstado(Envio.PENDIENTE_ASIGNACION);
        envioReciente.setFechaCreacion(LocalDateTime.now().minusHours(2));
        envioReciente.setCompraId("compra_reciente");

        when(envioRepository.findByEstado(Envio.PENDIENTE_ASIGNACION))
                .thenReturn(List.of(envioViejo, envioReciente));

        Compra compra = new Compra();
        compra.setId("compra_vieja");
        compra.setComprador(new DatosComprador("comp_1", "Ana"));
        when(compraRepository.findById("compra_vieja")).thenReturn(Optional.of(compra));

        when(usuarioRepository.findByRolesContaining(Role.ADMIN)).thenReturn(List.of(admin));

        int totalExpirados = transportistaService.verificarEnviosSinTransportista();

        assertEquals(1, totalExpirados);
        assertEquals(Envio.SIN_TRANSPORTISTA, envioViejo.getEstado());
        assertEquals(Envio.PENDIENTE_ASIGNACION, envioReciente.getEstado());
        verify(envioRepository, times(1)).save(envioViejo);
        verify(envioRepository, never()).save(envioReciente);
        verify(notificacionService, times(1)).enviar(eq("comp_1"), anyString(), anyString(), anyString());
        verify(notificacionService, times(1)).enviar(eq("admin_1"), anyString(), anyString(), anyString());
    }
}
