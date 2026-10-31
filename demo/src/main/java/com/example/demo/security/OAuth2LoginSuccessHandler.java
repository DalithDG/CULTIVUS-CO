package com.example.demo.security;

import com.example.demo.Model.Role;
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
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Se ejecuta automáticamente cuando el usuario se autentica con Google exitosamente.
 * Recupera el Usuario desde MongoDB, lo pone en HttpSession y redirige según su
 * rol o a la página que intentó abrir previamente.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private boolean esRutaSegura(String url) {
        if (url == null || url.isBlank()) return false;
        if (!url.startsWith("/") || url.startsWith("//")) return false;
        String low = url.toLowerCase();
        return !low.startsWith("/usuario/login") && !low.startsWith("/usuario/logout")
                && !low.startsWith("/logout") && !low.startsWith("/error");
    }

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

        // Revisar si hay una URL de retorno guardada en la caché de peticiones
        RequestCache requestCache = new HttpSessionRequestCache();
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        String targetUrl = null;

        if (savedRequest != null) {
            String savedUrl = savedRequest.getRedirectUrl();
            if (savedUrl != null) {
                try {
                    URI uri = new URI(savedUrl);
                    String path = uri.getPath();
                    if (esRutaSegura(path)) {
                        targetUrl = path + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (targetUrl != null) {
            if (usuario.hasRole(Role.VENDEDOR) && (targetUrl.startsWith("/carrito") || targetUrl.startsWith("/pago"))) {
                session.setAttribute("error", Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR);
                getRedirectStrategy().sendRedirect(request, response, "/vendedor/dashboard");
                return;
            }
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            return;
        }

        // Redirigir según el rol
        if (usuario.hasRole(Role.ADMIN)) {
            getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
        } else if (usuario.hasRole(Role.TRANSPORTISTA)) {
            getRedirectStrategy().sendRedirect(request, response, "/transportista/inicio");
        } else if (usuario.hasRole(Role.VENDEDOR)) {
            getRedirectStrategy().sendRedirect(request, response, "/vendedor/dashboard");
        } else {
            getRedirectStrategy().sendRedirect(request, response, "/usuario/inicio");
        }
    }
}
