package com.example.demo.security;

import com.example.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reconstruye el Authentication de Spring Security a partir de los roles actuales
 * del usuario en la base de datos (CustomUserDetailsService) y lo guarda en el
 * SecurityContext. Evita que cambios de rol requieran cerrar sesión.
 */
@Service
public class SecurityContextRefreshService {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    public void refrescarAutenticacion(String email) {
        if (email == null) {
            return;
        }
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        if (actual == null || !actual.isAuthenticated()) {
            return;
        }
        try {
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            userDetails,
                            actual.getCredentials(),
                            userDetails.getAuthorities());
            token.setDetails(actual.getDetails());
            SecurityContextHolder.getContext().setAuthentication(token);

            // Persistir en HttpSession para que la nueva Authentication sobreviva al siguiente request
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpSession session = attrs.getRequest().getSession(false);
                if (session != null) {
                    SecurityContext context = SecurityContextHolder.getContext();
                    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
                }
            }
        } catch (Exception ignored) {
            // Si el usuario no existe, se mantiene la autenticación actual
        }
    }
}