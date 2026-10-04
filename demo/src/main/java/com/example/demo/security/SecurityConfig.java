package com.example.demo.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * IMPORTANTE: La dependencia spring-boot-starter-oauth2-client está en el
 * classpath. Si las variables GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET NO están
 * definidas en el entorno, Spring Boot fallará al construir el
 * ClientRegistrationRepository y Spring Security caerá en su comportamiento
 * por defecto (proteger TODAS las rutas, incluyendo "/").
 *
 * Solución: leemos las credenciales con @Value y solo activamos OAuth2 si
 * ambas están presentes y no vacías.
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

    // Leemos las credenciales de Google desde el entorno.
    // Si no están definidas, el valor será null o vacío.
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
                .ignoringRequestMatchers("/api/**")
            )
            .authorizeHttpRequests(authz -> authz
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
                    // Recursos estáticos
                    "/*.css",
                    "/css/**",
                    "/*.js",
                    "/js/**",
                    "/images/**",
                    "/webjars/**",
                    "/favicon.ico",
                    // OAuth2 (solo si está activo)
                    "/oauth2/**",
                    "/login/oauth2/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
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

        // OAuth2 solo se activa si las credenciales de Google están presentes.
        // Esto evita que Spring Security bloquee todas las rutas cuando las
        // variables de entorno no están definidas en Render.
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
