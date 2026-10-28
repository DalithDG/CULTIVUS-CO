package com.example.demo.Controller;

import com.example.demo.services.PasswordPolicy;
import com.example.demo.services.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rutas públicas de recuperación de contraseña.
 *
 * Mantiene el patrón PRG (POST → redirect → GET) para evitar reenvíos y para
 * que el token no quede en la barra de direcciones después de usarlo.
 */
@Controller
@RequestMapping("/usuario")
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

    private static final String MENSAJE_GENERICO =
            "Si el correo está registrado en Cultivus, recibirás un enlace para restablecer "
                    + "tu contraseña. Revisa también la carpeta de spam.";

    private static final String MENSAJE_ENLACE_INVALIDO =
            "El enlace no es válido, expiró o ya fue utilizado. Solicita uno nuevo.";

    private static final String MENSAJE_ERROR_VALIDACION =
            "No pudimos validar el enlace. Intenta de nuevo o solicita uno nuevo.";

    /**
     * Validación de formato en servidor. El límite de 254 caracteres y el
     * patrón evitan entradas absurdas; no se usa para revelar si existe.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@"
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/recuperar")
    public String mostrarSolicitud(HttpServletResponse response) {
        aplicarCabecerasPrivadas(response);
        return "auth/recuperar-password";
    }

    @PostMapping("/recuperar")
    public String solicitarRecuperacion(
            @RequestParam(value = "email", required = false) String email,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {

        aplicarCabecerasPrivadas(response);

        String correo = normalizarYValidarEmail(email);
        if (correo == null) {
            model.addAttribute("error", "Ingresa un correo electrónico válido.");
            model.addAttribute("email", email == null ? "" : email.trim());
            return "auth/recuperar-password";
        }

        try {
            passwordResetService.solicitarRecuperacion(correo, ipDelCliente(request));
            String enlaceDirecto = passwordResetService.consumirUltimoEnlace(correo);
            if (enlaceDirecto != null && !enlaceDirecto.isBlank()) {
                redirectAttributes.addFlashAttribute("enlaceRecuperacionDirecto", enlaceDirecto);
            }
        } catch (Exception e) {
            // Nunca se muestra el detalle ni se registra el correo/token.
            log.error("Error al procesar la solicitud de recuperación ({})",
                    e.getClass().getSimpleName());
        }

        // Respuesta idéntica exista o no la cuenta y aunque el límite de
        // frecuencia haya descartado el envío.
        redirectAttributes.addFlashAttribute("mensaje", MENSAJE_GENERICO);
        return "redirect:/usuario/recuperar";
    }

    @GetMapping("/restablecer")
    public String mostrarFormulario(
            @RequestParam(value = "token", required = false) String token,
            HttpServletResponse response,
            Model model) {

        aplicarCabecerasPrivadas(response);

        boolean valido = false;
        try {
            valido = passwordResetService.tokenValido(token);
        } catch (Exception e) {
            // Nunca se muestra el detalle de la base de datos.
            log.error("Error al validar el token de recuperación ({})",
                    e.getClass().getSimpleName());
            model.addAttribute("error", MENSAJE_ERROR_VALIDACION);
        }

        model.addAttribute("tokenValido", valido);

        if (valido) {
            model.addAttribute("token", token);
        } else if (!model.containsAttribute("error")) {
            model.addAttribute("error", MENSAJE_ENLACE_INVALIDO);
        }

        return "auth/restablecer-password";
    }

    @PostMapping("/restablecer")
    public String restablecerContrasena(
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(value = "nuevaContrasena", required = false) String nuevaContrasena,
            @RequestParam(value = "confirmarContrasena", required = false) String confirmarContrasena,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {

        aplicarCabecerasPrivadas(response);

        boolean tokenVigente;
        try {
            tokenVigente = passwordResetService.tokenValido(token);
        } catch (Exception e) {
            log.error("Error al validar el token antes de restablecer ({})",
                    e.getClass().getSimpleName());
            return mostrarEnlaceInvalido(model, MENSAJE_ERROR_VALIDACION);
        }

        if (!tokenVigente) {
            return mostrarEnlaceInvalido(model, MENSAJE_ENLACE_INVALIDO);
        }

        // Mantener el formulario utilizable mientras el token siga vigente.
        model.addAttribute("tokenValido", true);
        model.addAttribute("token", token);

        if (nuevaContrasena == null || nuevaContrasena.isEmpty()
                || confirmarContrasena == null || confirmarContrasena.isEmpty()) {
            model.addAttribute("error", "Debes escribir y confirmar la nueva contraseña.");
            return "auth/restablecer-password";
        }

        if (!nuevaContrasena.equals(confirmarContrasena)) {
            model.addAttribute("error", "Las contraseñas no coinciden.");
            return "auth/restablecer-password";
        }

        String errorPolitica = PasswordPolicy.validar(nuevaContrasena);
        if (errorPolitica != null) {
            model.addAttribute("error", errorPolitica);
            return "auth/restablecer-password";
        }

        try {
            passwordResetService.restablecerContrasena(
                    token, nuevaContrasena, ipDelCliente(request));

            redirectAttributes.addFlashAttribute("mensaje",
                    "Tu contraseña se actualizó correctamente. Ya puedes iniciar sesión.");
            return "redirect:/usuario/login";

        } catch (PasswordResetService.TokenInvalidoException e) {
            return mostrarEnlaceInvalido(model, MENSAJE_ENLACE_INVALIDO);

        } catch (PasswordResetService.SolicitudLimitadaException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/restablecer-password";

        } catch (PasswordResetService.PasswordPolicyException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/restablecer-password";

        } catch (Exception e) {
            log.error("Error al restablecer la contraseña ({})", e.getClass().getSimpleName());
            return mostrarEnlaceInvalido(model,
                    "No pudimos actualizar la contraseña. Solicita un nuevo enlace.");
        }
    }

    private String mostrarEnlaceInvalido(Model model, String mensaje) {
        model.addAttribute("tokenValido", false);
        model.addAttribute("error", mensaje);
        return "auth/restablecer-password";
    }

    private static String normalizarYValidarEmail(String email) {
        if (email == null) {
            return null;
        }
        String limpio = email.trim().toLowerCase(Locale.ROOT);
        if (limpio.length() < 5 || limpio.length() > 254
                || !EMAIL_PATTERN.matcher(limpio).matches()) {
            return null;
        }
        return limpio;
    }

    private static String ipDelCliente(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "desconocida" : ip;
    }

    private static void aplicarCabecerasPrivadas(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }
}
