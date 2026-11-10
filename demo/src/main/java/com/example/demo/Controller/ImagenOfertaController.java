package com.example.demo.Controller;

import com.example.demo.Model.Usuario;
import com.example.demo.services.CloudinaryService;
import com.example.demo.services.ImagenValidador;
import com.example.demo.services.UsuarioService;
import com.example.demo.services.VendedorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Subida de la foto de una oferta a Cloudinary.
 *
 * <p>Solo VENDEDOR (regla {@code /vendedor/**} de SecurityConfig + comprobación de sesión) y con CSRF
 * activo: el cliente envía el token en la cabecera {@code X-CSRF-TOKEN}.</p>
 */
@RestController
@RequestMapping("/vendedor/imagenes")
public class ImagenOfertaController {

    private static final Logger logger = LoggerFactory.getLogger(ImagenOfertaController.class);

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private VendedorService vendedorService;

    @Autowired(required = false)
    private UsuarioService usuarioService;

    @PostMapping(value = "/subir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> subir(@RequestParam("archivo") MultipartFile archivo,
            HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Usuario usuario = session != null ? (Usuario) session.getAttribute("usuarioLogueado") : null;
        if (usuario == null && usuarioService != null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
                usuario = usuarioService.findByEmail(auth.getName());
                if (usuario != null && session != null) {
                    session.setAttribute("usuarioLogueado", usuario);
                }
            }
        }
        if (usuario == null) {
            return error(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión primero.");
        }
        if (!vendedorService.esVendedor(usuario.getId())) {
            return error(HttpStatus.FORBIDDEN, "Debes registrarte como vendedor primero.");
        }
        if (!cloudinaryService.estaConfigurado()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE,
                    "La subida de imágenes no está disponible en este momento. Reinicia el servidor si acabas de configurar las credenciales en .env.");
        }
        if (archivo.getSize() > ImagenValidador.TAMANO_MAXIMO_BYTES) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera el máximo de 3 MB.");
        }

        try {
            byte[] bytes = archivo.getBytes();
            ImagenValidador.validar(bytes); // tipo REAL por firma de bytes + tamaño
            String url = cloudinaryService.subirImagenOferta(bytes);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (CloudinaryService.CloudinaryNoConfiguradoException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (IOException e) {
            logger.warn("No se pudo leer el archivo subido: {}", e.getMessage());
            return error(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen. Elige otra.");
        } catch (RuntimeException e) {
            logger.error("Error al subir imagen de oferta a Cloudinary", e);
            return error(HttpStatus.BAD_GATEWAY, "No pudimos guardar la imagen en la nube. Usa 'Reintentar subida'.");
        }
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("error", mensaje));
    }
}
