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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransportistaBloque4Test {

    @Mock
    private EnvioRepository envioRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private AppConfigService appConfigService;

    @InjectMocks
    private TransportistaService transportistaService;

    private PedidoEstadoService pedidoEstadoService;

    @BeforeEach
    void setUp() {
        lenient().when(envioRepository.save(any(Envio.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        pedidoEstadoService = new PedidoEstadoService();
        ReflectionTestUtils.setField(pedidoEstadoService, "pedidoRepository", pedidoRepository);
        ReflectionTestUtils.setField(pedidoEstadoService, "compraRepository", compraRepository);
        ReflectionTestUtils.setField(transportistaService, "pedidoEstadoService", pedidoEstadoService);
    }

    @Test
    @DisplayName("4.1: Envío no expira a SIN_TRANSPORTISTA si las paradas aún no están listas (fechaDisponibleDesde == null)")
    void testEnvioNoExpiraSiNoEstaDisponible() {
        when(appConfigService.obtenerValorInt(eq("HORAS_MAX_SIN_TRANSPORTISTA"), anyInt())).thenReturn(24);

        Envio envio = new Envio();
        envio.setId("env_1");
        envio.setEstado(Envio.PENDIENTE_ASIGNACION);
        envio.setFechaCreacion(LocalDateTime.now().minusHours(30)); // Creado hace 30h pero aún no listo
        envio.setFechaDisponibleDesde(null); // Paradas aún en proceso

        ParadaEnvio parada = new ParadaEnvio();
        parada.setPedidoId("ped_1");
        envio.setParadas(List.of(parada));

        when(envioRepository.findByEstado(Envio.PENDIENTE_ASIGNACION)).thenReturn(List.of(envio));

        int expirados = transportistaService.verificarEnviosSinTransportista();

        assertEquals(0, expirados, "No debe expirar porque fechaDisponibleDesde es null");
        assertEquals(Envio.PENDIENTE_ASIGNACION, envio.getEstado());
        verify(envioRepository, never()).save(envio);
    }

    @Test
    @DisplayName("4.1: Envío expira a SIN_TRANSPORTISTA cuando pasaron 24h desde fechaDisponibleDesde")
    void testEnvioExpiraDesdeFechaDisponibleDesde() {
        when(appConfigService.obtenerValorInt(eq("HORAS_MAX_SIN_TRANSPORTISTA"), anyInt())).thenReturn(24);

        Envio envio = new Envio();
        envio.setId("env_2");
        envio.setEstado(Envio.PENDIENTE_ASIGNACION);
        envio.setFechaCreacion(LocalDateTime.now().minusHours(40));
        envio.setFechaDisponibleDesde(LocalDateTime.now().minusHours(25)); // Disponible hace 25h

        ParadaEnvio parada = new ParadaEnvio();
        parada.setPedidoId("ped_1");
        parada.setVendedorId("vend_1");
        envio.setParadas(List.of(parada));

        when(envioRepository.findByEstado(Envio.PENDIENTE_ASIGNACION)).thenReturn(List.of(envio));

        int expirados = transportistaService.verificarEnviosSinTransportista();

        assertEquals(1, expirados);
        assertEquals(Envio.SIN_TRANSPORTISTA, envio.getEstado());
        verify(envioRepository).save(envio);
        verify(notificacionService).enviar(eq("vend_1"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("4.2: Timeout de asignado regresa el envío a PENDIENTE_ASIGNACION si pasaron 24h sin recolección")
    void testTimeoutAsignadoSinRecoleccion() {
        when(appConfigService.obtenerValorInt(eq("HORAS_MAX_ASIGNADO_SIN_RECOGIDA"), anyInt())).thenReturn(24);

        Envio envio = new Envio();
        envio.setId("env_asig");
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_1");
        envio.setFechaAsignacion(LocalDateTime.now().minusHours(26));

        ParadaEnvio parada = new ParadaEnvio();
        parada.setPedidoId("ped_1");
        parada.setVendedorId("vend_1");
        parada.setRecogido(false); // Ninguna parada recogida
        envio.setParadas(List.of(parada));

        when(envioRepository.findByEstado(Envio.ASIGNADO)).thenReturn(List.of(envio));

        int revertidos = transportistaService.verificarEnviosAsignadosExpirados();

        assertEquals(1, revertidos);
        assertEquals(Envio.PENDIENTE_ASIGNACION, envio.getEstado());
        assertNull(envio.getTransportistaId());
        assertNull(envio.getFechaAsignacion());
        verify(envioRepository).save(envio);
        verify(notificacionService).enviar(eq("trans_1"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("4.3: Liberar envío no permite liberar si alguna parada ya fue recogida")
    void testLiberarEnvioNoPermiteConParadasRecogidas() {
        Envio envio = new Envio();
        envio.setId("env_lib");
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_1");

        ParadaEnvio parada = new ParadaEnvio();
        parada.setRecogido(true);
        envio.setParadas(List.of(parada));

        when(envioRepository.findById("env_lib")).thenReturn(Optional.of(envio));

        assertThrows(IllegalStateException.class, () ->
                transportistaService.liberarEnvio("env_lib", "trans_1")
        );
    }

    @Test
    @DisplayName("4.4: Reasignar envío SIN_TRANSPORTISTA lo devuelve a bolsa con 24h nuevas")
    void testReasignarEnvioSinTransportista() {
        Envio envio = new Envio();
        envio.setId("env_sin");
        envio.setEstado(Envio.SIN_TRANSPORTISTA);

        when(envioRepository.findById("env_sin")).thenReturn(Optional.of(envio));

        Envio reasignado = transportistaService.reasignarEnvioSinTransportista("env_sin", "admin_1");

        assertEquals(Envio.PENDIENTE_ASIGNACION, reasignado.getEstado());
        assertNotNull(reasignado.getFechaDisponibleDesde());
        assertTrue(reasignado.getFechaDisponibleDesde().isAfter(LocalDateTime.now().minusMinutes(1)));
        verify(envioRepository).save(envio);
    }

    @Test
    @DisplayName("4.5: Reportar incidencia en parada requiere motivo obligatorio y bloquea entrega final")
    void testReportarIncidenciaYBloqueoEntrega() {
        Envio envio = new Envio();
        envio.setId("env_inc");
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_1");

        ParadaEnvio parada = new ParadaEnvio();
        parada.setPedidoId("ped_p1");
        parada.setVendedorId("vend_p1");
        envio.setParadas(new ArrayList<>(List.of(parada)));

        when(envioRepository.findById("env_inc")).thenReturn(Optional.of(envio));

        // Motivo vacío debe fallar
        assertThrows(IllegalArgumentException.class, () ->
                transportistaService.reportarProblemaParada("env_inc", "ped_p1", "   ", "trans_1")
        );

        // Reporte exitoso
        transportistaService.reportarProblemaParada("env_inc", "ped_p1", "Finca cerrada sin acceso", "trans_1");
        assertTrue(parada.isIncidenciaReportada());
        assertEquals("Finca cerrada sin acceso", parada.getMotivoIncidencia());
        verify(notificacionService).enviar(eq("vend_p1"), anyString(), anyString(), anyString());

        // Marcar entrega debe fallar porque tiene incidencia abierta
        parada.setRecogido(true);
        assertThrows(IllegalStateException.class, () ->
                transportistaService.marcarEnvioEntregado("env_inc", "trans_1")
        );
    }

    @Test
    @DisplayName("4.8: Reconciliador crea envío para compra PAGADA a domicilio sin envío registrado")
    void testReconciliarEnviosFaltantes() {
        Compra compra = new Compra();
        compra.setId("compra_huerfana");
        compra.setEstado(Compra.PAGADA);
        compra.setModalidadEntrega(ModalidadEntrega.DOMICILIO);
        compra.setEnvioCentavos(1200000L);

        when(compraRepository.findAll()).thenReturn(List.of(compra));
        when(envioRepository.findByCompraId("compra_huerfana")).thenReturn(Optional.empty());

        Pedido ped = new Pedido();
        ped.setId("ped_h1");
        ped.setCompraId("compra_huerfana");
        ped.setEstadoEnum(EstadoPedido.CONFIRMADO);
        ped.setVendedor(new DatosVendedor("vend_1", "Finca Sol", true));

        when(pedidoRepository.findByCompraId("compra_huerfana")).thenReturn(List.of(ped));

        int reconciliados = transportistaService.reconciliarEnviosFaltantes();

        assertEquals(1, reconciliados);
        verify(envioRepository).save(any(Envio.class));
    }
}
