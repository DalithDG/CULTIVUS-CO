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
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;

/**
 * Se ejecuta automáticamente tras un inicio de sesión exitoso por formulario.
 * Carga el objeto Usuario completo en la sesión HTTP (usuarioLogueado)
 * y redirige al usuario a su panel correspondiente según su rol o a la
 * página que intentó abrir (validando que sea una ruta relativa segura).
 */
@Component
public class CustomAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;

    @Autowired(required = false)
    private com.example.demo.repository.CarritoRepository carritoRepository;

    @Autowired
    public CustomAuthenticationSuccessHandler(UsuarioRepository usuarioRepository,
                                             NotificacionRepository notificacionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.notificacionRepository = notificacionRepository;
    }

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

        clearAuthenticationAttributes(request);

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

                // Si había un carrito anónimo en sesión y el usuario puede comprar, fusionar ítems
                if (usuario.puedeComprar() && carritoRepository != null) {
                    try {
                        com.example.demo.Model.Carrito anonimo = (com.example.demo.Model.Carrito) session.getAttribute("carritoAnonimo");
                        if (anonimo != null && anonimo.getItems() != null && !anonimo.getItems().isEmpty()) {
                            com.example.demo.Model.Carrito carritoUsuario = carritoRepository.findByUsuarioId(usuario.getId())
                                    .orElseGet(() -> new com.example.demo.Model.Carrito(usuario.getId()));
                            for (com.example.demo.Model.embebidos.ProductoCarrito itemAnonimo : anonimo.getItems()) {
                                java.util.Optional<com.example.demo.Model.embebidos.ProductoCarrito> existente = carritoUsuario.getItems().stream()
                                        .filter(i -> itemAnonimo.getOfertaId() != null && itemAnonimo.getOfertaId().equals(i.getOfertaId()))
                                        .findFirst();
                                if (existente.isPresent()) {
                                    existente.get().setCantidad(com.example.demo.services.ValidacionCantidad.sumar(existente.get().getCantidad(), itemAnonimo.getCantidad()));
                                } else {
                                    carritoUsuario.getItems().add(itemAnonimo);
                                }
                            }
                            carritoUsuario.recalcularTotal();
                            carritoUsuario.setUpdatedAt(LocalDateTime.now());
                            carritoRepository.save(carritoUsuario);
                            session.removeAttribute("carritoAnonimo");
                        }
                    } catch (Exception ignored) {
                    }
                }

                // Revisar si hay una URL de retorno solicitada explícitamente o guardada por Spring Security
                String redirectParam = request.getParameter("redirect");
                String targetUrl = null;

                if (esRutaSegura(redirectParam)) {
                    targetUrl = redirectParam;
                } else {
                    RequestCache requestCache = new HttpSessionRequestCache();
                    SavedRequest savedRequest = requestCache.getRequest(request, response);
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
                }

                if (targetUrl != null) {
                    // Regla de negocio: un vendedor no puede comprar (/carrito o /pago)
                    if (usuario.hasRole(Role.VENDEDOR) && (targetUrl.startsWith("/carrito") || targetUrl.startsWith("/pago"))) {
                        session.setAttribute("error", Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR);
                        getRedirectStrategy().sendRedirect(request, response, "/vendedor/dashboard");
                        return;
                    }
                    getRedirectStrategy().sendRedirect(request, response, targetUrl);
                    return;
                }

                if (usuario.hasRole(Role.ADMIN)) {
                    getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
                    return;
                } else if (usuario.hasRole(Role.TRANSPORTISTA)) {
                    getRedirectStrategy().sendRedirect(request, response, "/transportista/inicio");
                    return;
                } else if (usuario.hasRole(Role.VENDEDOR)) {
                    getRedirectStrategy().sendRedirect(request, response, "/vendedor/dashboard");
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
