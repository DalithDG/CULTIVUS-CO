package com.example.demo.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuración de Spring Security.
 *
 * OAuth2 con Google y Facebook es OPCIONAL: solo se activa si las credenciales
 * (client-id y client-secret) de al menos un proveedor están presentes y no
 * vacías. Si no lo están, la app funciona con el login por formulario.
 *
 * IMPORTANTE: "/error" debe ser público. Si no lo es, cualquier excepción
 * en un controlador (por ejemplo, un fallo al conectar con MongoDB) termina
 * redirigiendo al login y oculta el error real.
 *
 * AISLAMIENTO POR ROL: cada usuario solo accede a las rutas de su rol
 * principal.
 * El rol principal se resuelve con la misma jerarquía que
 * {@code Usuario.getRol()}:
 * ADMIN &gt; TRANSPORTISTA &gt; VENDEDOR &gt; COMPRADOR. Así, por ejemplo, un
 * administrador no puede entrar a /vendedor/**, /transportista/**, /usuario/**
 * ni
 * /carrito/**, y un vendedor no puede entrar a /admin/** ni a las rutas de
 * compra.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Jerarquía de roles (de mayor a menor), alineada con Usuario.getRol(). */
    static final List<String> JERARQUIA_ROLES = List.of("ADMIN", "TRANSPORTISTA", "VENDEDOR", "COMPRADOR");

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Autowired
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    @Autowired(required = false)
    private OAuth2UsuarioService oAuth2UsuarioService;

    @Autowired(required = false)
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    // Credenciales de Google leídas desde las propiedades de Spring
    // (que a su vez vienen de las variables de entorno en Render).
    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String googleClientSecret;

    // Credenciales de Facebook leídas desde las propiedades de Spring
    // (que a su vez vienen de las variables de entorno en Render).
    @Value("${spring.security.oauth2.client.registration.facebook.client-id:}")
    private String facebookClientId;

    @Value("${spring.security.oauth2.client.registration.facebook.client-secret:}")
    private String facebookClientSecret;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Registro de sesiones activas. Permite invalidar las sesiones de un
     * usuario cuando restablece su contraseña.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Necesario para que SessionRegistry elimine las sesiones destruidas y no
     * acumule información en memoria.
     */
    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            SessionRegistry sessionRegistry) throws Exception {
        http
                .csrf(csrf -> csrf
                        // NO QUITAR: el webhook de Wompi (server-to-server) no envia sesion ni token
                        // CSRF.
                        // Se deja explicito (y no solo bajo "/api/**") para que siga exento cuando se
                        // active
                        // CSRF global en el Sprint 5. Su autenticidad se valida con el checksum del
                        // evento.
                        .ignoringRequestMatchers("/api/wompi/webhook")
                        .ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(authz -> authz
                        // NO QUITAR: webhook de Wompi, publico a proposito (ver comentario en csrf).
                        .requestMatchers(HttpMethod.POST, "/api/wompi/webhook").permitAll()
                        .requestMatchers(
                                "/",
                                "/login",
                                "/registro",
                                "/register",
                                "/usuario/login", "/comprador/login",
                                "/usuario/registro", "/comprador/registro",
                                "/usuario/guardar", "/comprador/guardar",
                                // Recuperación de contraseña: solo las rutas necesarias,
                                // públicas pero con CSRF activo.
                                "/usuario/recuperar", "/comprador/recuperar",
                                "/usuario/restablecer", "/comprador/restablecer",
                                "/usuario/logout", "/comprador/logout",
                                "/logout",
                                "/category",
                                "/category/**",
                                "/categoria/**",
                                "/categoria",
                                "/productos/**",
                                "/producto/**",
                                "/oferta/**",
                                "/buscar/**",
                                "/nosotros",
                                "/productores",
                                "/productores/**",
                                "/ofertas",
                                "/frutas",
                                "/verduras",
                                "/lacteos",
                                "/cafe",
                                "/granos",
                                "/miel",
                                "/carnes",
                                "/bebidas",
                                "/condimentos",
                                "/panaderia",
                                "/otros",
                                "/api/**",
                                // Página de error de Spring (necesaria para ver el error real)
                                "/error",
                                // Recursos estáticos
                                "/*.css",
                                "/css/**",
                                "/*.js",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/favicon.ico",
                                // OAuth2 (solo se usan si está activo)
                                "/oauth2/**",
                                "/login/oauth2/**")
                        .permitAll()

                        // ── Administrador: solo su panel ─────────────────────────────
                        .requestMatchers("/admin/**").access(soloRol("ADMIN"))

                        // ── Vendedor ────────────────────────────────────────────────
                        // El registro lo puede abrir un visitante o un comprador (nunca admin ni
                        // transportista).
                        .requestMatchers("/vendedor/registro", "/vendedor/guardar")
                        .access(anonimoORol("COMPRADOR", "VENDEDOR"))
                        .requestMatchers("/vendedor/**").access(soloRol("VENDEDOR"))

                        // ── Transportista ───────────────────────────────────────────
                        .requestMatchers("/transportista/registro", "/transportista/guardar", "/transportista/login")
                        .access(anonimoORol("COMPRADOR", "TRANSPORTISTA"))
                        .requestMatchers("/transportista/**").access(soloRol("TRANSPORTISTA"))

                        // ── Rutas compartidas (redirigen al panel de cada rol) ───────
                        .requestMatchers(
                                "/usuario/inicio",
                                "/usuario/perfil",
                                "/usuario/perfil/actualizar",
                                "/usuario/billetera",
                                "/usuario/notificaciones")
                        .access(soloRol("COMPRADOR", "VENDEDOR", "TRANSPORTISTA"))

                        // ── Comprador: compras, carrito y pedidos ────────────────────
                        .requestMatchers("/usuario/**").access(soloRol("COMPRADOR"))
                        .requestMatchers("/comprador/**").access(soloRol("COMPRADOR"))
                        .requestMatchers("/carrito/**").access(soloRol("COMPRADOR"))

                        .anyRequest().authenticated())
                // maximumSessions(-1) = sin límite de sesiones, pero registrando
                // cada sesión para poder expirarlas tras un cambio de contraseña.
                .sessionManagement(session -> session
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(-1)
                                .sessionRegistry(sessionRegistry)
                                .expiredUrl("/usuario/login?sesionExpirada=true")))
                .formLogin(form -> form
                        .loginPage("/usuario/login")
                        .loginProcessingUrl("/usuario/login")
                        .usernameParameter("email")
                        .passwordParameter("contrasena")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureUrl("/usuario/login?error=true")
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/usuario/logout"))
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(customAccessDeniedHandler)
                        .authenticationEntryPoint(
                                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint(
                                        "/usuario/login")));

        // OAuth2 solo se activa si las credenciales de Google o Facebook están
        // presentes y los beans necesarios existen.
        boolean googleHabilitado = StringUtils.hasText(googleClientId)
                && StringUtils.hasText(googleClientSecret);

        boolean facebookHabilitado = StringUtils.hasText(facebookClientId)
                && StringUtils.hasText(facebookClientSecret)
                && !"disabled".equalsIgnoreCase(facebookClientId);

        boolean oauthHabilitado = (googleHabilitado || facebookHabilitado)
                && clientRegistrationRepository != null
                && oAuth2UsuarioService != null
                && oAuth2LoginSuccessHandler != null;

        if (oauthHabilitado) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/usuario/login")
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(oAuth2UsuarioService))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureUrl("/usuario/login?error=oauth_failed"));
        }

        return http.build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reglas de autorización por rol principal
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Devuelve el rol principal (sin prefijo ROLE_) de la autenticación, o null si
     * es anónima.
     */
    static String rolPrincipal(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Set<String> autoridades = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        for (String rol : JERARQUIA_ROLES) {
            if (autoridades.contains("ROLE_" + rol)) {
                return rol;
            }
        }
        return "";
    }

    /** Permite el acceso solo si el rol principal del usuario está entre los indicados. */
    private static AuthorizationManager<RequestAuthorizationContext> soloRol(String... roles) {
        List<String> permitidos = Arrays.asList(roles);
        return (authentication, context) -> {
            String rol = rolPrincipal(authentication.get());
            return new AuthorizationDecision(rol != null && permitidos.contains(rol));
        };
    }

/**
 * Permite el acceso a visitantes anónimos o a usuarios cuyo rol principal esté
 * entre los indicados.
 */
    private static AuthorizationManager<RequestAuthorizationContext> anonimoORol(String... roles) {
        List<String> permitidos = Arrays.asList(roles);
        return (authentication, context) -> {
            String rol = rolPrincipal(authentication.get());
            return new AuthorizationDecision(rol == null || permitidos.contains(rol));
        };
    }
}
