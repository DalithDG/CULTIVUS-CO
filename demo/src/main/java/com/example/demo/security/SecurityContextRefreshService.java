package com.example.demo.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reconstruye el Authentication de Spring Security a partir de los roles actuales
 * del usuario en la base de datos (CustomUserDetailsService) y lo guarda en el
 * SecurityContext y en la HttpSession. Funciona tanto para cambios de rol como
 * para autenticar al usuario recién verificado en /usuario/verificar.
 */
@Service
public class SecurityContextRefreshService {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    public void refrescarAutenticacion(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        try {
            Authentication actual = SecurityContextHolder.getContext().getAuthentication();
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(email.trim().toLowerCase());
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities());
            if (actual != null && actual.getDetails() != null) {
                token.setDetails(actual.getDetails());
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(token);
            SecurityContextHolder.setContext(context);

            // Persistir en HttpSession para que la Authentication sobreviva a los siguientes requests
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpSession session = attrs.getRequest().getSession(true);
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            }
        } catch (Exception ignored) {
            // Si el usuario no existe, se mantiene la autenticación actual
        }
    }
}
