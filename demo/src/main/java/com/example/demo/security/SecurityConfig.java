package com.example.demo.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Configuración de Spring Security.
 *
 * OAuth2 con Google es OPCIONAL: solo se activa si las credenciales
 * (client-id y client-secret) están presentes y no vacías. Si no lo están,
 * la app funciona con el login por formulario.
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

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
                    "/newregister",
                    "/loginnew",
                    "/usuario/login",
                    "/usuario/registro",
                    "/usuario/guardar",
                    "/usuario/logout",
                    "/logout",
                    "/category/**",
                    "/catalogo/**",
                    "/productos/**",
                    "/producto/**",
                    "/oferta/**",
                    "/buscar/**",
                    "/frutas",
                    "/verduras",
                    "/lacteos",
                    "/cafe",
                    "/granos",
                    "/miel",
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
                .anyRequest().authenticated()
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
                .logoutUrl("/usuario/logout")
                .logoutSuccessUrl("/usuario/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        // OAuth2 solo se activa si las credenciales de Google están presentes
        // y los beans necesarios existen.
        boolean oauthHabilitado = StringUtils.hasText(googleClientId)
                && StringUtils.hasText(googleClientSecret)
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