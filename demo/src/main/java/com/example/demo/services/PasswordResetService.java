package com.example.demo.services;

import com.example.demo.Config.PasswordResetProperties;
import com.example.demo.Model.PasswordResetToken;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lógica del flujo "¿Olvidaste tu contraseña?".
 *
 * Decisiones de seguridad:
 * - Token aleatorio de 256 bits; solo se persiste su hash SHA-256.
 * - Un único token por usuario: pedir otro invalida el anterior.
 * - Expiración comprobada en cada operación (el TTL de Mongo solo limpia).
 * - Consumo atómico con findAndModify para impedir doble uso concurrente.
 * - Respuesta genérica: no se revela si el correo existe.
 * - No se registran correos, tokens, contraseñas ni credenciales SMTP.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int BYTES_TOKEN = 32; // 256 bits
    private static final Pattern FORMATO_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Duration TTL_POR_DEFECTO = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenStore tokenStore;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailService mailService;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordResetProperties properties;
    private final SessionRegistry sessionRegistry;
    private final Clock clock;

    public PasswordResetService(UsuarioRepository usuarioRepository,
                                PasswordResetTokenStore tokenStore,
                                PasswordEncoder passwordEncoder,
                                PasswordResetMailService mailService,
                                PasswordResetRateLimiter rateLimiter,
                                PasswordResetProperties properties,
                                SessionRegistry sessionRegistry,
                                Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.tokenStore = tokenStore;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.sessionRegistry = sessionRegistry;
        this.clock = clock;
    }

    /**
     * Procesa una solicitud. No devuelve ni registra si el correo existe.
     * Si se supera el límite de frecuencia simplemente no hace nada: el
     * controlador responde igual para no filtrar información.
     */
    public void solicitarRecuperacion(String email, String ip) {
        String correo = normalizarEmail(email);
        if (correo == null) {
            return;
        }

        Duration ventana = ventanaSegura();
        int maxEmail = Math.max(1, properties.getMaxSolicitudesPorEmail());
        int maxIp = Math.max(1, properties.getMaxSolicitudesPorIp());

        if (!rateLimiter.permitir("reset-email:" + correo, maxEmail, ventana)) {
            log.info("Solicitud de recuperación limitada por frecuencia (correo)");
            return;
        }
        if (!rateLimiter.permitir("reset-ip:" + ipSegura(ip), maxIp, ventana)) {
            log.info("Solicitud de recuperación limitada por frecuencia (IP)");
            return;
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(correo);
        if (usuarioOpt.isEmpty()) {
            usuarioOpt = usuarioRepository.findByEmailIgnoreCase(correo);
        }
        if (usuarioOpt.isEmpty()) {
            // Misma respuesta y mismo trabajo aproximado que si existiera.
            // El log es solo para diagnóstico interno; nunca se registra el correo.
            log.info("Solicitud de recuperación: no hay cuenta asociada en la base de datos");
            return;
        }

        log.info("Solicitud de recuperación: cuenta localizada; se genera el token");
        Usuario usuario = usuarioOpt.get();
        Instant ahora = clock.instant();
        String tokenOriginal = generarToken();
        String hash = hashToken(tokenOriginal);

        PasswordResetToken token = new PasswordResetToken(
                usuario.getId(),
                hash,
                correo,
                ahora,
                ahora.plus(ttlSeguro()));

        tokenStore.guardar(token);

        try {
            mailService.enviarEnlace(usuario.getEmail(), usuario.getNombre(), tokenOriginal);
        } catch (Exception e) {
            // El correo se envía en otro hilo; este catch cubre rechazos del
            // executor o errores síncronos. Nunca se registra el token.
            log.error("No se pudo encolar el correo de recuperación ({})", e.getClass().getSimpleName());
        }

        log.info("Solicitud de recuperación: token guardado y correo encolado");
    }

    /**
     * Obtiene y consume el último enlace generado para un correo cuando el
     * modo de enlace directo en pantalla está habilitado (útil en Render Free
     * o con correos inventados sin bandeja SMTP).
     */
    public void actualizarBaseUrlSiLocalhost(String urlDetectada) {
        if (urlDetectada != null) {
            mailService.actualizarBaseUrlSiLocalhost(urlDetectada);
        }
    }

    /**
     * Valida un token sin consumirlo (para mostrar el formulario).
     */
    public boolean tokenValido(String tokenOriginal) {
        if (!formatoTokenValido(tokenOriginal)) {
            return false;
        }
        return tokenStore.buscarVigente(hashToken(tokenOriginal), clock.instant()).isPresent();
    }

    /**
     * Restablece la contraseña consumiendo el token de forma atómica.
     * Si el token ya fue usado, expiró o no existe, lanza TokenInvalidoException.
     */
    public void restablecerContrasena(String tokenOriginal, String nuevaContrasena, String ip) {
        Duration ventana = ventanaSegura();
        int maxIntentos = Math.max(1, properties.getMaxIntentosPorIp());
        if (!rateLimiter.permitir("reset-intento:" + ipSegura(ip), maxIntentos, ventana)) {
            throw new SolicitudLimitadaException(
                    "Demasiados intentos. Espera unos minutos y vuelve a intentarlo.");
        }

        String errorPolitica = PasswordPolicy.validar(nuevaContrasena);
        if (errorPolitica != null) {
            throw new PasswordPolicyException(errorPolitica);
        }

        if (!formatoTokenValido(tokenOriginal)) {
            throw new TokenInvalidoException();
        }

        Instant ahora = clock.instant();
        String hash = hashToken(tokenOriginal);

        // Operación atómica: solo una petición concurrente obtiene el token.
        Optional<PasswordResetToken> consumido = tokenStore.consumirAtomicamente(hash, ahora);
        if (consumido.isEmpty()) {
            throw new TokenInvalidoException();
        }

        PasswordResetToken token = consumido.get();
        Usuario usuario = usuarioRepository.findById(token.getUsuarioId()).orElse(null);
        if (usuario == null) {
            throw new TokenInvalidoException();
        }

        // Se usa el mismo PasswordEncoder (BCrypt) que el login actual.
        usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
        usuarioRepository.save(usuario);

        // El token ya quedó usado; invalidar otros es defensivo.
        try {
            tokenStore.invalidarPendientes(usuario.getId(), ahora);
        } catch (Exception e) {
            log.warn("No se pudieron invalidar tokens pendientes ({})", e.getClass().getSimpleName());
        }

        expirarSesiones(usuario.getEmail());
        log.info("Contraseña restablecida correctamente");
    }

    /**
     * Invalida las sesiones activas del usuario (login por formulario y
     * OAuth2). Si el registro de sesiones no está disponible, no se rompe el
     * restablecimiento.
     */
    private void expirarSesiones(String email) {
        if (email == null) {
            return;
        }
        try {
            int expiradas = 0;
            for (Object principal : sessionRegistry.getAllPrincipals()) {
                String emailPrincipal = emailDePrincipal(principal);
                if (emailPrincipal != null && email.equalsIgnoreCase(emailPrincipal)) {
                    for (SessionInformation sesion : sessionRegistry.getAllSessions(principal, false)) {
                        sesion.expireNow();
                        expiradas++;
                    }
                }
            }
            if (expiradas > 0) {
                log.info("Sesiones invalidadas tras restablecimiento: {}", expiradas);
            }
        } catch (Exception e) {
            log.warn("No se pudieron invalidar las sesiones activas ({})", e.getClass().getSimpleName());
        }
    }

    private static String emailDePrincipal(Object principal) {
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof OAuth2User oAuth2User) {
            Object email = oAuth2User.getAttribute("email");
            return email != null ? email.toString() : null;
        }
        if (principal instanceof String texto) {
            return texto;
        }
        return null;
    }

    private static String normalizarEmail(String email) {
        if (email == null) {
            return null;
        }
        String limpio = email.trim().toLowerCase(Locale.ROOT);
        return limpio.isEmpty() ? null : limpio;
    }

    private static String ipSegura(String ip) {
        return (ip == null || ip.isBlank()) ? "desconocida" : ip.trim();
    }

    private Duration ttlSeguro() {
        Duration ttl = properties.getTokenTtl();
        return (ttl == null || ttl.isZero() || ttl.isNegative()) ? TTL_POR_DEFECTO : ttl;
    }

    private Duration ventanaSegura() {
        Duration ventana = properties.getVentana();
        return (ventana == null || ventana.isZero() || ventana.isNegative())
                ? Duration.ofMinutes(15)
                : ventana;
    }

    private static boolean formatoTokenValido(String token) {
        return token != null && FORMATO_TOKEN.matcher(token).matches();
    }

    private static String generarToken() {
        byte[] bytes = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 es suficiente porque el token tiene 256 bits de entropía: no es
     * factible hacer fuerza bruta sobre el hash aunque se filtre la base.
     */
    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // ── Excepciones con mensajes seguros para el usuario ──────────────

    public static class TokenInvalidoException extends RuntimeException {
        public TokenInvalidoException() {
            super("El enlace no es válido, expiró o ya fue utilizado.");
        }
    }

    public static class PasswordPolicyException extends RuntimeException {
        public PasswordPolicyException(String mensaje) {
            super(mensaje);
        }
    }

    public static class SolicitudLimitadaException extends RuntimeException {
        public SolicitudLimitadaException(String mensaje) {
            super(mensaje);
        }
    }
}
