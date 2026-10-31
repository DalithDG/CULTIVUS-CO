package com.example.demo.security;

import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Maneja excepciones de acceso denegado (HTTP 403).
 * Redirige usuarios autenticados sin el rol adecuado a su panel o a la página de registro
 * correspondiente con un mensaje descriptivo en la sesión, evitando pantallas de error abruptas.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        HttpSession session = request.getSession();
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            response.sendRedirect("/usuario/login");
            return;
        }

        String email = null;
        if (auth.getPrincipal() instanceof UserDetails) {
            email = ((UserDetails) auth.getPrincipal()).getUsername();
        } else if (auth.getName() != null) {
            email = auth.getName();
        }

        Usuario usuario = null;
        if (email != null) {
            usuario = usuarioRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        }

        String requestUri = request.getRequestURI();

        if (requestUri != null && requestUri.startsWith("/vendedor")) {
            session.setAttribute("error", "No tienes una tienda registrada como vendedor. Regístrate aquí para comenzar.");
            response.sendRedirect("/vendedor/registro");
            return;
        }

        if (requestUri != null && requestUri.startsWith("/transportista")) {
            session.setAttribute("error", "No tienes un perfil de transportista activo. Regístrate aquí para unirte.");
            response.sendRedirect("/transportista/registro");
            return;
        }

        if (requestUri != null && requestUri.startsWith("/admin")) {
            session.setAttribute("error", "Acceso denegado: se requieren permisos de administrador.");
            if (usuario != null) {
                if (usuario.hasRole(Role.VENDEDOR)) {
                    response.sendRedirect("/vendedor/dashboard");
                    return;
                } else if (usuario.hasRole(Role.TRANSPORTISTA)) {
                    response.sendRedirect("/transportista/inicio");
                    return;
                }
            }
            response.sendRedirect("/usuario/inicio");
            return;
        }

        session.setAttribute("error", "No tienes permisos suficientes para acceder a esa sección.");
        if (usuario != null) {
            if (usuario.hasRole(Role.ADMIN)) {
                response.sendRedirect("/admin/dashboard");
                return;
            } else if (usuario.hasRole(Role.TRANSPORTISTA)) {
                response.sendRedirect("/transportista/inicio");
                return;
            } else if (usuario.hasRole(Role.VENDEDOR)) {
                response.sendRedirect("/vendedor/dashboard");
                return;
            }
        }
        response.sendRedirect("/usuario/inicio");
    }
}

