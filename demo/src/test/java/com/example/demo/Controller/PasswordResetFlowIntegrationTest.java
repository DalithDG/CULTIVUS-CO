package com.example.demo.Controller;

import com.example.demo.Model.PasswordResetToken;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.PasswordResetMailService;
import com.example.demo.services.PasswordResetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Flujo HTTP completo: Spring Security + CSRF + controlador + servicio +
 * MongoDB de test.
 *
 * Requiere MongoDB local en localhost:27017. Se puede apuntar a otro servidor:
 *   TEST_MONGODB_URI=mongodb://host:27017
 *   TEST_MONGODB_DATABASE=cultivus_test_recuperacion
 *
 * Usa una base separada y solo la borra al inicio/fin de cada prueba. Nunca
 * toca la base de desarrollo/producción.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowIntegrationTest {

    private static final String EMAIL = "ana.integracion@example.com";
    private static final String CLAVE_VIEJA = "Vieja123!";
    private static final String CLAVE_NUEVA = "NuevaClave1!";

    @DynamicPropertySource
    static void configurarPropiedades(DynamicPropertyRegistry registry) {
        String uri = System.getenv().getOrDefault(
                "TEST_MONGODB_URI", "mongodb://localhost:27017");
        String database = System.getenv().getOrDefault(
                "TEST_MONGODB_DATABASE", "cultivus_test_recuperacion");

        registry.add("spring.data.mongodb.uri", () -> uri);
        registry.add("spring.data.mongodb.database", () -> database);
        registry.add("spring.mail.host", () -> "");
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("app.base-url", () -> "http://localhost:8080");
        registry.add("spring.thymeleaf.cache", () -> false);
        // Evita que el límite de frecuencia interfiera con las pruebas.
        registry.add("cultivus.password-reset.max-solicitudes-por-email", () -> 100);
        registry.add("cultivus.password-reset.max-solicitudes-por-ip", () -> 100);
        registry.add("cultivus.password-reset.max-intentos-por-ip", () -> 100);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetService passwordResetService;

    @MockBean
    private PasswordResetMailService mailService;

    @BeforeEach
    void limpiarAntes() {
        tokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @AfterEach
    void limpiarDespues() {
        tokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    @DisplayName("Flujo completo: solicitar, abrir enlace, cambiar contraseña y volver a entrar")
    void flujoCompleto() throws Exception {
        crearUsuario();

        String token = solicitarEnlace(EMAIL);

        // El enlace del correo abre el formulario con el token en un campo oculto.
        mockMvc.perform(get("/usuario/restablecer").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/restablecer-password"))
                .andExpect(model().attribute("tokenValido", true))
                .andExpect(content().string(containsString("Nueva contraseña")))
                .andExpect(content().string(containsString("name=\"token\"")));

        restablecer(token, CLAVE_NUEVA, CLAVE_NUEVA)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuario/login"))
                .andExpect(flash().attribute("mensaje", containsString("actualizó")));

        Usuario actualizado = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertTrue(passwordEncoder.matches(CLAVE_NUEVA, actualizado.getContrasena()));
        assertFalse(passwordEncoder.matches(CLAVE_VIEJA, actualizado.getContrasena()));

        // La nueva contraseña funciona en el login real (mismo DaoAuthenticationProvider).
        mockMvc.perform(post("/usuario/login").with(csrf())
                        .param("email", EMAIL)
                        .param("contrasena", CLAVE_NUEVA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuario/inicio"));

        // La anterior ya no funciona.
        mockMvc.perform(post("/usuario/login").with(csrf())
                        .param("email", EMAIL)
                        .param("contrasena", CLAVE_VIEJA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuario/login?error=true"));
    }

    @Test
    @DisplayName("El enlace no se puede reutilizar después del cambio")
    void tokenDeUnSoloUso() throws Exception {
        crearUsuario();
        String token = solicitarEnlace(EMAIL);

        restablecer(token, CLAVE_NUEVA, CLAVE_NUEVA)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuario/login"));

        // Segundo intento con el mismo token: se muestra el estado seguro.
        restablecer(token, "OtraClave2@", "OtraClave2@")
                .andExpect(status().isOk())
                .andExpect(view().name("auth/restablecer-password"))
                .andExpect(model().attribute("tokenValido", false))
                .andExpect(content().string(containsString("Enlace no válido")));

        Usuario actualizado = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertTrue(passwordEncoder.matches(CLAVE_NUEVA, actualizado.getContrasena()));
    }

    @Test
    @DisplayName("Un token vencido no cambia la contraseña, aunque el TTL aún no lo haya borrado")
    void enlaceExpiradoSeRechazaEnServidor() throws Exception {
        crearUsuario();
        String token = solicitarEnlace(EMAIL);

        Usuario usuario = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        Optional<PasswordResetToken> guardado = tokenRepository.findById(usuario.getId());
        assertTrue(guardado.isPresent());
        PasswordResetToken documento = guardado.get();
        documento.setExpiraEn(Instant.now().minusSeconds(60));
        tokenRepository.save(documento);

        mockMvc.perform(get("/usuario/restablecer").param("token", token))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tokenValido", false))
                .andExpect(content().string(containsString("Enlace no válido")));

        restablecer(token, CLAVE_NUEVA, CLAVE_NUEVA)
                .andExpect(status().isOk())
                .andExpect(model().attribute("tokenValido", false));

        Usuario sinCambios = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertTrue(passwordEncoder.matches(CLAVE_VIEJA, sinCambios.getContrasena()));
    }

    @Test
    @DisplayName("Contraseñas distintas no consumen el token ni cambian la clave")
    void contrasenasNoCoincidenNoConsumenToken() throws Exception {
        crearUsuario();
        String token = solicitarEnlace(EMAIL);

        restablecer(token, CLAVE_NUEVA, "OtraClave2@")
                .andExpect(status().isOk())
                .andExpect(view().name("auth/restablecer-password"))
                .andExpect(model().attribute("tokenValido", true))
                .andExpect(content().string(containsString("no coinciden")));

        // El token sigue vigente: el usuario puede corregir y guardar.
        restablecer(token, CLAVE_NUEVA, CLAVE_NUEVA)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuario/login"));
    }

    @Test
    @DisplayName("Contraseña débil: error de política sin consumir el enlace")
    void contrasenaDebilSeRechaza() throws Exception {
        crearUsuario();
        String token = solicitarEnlace(EMAIL);

        restablecer(token, "debil", "debil")
                .andExpect(status().isOk())
                .andExpect(model().attribute("tokenValido", true))
                .andExpect(content().string(containsString("al menos 8")));

        assertTrue(tokenRepository.findById(
                usuarioRepository.findByEmail(EMAIL).orElseThrow().getId()).isPresent());
    }

    @Test
    @DisplayName("Sin token CSRF la solicitud se rechaza (protección activa)")
    void solicitudSinCsrfSeRechaza() throws Exception {
        crearUsuario();

        mockMvc.perform(post("/usuario/recuperar")
                        .param("email", EMAIL))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mailService);
        assertEquals(0, tokenRepository.count());
    }

    @Test
    @DisplayName("Correo no registrado: misma respuesta genérica y ningún envío")
    void correoNoRegistradoNoRevelaNada() throws Exception {
        mockMvc.perform(post("/usuario/recuperar").with(csrf())
                        .param("email", "no.registrado@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuario/recuperar"))
                .andExpect(flash().attribute("mensaje", containsString("Si el correo está registrado")));

        verify(mailService, never()).enviarEnlace(anyString(), anyString(), anyString());
        assertEquals(0, tokenRepository.count());
    }

    @Test
    @DisplayName("Las páginas de recuperación son públicas y no exponen caché")
    void paginasPublicasSinCache() throws Exception {
        mockMvc.perform(get("/usuario/recuperar"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/recuperar-password"))
                .andExpect(content().string(containsString("Recuperar contraseña")))
                .andExpect(result -> {
                    String cache = result.getResponse().getHeader("Cache-Control");
                    assertTrue(cache != null && cache.contains("no-store"));
                    assertEquals("no-referrer", result.getResponse().getHeader("Referrer-Policy"));
                });
    }

    @Test
    @DisplayName("Dos peticiones concurrentes con el mismo token: solo una puede cambiar la clave")
    void consumoConcurrenteSoloUnaGana() throws Exception {
        crearUsuario();
        String token = solicitarEnlace(EMAIL);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);

        Callable<Boolean> intento = () -> {
            inicio.await(5, TimeUnit.SECONDS);
            try {
                passwordResetService.restablecerContrasena(token, CLAVE_NUEVA, "198.51.100.77");
                return true;
            } catch (PasswordResetService.TokenInvalidoException e) {
                return false;
            }
        };

        try {
            Future<Boolean> primero = pool.submit(intento);
            Future<Boolean> segundo = pool.submit(intento);
            inicio.countDown();

            int exitos = (primero.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (segundo.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, exitos, "El findAndModify debe permitir un único consumo");
        } finally {
            pool.shutdownNow();
        }

        Usuario actualizado = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertTrue(passwordEncoder.matches(CLAVE_NUEVA, actualizado.getContrasena()));
    }

    // ── helpers ───────────────────────────────────────────────────────

    private void crearUsuario() {
        Usuario usuario = new Usuario("Ana Integración", EMAIL,
                passwordEncoder.encode(CLAVE_VIEJA), Role.COMPRADOR);
        usuarioRepository.save(usuario);
    }

    private String solicitarEnlace(String email) throws Exception {
        mockMvc.perform(post("/usuario/recuperar").with(csrf())
                        .param("email", email))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuario/recuperar"))
                .andExpect(flash().attribute("mensaje", containsString("Si el correo está registrado")));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailService).enviarEnlace(eq(email), anyString(), captor.capture());
        return captor.getValue();
    }

    private ResultActions restablecer(String token, String nueva, String confirmar) throws Exception {
        return mockMvc.perform(post("/usuario/restablecer").with(csrf())
                .param("token", token)
                .param("nuevaContrasena", nueva)
                .param("confirmarContrasena", confirmar));
    }
}
