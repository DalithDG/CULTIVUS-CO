package com.example.demo.Controller;

import com.example.demo.Model.Producto;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.UbicacionUsuario;
import com.example.demo.services.ProductoService;
import com.example.demo.services.UbicacionService;
import com.example.demo.services.UsuarioService;
import com.example.demo.services.VendedorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/usuario")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UbicacionService ubicacionService;
    private final VendedorService vendedorService;
    private final ProductoService productoService;
    // Temporalmente desactivado
    // private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioService usuarioService,
            UbicacionService ubicacionService,
            VendedorService vendedorService,
            ProductoService productoService
            // PasswordEncoder passwordEncoder - Temporalmente desactivado
            ) {
        this.usuarioService = usuarioService;
        this.ubicacionService = ubicacionService;
        this.vendedorService = vendedorService;
        this.productoService = productoService;
        // this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/registro")
    public String mostrarFormularioRegistro(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("departamentos", ubicacionService.obtenerTodos());
        return "registro";
    }

    @PostMapping("/guardar")
    public String guardarUsuario(@RequestParam("nombre") String nombre,
            @RequestParam("email") String email,
            @RequestParam("contrasena") String contrasena,
            @RequestParam("departamento") String nombreDepartamento,
            @RequestParam("ciudad") String nombreCiudad,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            // Validaciones de campos vacíos
            if (nombre == null || nombre.trim().isEmpty()) {
                model.addAttribute("error", "El nombre es requerido");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (email == null || email.trim().isEmpty()) {
                model.addAttribute("error", "El correo electrónico es requerido");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (contrasena == null || contrasena.trim().isEmpty()) {
                model.addAttribute("error", "La contraseña es requerida");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (nombreDepartamento == null || nombreDepartamento.trim().isEmpty()) {
                model.addAttribute("error", "El departamento es requerido");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (nombreCiudad == null || nombreCiudad.trim().isEmpty()) {
                model.addAttribute("error", "La ciudad es requerida");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }

            String nombreLimpio = nombre.trim();

            // Validaciones de nombre
            if (nombreLimpio.length() < 3) {
                model.addAttribute("error", "El nombre debe tener al menos 3 caracteres");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (nombreLimpio.length() > 50) {
                model.addAttribute("error", "El nombre no puede exceder 50 caracteres");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (!nombreLimpio.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$")) {
                model.addAttribute("error", "El nombre solo puede contener letras y espacios");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }

            String emailLimpio = email.trim().toLowerCase();

            // Validaciones de email
            String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
            if (!emailLimpio.matches(emailRegex)) {
                model.addAttribute("error", "El formato del correo electrónico no es válido");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (usuarioService.existeEmail(emailLimpio)) {
                model.addAttribute("error", "Este correo ya está registrado");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }

            // Validaciones de contraseña
            if (contrasena.length() < 8) {
                model.addAttribute("error", "La contraseña debe tener al menos 8 caracteres");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (!contrasena.matches(".*[A-Z].*")) {
                model.addAttribute("error", "La contraseña debe contener al menos una mayúscula");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (!contrasena.matches(".*[a-z].*")) {
                model.addAttribute("error", "La contraseña debe contener al menos una minúscula");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (!contrasena.matches(".*[0-9].*")) {
                model.addAttribute("error", "La contraseña debe contener al menos un número");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }
            if (!contrasena.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
                model.addAttribute("error", "La contraseña debe contener al menos un carácter especial");
                preservarDatosFormulario(model, nombre, email, nombreDepartamento, nombreCiudad);
                return "registro";
            }

            // ── Crear ubicación embebida ──────────────────────────────
            UbicacionUsuario ubicacion = new UbicacionUsuario(
                    nombreCiudad.trim(),
                    nombreDepartamento.trim()
            );

            // ── Crear usuario ─────────────────────────────────────────
            Usuario usuario = new Usuario();
            usuario.setNombre(nombreLimpio);
            usuario.setEmail(emailLimpio);
            usuario.setContrasena(contrasena); // Temporal: sin encriptar
            usuario.setRol("COMPRADOR");
            usuario.setUbicacion(ubicacion);

            usuarioService.save(usuario);

            redirectAttributes.addFlashAttribute("mensaje", "¡Registro exitoso! Inicie sesión.");
            return "redirect:/usuario/login";

        } catch (Exception e) {
            model.addAttribute("error", "Error al registrar: " + e.getMessage());
            return "registro";
        }
    }

    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }

    @GetMapping("/inicio")
    public String mostrarInicio(HttpSession session, Model model,
                                RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }

        // Recargar usuario actualizado
        Usuario usuarioActualizado = usuarioService.obtenerUsuarioPorId(usuario.getId());
        if (usuarioActualizado != null) {
            usuario = usuarioActualizado;
            session.setAttribute("usuarioLogueado", usuario);
        }

        // Cargar productos disponibles más recientes
        List<Producto> productos = productoService.listarDisponibles()
                .stream()
                .limit(8)
                .collect(Collectors.toList());

        model.addAttribute("usuario", usuario);
        model.addAttribute("productos", productos);

        // Redirigir según rol
        if ("VENDEDOR".equalsIgnoreCase(usuario.getRol())) {
            return "inicio-vendedor";
        }
        return "inicio-comprador";
    }

    @GetMapping("/perfil")
    public String mostrarPerfil(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }

        Usuario usuarioActualizado = usuarioService.obtenerUsuarioPorId(usuario.getId());
        if (usuarioActualizado != null) {
            usuario = usuarioActualizado;
            session.setAttribute("usuarioLogueado", usuario);
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("departamentos", ubicacionService.obtenerTodos());
        return "perfil";
    }

    @PostMapping("/perfil/actualizar")
    public String actualizarPerfil(@RequestParam("nombre") String nombre,
            @RequestParam("email") String email,
            @RequestParam("departamento") String nombreDepartamento,
            @RequestParam("ciudad") String nombreCiudad,
            @RequestParam(value = "contrasena", required = false) String contrasena,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
                return "redirect:/usuario/login";
            }

            if (nombre == null || nombre.trim().isEmpty() ||
                    email == null || email.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Nombre y email son requeridos");
                return "redirect:/usuario/perfil";
            }

            String emailLimpio = email.trim().toLowerCase();

            // Verificar si el email ya existe en otro usuario
            Usuario usuarioConEmail = usuarioService.findByEmail(emailLimpio);
            if (usuarioConEmail != null &&
                    !usuarioConEmail.getId().equals(usuario.getId())) {
                redirectAttributes.addFlashAttribute("error",
                        "Este correo ya está registrado por otro usuario");
                return "redirect:/usuario/perfil";
            }

            Usuario usuarioActualizado = usuarioService.obtenerUsuarioPorId(usuario.getId());
            if (usuarioActualizado == null) {
                redirectAttributes.addFlashAttribute("error", "Usuario no encontrado");
                return "redirect:/usuario/login";
            }

            // Actualizar datos básicos
            usuarioActualizado.setNombre(nombre.trim());
            usuarioActualizado.setEmail(emailLimpio);

            // Actualizar contraseña solo si se proporcionó
            if (contrasena != null && !contrasena.trim().isEmpty()) {
                usuarioActualizado.setContrasena(contrasena); // Temporal: sin encriptar
            }

            // Actualizar ubicación embebida
            UbicacionUsuario ubicacion = new UbicacionUsuario(
                    nombreCiudad.trim(),
                    nombreDepartamento.trim()
            );
            usuarioActualizado.setUbicacion(ubicacion);

            usuarioService.actualizarUsuario(usuarioActualizado);
            session.setAttribute("usuarioLogueado", usuarioActualizado);

            redirectAttributes.addFlashAttribute("mensaje", "Perfil actualizado exitosamente");
            return "redirect:/usuario/perfil";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al actualizar el perfil: " + e.getMessage());
            return "redirect:/usuario/perfil";
        }
    }

    @GetMapping("/logout")
    public String cerrarSesion(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    private void preservarDatosFormulario(Model model, String nombre, String email,
            String departamento, String ciudad) {
        if (nombre != null) model.addAttribute("nombre", nombre);
        if (email != null) model.addAttribute("email", email);
        if (departamento != null) model.addAttribute("departamento", departamento);
        if (ciudad != null) model.addAttribute("ciudad", ciudad);
    }
}