package com.example.demo.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;

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
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

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
                // NO QUITAR: el webhook de Wompi (server-to-server) no envia sesion ni token CSRF.
                // Se deja explicito (y no solo bajo "/api/**") para que siga exento cuando se active
                // CSRF global en el Sprint 5. Su autenticidad se valida con el checksum del evento.
                .ignoringRequestMatchers("/api/wompi/webhook")
                .ignoringRequestMatchers("/api/**")
            )
            .authorizeHttpRequests(authz -> authz
                // NO QUITAR: webhook de Wompi, publico a proposito (ver comentario en csrf).
                .requestMatchers(HttpMethod.POST, "/api/wompi/webhook").permitAll()
                .requestMatchers(
                    "/",
                    "/login",
                    "/registro",
                    "/register",
                    "/usuario/login",
                    "/usuario/registro",
                    "/usuario/guardar",
                    // Recuperación de contraseña: solo las rutas necesarias,
                    // públicas pero con CSRF activo.
                    "/usuario/recuperar",
                    "/usuario/restablecer",
                    "/usuario/logout",
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
                    "/login/oauth2/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/vendedor/registro", "/vendedor/guardar").authenticated()
                .requestMatchers("/vendedor/**").hasAnyRole("VENDEDOR", "ADMIN")
                .requestMatchers("/transportista/registro", "/transportista/guardar", "/transportista/login").permitAll()
                .requestMatchers("/transportista/**").hasAnyRole("TRANSPORTISTA", "ADMIN")
                .anyRequest().authenticated()
            )
            // maximumSessions(-1) = sin límite de sesiones, pero registrando
            // cada sesión para poder expirarlas tras un cambio de contraseña.
            .sessionManagement(session -> session
                .sessionConcurrency(concurrency -> concurrency
                    .maximumSessions(-1)
                    .sessionRegistry(sessionRegistry)
                    .expiredUrl("/usuario/login?sesionExpirada=true")
                )
            )
            .formLogin(form -> form
                .loginPage("/usuario/login")
                .loginProcessingUrl("/usuario/login")
                .usernameParameter("email")
                .passwordParameter("contrasena")
                .successHandler(customAuthenticationSuccessHandler)
                .failureUrl("/usuario/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/usuario/logout"))
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

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
                    .userService(oAuth2UsuarioService)
                )
                .successHandler(oAuth2LoginSuccessHandler)
                .failureUrl("/usuario/login?error=oauth_failed")
            );
        }

        return http.build();
    }
}