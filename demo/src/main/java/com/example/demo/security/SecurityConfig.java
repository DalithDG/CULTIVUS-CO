package com.example.demo.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OAuth2UsuarioService oAuth2UsuarioService;

    @Autowired
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    /**
     * Proveedor de autenticación para login con email/contraseña (BCrypt).
     */
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
            // CSRF deshabilitado — reactivar en Fase 2
            .csrf(csrf -> csrf.disable())

            // Por ahora permite todo — se restringirá en Fase 2
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            )

            // ✅ OAuth2 Login con Google
            .oauth2Login(oauth2 -> oauth2
                // Página de login personalizada (en lugar de la de Spring Security)
                .loginPage("/usuario/login")
                // Servicio que busca/crea el usuario en MongoDB tras autenticar con Google
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UsuarioService)
                )
                // Handler que pone al usuario en sesión y redirige según su rol
                .successHandler(oAuth2LoginSuccessHandler)
                // Si falla el login con Google, redirige al login con error
                .failureUrl("/usuario/login?error=oauth_failed")
            );

        return http.build();
    }
}
