package com.example.demo.security;

import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.NotificacionRepository;
import com.example.demo.repository.UsuarioRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Se ejecuta automáticamente tras un inicio de sesión exitoso por formulario.
 * Carga el objeto Usuario completo en la sesión HTTP (usuarioLogueado)
 * y redirige al usuario a su panel correspondiente según su rol.
 */
@Component
public class CustomAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String email = null;
        if (authentication.getPrincipal() instanceof UserDetails) {
            email = ((UserDetails) authentication.getPrincipal()).getUsername();
        } else if (authentication.getName() != null) {
            email = authentication.getName();
        }

        if (email != null) {
            String emailLimpio = email.trim().toLowerCase();
            Usuario usuario = usuarioRepository.findByEmail(emailLimpio).orElse(null);

            if (usuario != null) {
                usuario.setUltimaConexion(LocalDateTime.now());
                usuarioRepository.save(usuario);

                HttpSession session = request.getSession();
                session.setAttribute("usuarioLogueado", usuario);

                long notificacionesCount = notificacionRepository.findByUsuarioIdOrderByFechaDesc(usuario.getId())
                        .stream().filter(n -> !n.isLeida()).count();
                session.setAttribute("notificacionesCount", notificacionesCount);

                if (usuario.hasRole(Role.ADMIN)) {
                    getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
                    return;
                } else if (usuario.hasRole(Role.VENDEDOR)) {
                    getRedirectStrategy().sendRedirect(request, response, "/vendedor/inicio");
                    return;
                } else {
                    getRedirectStrategy().sendRedirect(request, response, "/usuario/inicio");
                    return;
                }
            }
        }

        getRedirectStrategy().sendRedirect(request, response, "/");
    }
}
