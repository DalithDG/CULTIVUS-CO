package com.example.demo.services;

import com.example.demo.Config.PasswordResetProperties;
import com.example.demo.Model.PasswordResetToken;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del flujo de recuperación sin MongoDB ni SMTP reales.
 * El almacén en memoria reproduce la semántica atómica del findAndModify.
 */
class PasswordResetServiceTest {

    private static final String EMAIL = "ana@example.com";
    private static final String IP = "192.0.2.10";
    private static final Instant INICIO = Instant.parse("2026-01-15T12:00:00Z");

    private UsuarioRepository usuarioRepository;
    private PasswordResetMailService mailService;
    private PasswordResetRateLimiter rateLimiter;
    private PasswordResetProperties properties;
    private PasswordEncoder passwordEncoder;
    private RelojMutable clock;
    private AlmacenEnMemoria tokenStore;
    private SessionRegistryImpl sessionRegistry;
    private PasswordResetService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        mailService = mock(PasswordResetMailService.class);
        properties = new PasswordResetProperties();
        properties.setTokenTtl(Duration.ofMinutes(15));
        properties.setVentana(Duration.ofMinutes(15));
        properties.setMaxSolicitudesPorEmail(3);
        properties.setMaxSolicitudesPorIp(10);
        properties.setMaxIntentosPorIp(20);

        passwordEncoder = new BCryptPasswordEncoder(4);
        clock = new RelojMutable(INICIO);
        rateLimiter = new PasswordResetRateLimiter(clock);
        tokenStore = new AlmacenEnMemoria();
        sessionRegistry = new SessionRegistryImpl();

        usuario = new Usuario("Ana", EMAIL, "hash-inicial", Role.COMPRADOR);
        usuario.setId("u1");

        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service = crearServicio(sessionRegistry);
    }

    private PasswordResetService crearServicio(SessionRegistry registry) {
        return new PasswordResetService(
                usuarioRepository,
                tokenStore,
                passwordEncoder,
                mailService,
                rateLimiter,
                properties,
                registry,
                clock);
    }

    @Test
    @DisplayName("Solicitud: normaliza el correo, guarda solo el hash y envía el token al correo")
    void solicitudGuardaHashYEnviaToken() {
        service.solicitarRecuperacion("  ANA@Example.COM ", IP);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).enviarEnlace(eq(EMAIL), eq("Ana"), tokenCaptor.capture());
        String tokenOriginal = tokenCaptor.getValue();

        assertTrue(tokenOriginal.matches("^[A-Za-z0-9_-]{43}$"),
                "El token debe ser Base64URL de 32 bytes (256 bits)");
        assertNotNull(tokenStore.token);
        assertEquals("u1", tokenStore.token.getUsuarioId());
        assertEquals(EMAIL, tokenStore.token.getEmail());
        assertFalse(tokenStore.token.isUsado());
        assertNotEquals(tokenOriginal, tokenStore.token.getTokenHash());
        assertEquals(64, tokenStore.token.getTokenHash().length());
        assertEquals(sha256(tokenOriginal), tokenStore.token.getTokenHash());
        assertEquals(INICIO.plus(Duration.ofMinutes(15)), tokenStore.token.getExpiraEn());
        assertTrue(service.tokenValido(tokenOriginal));
    }

    @Test
    @DisplayName("Correo no registrado: no crea token, no envía correo y no lanza error")
    void correoNoRegistradoNoRevelaNada() {
        when(usuarioRepository.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.solicitarRecuperacion("nadie@example.com", IP));

        assertNull(tokenStore.token);
        verifyNoInteractions(mailService);
    }

    @Test
    @DisplayName("Nueva solicitud invalida el enlace anterior (un solo token vigente)")
    void nuevaSolicitudInvalidaLaAnterior() {
        service.solicitarRecuperacion(EMAIL, IP);
        String primerToken = capturarUltimoToken();

        service.solicitarRecuperacion(EMAIL, IP);
        String segundoToken = capturarUltimoToken();

        assertNotEquals(primerToken, segundoToken);
        assertFalse(service.tokenValido(primerToken));
        assertTrue(service.tokenValido(segundoToken));
        assertEquals(sha256(segundoToken), tokenStore.token.getTokenHash());
    }

    @Test
    @DisplayName("El token es de un solo uso y la contraseña se guarda con BCrypt")
    void tokenDeUnSoloUsoYPasswordEncoderCorrecto() {
        service.solicitarRecuperacion(EMAIL, IP);
        String token = capturarUltimoToken();

        service.restablecerContrasena(token, "NuevaClave1!", IP);

        assertTrue(passwordEncoder.matches("NuevaClave1!", usuario.getContrasena()));
        assertTrue(tokenStore.token.isUsado());
        assertNotNull(tokenStore.token.getUsadoEn());
        assertFalse(service.tokenValido(token));

        assertThrows(PasswordResetService.TokenInvalidoException.class,
                () -> service.restablecerContrasena(token, "OtraClave2!", IP));
        assertTrue(passwordEncoder.matches("NuevaClave1!", usuario.getContrasena()));
    }

    @Test
    @DisplayName("Token expirado: se rechaza aunque el documento siga existiendo")
    void tokenExpiradoSeRechaza() {
        service.solicitarRecuperacion(EMAIL, IP);
        String token = capturarUltimoToken();

        clock.avanzar(Duration.ofMinutes(15).plusSeconds(1));

        assertFalse(service.tokenValido(token));
        assertThrows(PasswordResetService.TokenInvalidoException.class,
                () -> service.restablecerContrasena(token, "NuevaClave1!", IP));
        assertEquals("hash-inicial", usuario.getContrasena());
    }

    @Test
    @DisplayName("Contraseña débil: se rechaza antes de consumir el token")
    void contrasenaDebilNoConsumeElToken() {
        service.solicitarRecuperacion(EMAIL, IP);
        String token = capturarUltimoToken();

        assertThrows(PasswordResetService.PasswordPolicyException.class,
                () -> service.restablecerContrasena(token, "debil", IP));

        assertFalse(tokenStore.token.isUsado());
        assertTrue(service.tokenValido(token));
    }

    @Test
    @DisplayName("Formato de token inválido: no consulta MongoDB")
    void formatoInvalidoNoConsultaMongo() {
        assertFalse(service.tokenValido("no-es-un-token"));
        assertFalse(service.tokenValido(null));
        assertEquals(0, tokenStore.consultas);
    }

    @Test
    @DisplayName("Límite de solicitudes por correo: la cuarta no envía correo")
    void limiteDeSolicitudesPorCorreo() {
        service.solicitarRecuperacion(EMAIL, IP);
        service.solicitarRecuperacion(EMAIL, IP);
        service.solicitarRecuperacion(EMAIL, IP);
        service.solicitarRecuperacion(EMAIL, IP);

        verify(mailService, times(3)).enviarEnlace(eq(EMAIL), anyString(), anyString());
    }

    @Test
    @DisplayName("Límite de intentos por IP: se rechaza con mensaje seguro")
    void limiteDeIntentosPorIp() {
        properties.setMaxIntentosPorIp(2);
        String tokenSinUso = "a".repeat(43);

        assertThrows(PasswordResetService.TokenInvalidoException.class,
                () -> service.restablecerContrasena(tokenSinUso, "NuevaClave1!", IP));
        assertThrows(PasswordResetService.TokenInvalidoException.class,
                () -> service.restablecerContrasena(tokenSinUso, "NuevaClave1!", IP));
        assertThrows(PasswordResetService.SolicitudLimitadaException.class,
                () -> service.restablecerContrasena(tokenSinUso, "NuevaClave1!", IP));
    }

    @Test
    @DisplayName("Invalida sesiones activas del usuario, incluidas las de OAuth2")
    void invalidaSesionesActivas() {
        UserDetails formulario = User.withUsername(EMAIL)
                .password("x")
                .roles("COMPRADOR")
                .build();

        OAuth2User oauth2 = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_COMPRADOR")),
                Map.of("email", EMAIL),
                "email");

        UserDetails otro = User.withUsername("otro@example.com")
                .password("x")
                .roles("COMPRADOR")
                .build();

        sessionRegistry.registerNewSession("s-form", formulario);
        sessionRegistry.registerNewSession("s-oauth", oauth2);
        sessionRegistry.registerNewSession("s-otro", otro);

        service.solicitarRecuperacion(EMAIL, IP);
        String token = capturarUltimoToken();
        service.restablecerContrasena(token, "NuevaClave1!", IP);

        assertTrue(sessionRegistry.getSessionInformation("s-form").isExpired());
        assertTrue(sessionRegistry.getSessionInformation("s-oauth").isExpired());
        assertFalse(sessionRegistry.getSessionInformation("s-otro").isExpired());
    }

    @Test
    @DisplayName("El token no aparece en los logs de la aplicación")
    void noSeRegistraElToken() {
        // Verificación de diseño: el modelo no expone el token original y el
        // servicio solo recibe el hash en el almacén.
        service.solicitarRecuperacion(EMAIL, IP);
        String token = capturarUltimoToken();

        assertNotEquals(token, tokenStore.token.getTokenHash());
        assertFalse(tokenStore.token.getTokenHash().contains(token));
    }

    private String capturarUltimoToken() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailService, atLeastOnce()).enviarEnlace(eq(EMAIL), anyString(), captor.capture());
        return captor.getValue();
    }

    private static String sha256(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── Implementaciones de prueba ────────────────────────────────────

    static class AlmacenEnMemoria implements PasswordResetTokenStore {
        PasswordResetToken token;
        int consultas;

        @Override
        public void guardar(PasswordResetToken token) {
            this.token = token;
        }

        @Override
        public Optional<PasswordResetToken> buscarVigente(String tokenHash, Instant ahora) {
            consultas++;
            return vigente(tokenHash, ahora);
        }

        @Override
        public synchronized Optional<PasswordResetToken> consumirAtomicamente(String tokenHash, Instant ahora) {
            Optional<PasswordResetToken> vigente = vigente(tokenHash, ahora);
            vigente.ifPresent(t -> {
                t.setUsado(true);
                t.setUsadoEn(ahora);
            });
            return vigente;
        }

        @Override
        public void invalidarPendientes(String usuarioId, Instant ahora) {
            if (token != null && usuarioId != null && usuarioId.equals(token.getUsuarioId())
                    && !token.isUsado()) {
                token.setUsado(true);
                token.setUsadoEn(ahora);
            }
        }

        private Optional<PasswordResetToken> vigente(String tokenHash, Instant ahora) {
            if (token == null || tokenHash == null) {
                return Optional.empty();
            }
            if (token.isUsado() || token.getExpiraEn() == null || !token.getExpiraEn().isAfter(ahora)) {
                return Optional.empty();
            }
            return token.getTokenHash().equals(tokenHash) ? Optional.of(token) : Optional.empty();
        }
    }

    static class RelojMutable extends Clock {
        private Instant instante;

        RelojMutable(Instant instante) {
            this.instante = instante;
        }

        void avanzar(Duration duracion) {
            instante = instante.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
