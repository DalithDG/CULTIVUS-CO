package com.example.demo.security;

import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maneja excepciones de acceso denegado (HTTP 403).
 * Redirige usuarios autenticados sin el rol adecuado a su panel o a la página de registro
 * correspondiente con un mensaje descriptivo en la sesión, evitando pantallas de error abruptas.
 *
 * Cada usuario solo puede usar las rutas de su rol principal (ver {@link SecurityConfig}).
 * Si los roles cambiaron en la base de datos con la sesión abierta, se reconstruye la
 * autenticación una única vez y se reintenta la petición (solo GET).
 * Nunca redirige a la misma URL que fue denegada, para evitar bucles de redirección.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private static final String REINTENTO_KEY = "accesoDenegadoReintento";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        String contexto = request.getContextPath() != null ? request.getContextPath() : "";
        String requestUri = request.getRequestURI() != null ? request.getRequestURI() : "";
        String ruta = requestUri.startsWith(contexto) ? requestUri.substring(contexto.length()) : requestUri;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            response.sendRedirect(contexto + "/usuario/login");
            return;
        }

        HttpSession session = request.getSession();

        String email = obtenerEmail(auth);
        Usuario usuario = null;
        if (email != null) {
            try {
                usuario = usuarioRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
            } catch (Exception ignored) {
                // Si la base de datos falla se decide solo con la autenticación actual
            }
        }

        // 1) Roles desactualizados: reconstruir la autenticación una sola vez y reintentar (solo GET)
        if (usuario != null
                && "GET".equalsIgnoreCase(request.getMethod())
                && rolesDesactualizados(auth, usuario)
                && !ruta.equals(session.getAttribute(REINTENTO_KEY))) {
            Authentication nueva = reconstruirAutenticacion(auth, usuario.getEmail(), session);
            if (nueva != null) {
                session.setAttribute(REINTENTO_KEY, ruta);
                session.setAttribute("usuarioLogueado", usuario);
                response.sendRedirect(requestUri);
                return;
            }
        }
        session.removeAttribute(REINTENTO_KEY);

        // 2) El destino se calcula con los mismos roles que usa SecurityConfig (los de la sesión)
        String rolPrincipal = SecurityConfig.rolPrincipal(auth);
        boolean esComprador = "COMPRADOR".equals(rolPrincipal);

        String destino;
        if (ruta.startsWith("/vendedor") && esComprador) {
            session.setAttribute("error", "No tienes una tienda registrada como vendedor. Regístrate aquí para comenzar.");
            destino = "/vendedor/registro";
        } else if (ruta.startsWith("/transportista") && esComprador) {
            session.setAttribute("error", "No tienes un perfil de transportista activo. Regístrate aquí para unirte.");
            destino = "/transportista/registro";
        } else {
            session.setAttribute("error", ruta.startsWith("/admin")
                    ? "Acceso denegado: se requieren permisos de administrador."
                    : "No tienes permisos para acceder a esa sección con tu tipo de cuenta.");
            destino = panelDe(rolPrincipal);
        }

        // 3) Sin panel válido o el destino es la misma ruta denegada: cerrar sesión para no entrar en bucle
        if (destino == null || destino.equals(ruta)) {
            SecurityContextHolder.clearContext();
            session.invalidate();
            response.sendRedirect(contexto + "/usuario/login?error=acceso");
            return;
        }

        response.sendRedirect(contexto + destino);
    }

    /** Panel de inicio de cada rol principal; null si el usuario no tiene un rol reconocido. */
    private static String panelDe(String rol) {
        if (rol == null) {
            return null;
        }
        return switch (rol) {
            case "ADMIN" -> "/admin/dashboard";
            case "TRANSPORTISTA" -> "/transportista/inicio";
            case "VENDEDOR" -> "/vendedor/dashboard";
            case "COMPRADOR" -> "/usuario/inicio";
            default -> null;
        };
    }

    private static String obtenerEmail(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return auth.getName();
    }

    private static boolean rolesDesactualizados(Authentication auth, Usuario usuario) {
        if (usuario.getRoles() == null) {
            return false;
        }
        Set<String> enBaseDeDatos = usuario.getRoles().stream()
                .map(r -> "ROLE_" + r.name())
                .collect(Collectors.toSet());
        return !enBaseDeDatos.equals(rolesDe(auth.getAuthorities()));
    }

    private static Set<String> rolesDe(Collection<? extends GrantedAuthority> autoridades) {
        return autoridades.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    /**
     * Reconstruye la autenticación con los roles actuales de la base de datos y la guarda en la sesión.
     * Devuelve null si no fue posible o si los roles no cambiaron realmente.
     */
    private Authentication reconstruirAutenticacion(Authentication actual, String email, HttpSession session) {
        if (email == null) {
            return null;
        }
        try {
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
            if (rolesDe(userDetails.getAuthorities()).equals(rolesDe(actual.getAuthorities()))) {
                return null;
            }
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    userDetails, actual.getCredentials(), userDetails.getAuthorities());
            token.setDetails(actual.getDetails());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(token);
            SecurityContextHolder.setContext(context);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return token;
        } catch (Exception e) {
            return null;
        }
    }
}
