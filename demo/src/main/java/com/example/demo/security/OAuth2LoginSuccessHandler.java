package com.example.demo.security;

import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Se ejecuta automáticamente cuando el usuario se autentica con Google exitosamente.
 * Recupera el Usuario desde MongoDB, lo pone en HttpSession y redirige según su rol.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        if (email == null) {
            response.sendRedirect("/usuario/login?error=oauth_email");
            return;
        }

        String emailLimpio = email.trim().toLowerCase();

        // Buscar el usuario en MongoDB (ya fue creado por OAuth2UsuarioService)
        Usuario usuario = usuarioRepository.findByEmail(emailLimpio).orElse(null);

        if (usuario == null) {
            // Esto no debería ocurrir, pero como seguridad redirigimos al login
            response.sendRedirect("/usuario/login?error=usuario_no_encontrado");
            return;
        }

        // Guardar el Usuario en la sesión (mismo mecanismo que el login manual)
        HttpSession session = request.getSession();
        session.setAttribute("usuarioLogueado", usuario);

        // Redirigir según el rol
        String rol = usuario.getRol();
        if ("ADMIN".equalsIgnoreCase(rol)) {
            getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
        } else {
            getRedirectStrategy().sendRedirect(request, response, "/usuario/inicio");
        }
    }
}
