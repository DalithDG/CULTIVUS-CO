package com.example.demo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Temporalmente desactivado para evitar errores
    // @Autowired
    // private CustomUserDetailsService customUserDetailsService;

    // @Bean
    // public PasswordEncoder passwordEncoder() {
    //     return new BCryptPasswordEncoder();
    // }

    // @Bean
    // public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
    //     AuthenticationManagerBuilder auth = http.getSharedObject(AuthenticationManagerBuilder.class);
    //     auth.userDetailsService(customUserDetailsService).passwordEncoder(passwordEncoder());
    //     return auth.build();
    // }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Deshabilitado momentn 
            .csrf(csrf -> csrf.disable())
            
            .authorizeHttpRequests(authz -> authz
                // Permitir todo temporalmente
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
