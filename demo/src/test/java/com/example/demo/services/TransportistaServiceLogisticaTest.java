package com.example.demo.services;

import com.example.demo.Model.Envio;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.ParadaEnvio;
import com.example.demo.Model.embebidos.PerfilTransportista;
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
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.demo.security.SecurityContextRefreshService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransportistaServiceLogisticaTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private EnvioRepository envioRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SecurityContextRefreshService securityContextRefreshService;

    @InjectMocks
    private TransportistaService transportistaService;

    private Usuario crearTransportistaValido(String id, String nombre) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombre(nombre);
        u.setEmail(id + "@transportista.com");
        u.addRole(Role.TRANSPORTISTA);

        PerfilTransportista perfil = new PerfilTransportista();
        perfil.setTipo(PerfilTransportista.TIPO_INDEPENDIENTE);
        perfil.setPlaca("XYZ-789");
        perfil.setLicenciaConduccion("C2");
        perfil.setTelefonoContacto("3009998877");
        perfil.setDisponible(true);
        perfil.setVerificado(true); // Verificado por admin
        perfil.setTodasLasZonas(true);
        perfil.setZonasCoberturaIds(List.of(1, 2, 3, 4, 5, 6));

        u.setPerfilTransportista(perfil);
        return u;
    }

    @BeforeEach
    void setUp() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(envioRepository.save(any(Envio.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Aceptar compra concurrente: si dos intentan a la vez, solo uno gana y el otro recibe 'ya fue tomada'")
    void testAceptarCompraConcurrente() {
        String envioId = "env_100";
        Usuario t1 = crearTransportistaValido("trans_1", "Transportista 1");
        Usuario t2 = crearTransportistaValido("trans_2", "Transportista 2");

        when(usuarioRepository.findById("trans_1")).thenReturn(Optional.of(t1));
        when(usuarioRepository.findById("trans_2")).thenReturn(Optional.of(t2));

        Envio envioAsignadoT1 = new Envio();
        envioAsignadoT1.setId(envioId);
        envioAsignadoT1.setEstado(Envio.ASIGNADO);
        envioAsignadoT1.setTransportistaId("trans_1");

        // El primer llamado a findAndModify tiene éxito y retorna el documento actualizado
        // El segundo llamado simula que ya no hace match en estado PENDIENTE_ASIGNACION y retorna null
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Envio.class)))
                .thenReturn(envioAsignadoT1)  // Ganador (t1)
                .thenReturn(null);            // Perdedor (t2)

        // T1 acepta primero
        Envio resT1 = transportistaService.aceptarEnvio(envioId, "trans_1");
        assertNotNull(resT1);
        assertEquals(Envio.ASIGNADO, resT1.getEstado());
        assertEquals("trans_1", resT1.getTransportistaId());

        // T2 intenta aceptar el mismo envío de inmediato
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            transportistaService.aceptarEnvio(envioId, "trans_2");
        });

        assertTrue(ex.getMessage().contains("ya fue tomada"),
                "El mensaje debe indicar que la compra ya fue tomada por otro transportista. Mensaje real: " + ex.getMessage());
    }

    @Test
    @DisplayName("Liberar ruta: falla si al menos una parada ya fue recolectada")
    void testLiberarRutaFallaSiParadaRecolectada() {
        String envioId = "env_200";
        String transportistaId = "trans_1";

        Envio envio = new Envio();
        envio.setId(envioId);
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId(transportistaId);

        ParadaEnvio p1 = new ParadaEnvio();
        p1.setRecolectado(true); // Ya fue recolectada por el conductor

        ParadaEnvio p2 = new ParadaEnvio();
        p2.setRecolectado(false);

        envio.setParadas(List.of(p1, p2));

        when(envioRepository.findById(envioId)).thenReturn(Optional.of(envio));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            transportistaService.liberarEnvio(envioId, transportistaId);
        });

        assertTrue(ex.getMessage().contains("ya tiene paradas recogidas"),
                "Debe impedir la liberación si ya se recolectaron paradas.");
    }

    @Test
    @DisplayName("Liberar ruta: si ninguna parada está recolectada, vuelve a PENDIENTE_ASIGNACION y limpia transportista")
    void testLiberarRutaExitoso() {
        String envioId = "env_201";
        String transportistaId = "trans_1";

        Envio envio = new Envio();
        envio.setId(envioId);
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId(transportistaId);

        ParadaEnvio p1 = new ParadaEnvio();
        p1.setRecolectado(false);

        ParadaEnvio p2 = new ParadaEnvio();
        p2.setRecolectado(false);

        envio.setParadas(new ArrayList<>(List.of(p1, p2)));

        when(envioRepository.findById(envioId)).thenReturn(Optional.of(envio));

        Envio liberado = transportistaService.liberarEnvio(envioId, transportistaId);

        assertNotNull(liberado);
        assertEquals(Envio.PENDIENTE_ASIGNACION, liberado.getEstado());
        assertNull(liberado.getTransportistaId());
        assertNull(liberado.getTransportista());
        assertNull(liberado.getFechaAsignacion());
        verify(envioRepository, times(1)).save(envio);
    }

    @Test
    @DisplayName("Liberar ruta: falla si un usuario distinto al asignado intenta liberarla")
    void testLiberarRutaFallaUsuarioDistinto() {
        String envioId = "env_202";
        Envio envio = new Envio();
        envio.setId(envioId);
        envio.setEstado(Envio.ASIGNADO);
        envio.setTransportistaId("trans_propietario");

        when(envioRepository.findById(envioId)).thenReturn(Optional.of(envio));

        assertThrows(SecurityException.class, () -> {
            transportistaService.liberarEnvio(envioId, "trans_impostor");
        });
    }

    @Test
    @DisplayName("Validación de registro: sin zonas seleccionadas falla con excepción")
    void testRegistroSinZonasFalla() {
        when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        // Intento de registrar sin marcar todasLasZonas y con lista nula o vacía
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            transportistaService.registrarNuevoTransportista(
                    "Carlos Conductor", "carlos@test.com", "123456",
                    PerfilTransportista.TIPO_INDEPENDIENTE, null, null,
                    "Camioneta", "ABC-123", 1000.0, "C2", "3001112233",
                    "Cartagena", "Bolívar", null, false, false, null, null, null
            );
        });

        assertTrue(ex.getMessage().contains("al menos una zona"),
                "Debe requerir al menos una zona de cobertura");
    }

    @Test
    @DisplayName("Validación de registro: 'Todas las zonas' guarda automáticamente las 6 zonas")
    void testRegistroTodasLasZonasGuarda6() {
        when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        Usuario creado = transportistaService.registrarNuevoTransportista(
                "Carlos Conductor", "carlos@test.com", "123456",
                PerfilTransportista.TIPO_INDEPENDIENTE, null, null,
                "Camioneta", "ABC-123", 1000.0, "C2", "3001112233",
                "Cartagena", "Bolívar", null, true, false, null, null, null
        );

        assertNotNull(creado);
        PerfilTransportista perfil = creado.getPerfilTransportista();
        assertNotNull(perfil);
        assertTrue(perfil.isTodasLasZonas());
        assertNotNull(perfil.getZonasCoberturaIds());
        assertEquals(6, perfil.getZonasCoberturaIds().size(), "Debe guardar los 6 IDs de zona de Bolívar");
        assertTrue(perfil.getZonasCoberturaIds().containsAll(List.of(1, 2, 3, 4, 5, 6)));
    }
}
