package com.example.demo.Controller;

import com.example.demo.Model.Pedido;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.services.TransportistaService;
import com.example.demo.services.UbicacionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/transportista")
public class TransportistaController {

    @Autowired
    private TransportistaService transportistaService;

    @Autowired
    private UbicacionService ubicacionService;

    /**
     * Pantalla de Login dedicada para transportistas.
     */
    @GetMapping("/login")
    public String mostrarLogin(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.hasRole(Role.TRANSPORTISTA)) {
            return "redirect:/transportista/inicio";
        }
        return "transportista/login-transportista";
    }

    /**
     * Formulario de Registro para transportistas (usuarios nuevos o existentes).
     */
    @GetMapping("/registro")
    public String mostrarRegistro(HttpSession session, Model model) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.hasRole(Role.TRANSPORTISTA)) {
            return "redirect:/transportista/inicio";
        }

        model.addAttribute("usuario", usuario != null ? usuario : new Usuario());
        model.addAttribute("departamentos", ubicacionService.obtenerTodos());
        return "transportista/registro-transportista";
    }

    /**
     * Procesa el formulario de registro de transportista.
     */
    @PostMapping("/guardar")
    public String guardarTransportista(
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "contrasena", required = false) String contrasena,
            @RequestParam("tipoVehiculo") String tipoVehiculo,
            @RequestParam("placa") String placa,
            @RequestParam("capacidadCargaKg") Double capacidadCargaKg,
            @RequestParam("licenciaConduccion") String licenciaConduccion,
            @RequestParam("telefonoContacto") String telefonoContacto,
            @RequestParam(value = "ciudadBase", required = false) String ciudadBase,
            @RequestParam(value = "departamentoBase", required = false) String departamentoBase,
            @RequestParam("zonasCobertura") String zonasCobertura,
            @RequestParam(value = "refrigerado", defaultValue = "false") boolean refrigerado,
            @RequestParam(value = "empresa", required = false) String empresa,
            @RequestParam(value = "banco", required = false) String banco,
            @RequestParam(value = "cuentaBancaria", required = false) String cuentaBancaria,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            // Validaciones básicas de vehículo y contacto
            if (placa == null || placa.trim().length() < 5) {
                redirectAttributes.addFlashAttribute("error", "La placa del vehículo debe ser válida (ej. ABC-123)");
                return "redirect:/transportista/registro";
            }
            if (capacidadCargaKg == null || capacidadCargaKg <= 0) {
                redirectAttributes.addFlashAttribute("error", "Indica una capacidad de carga válida en Kilogramos");
                return "redirect:/transportista/registro";
            }
            if (telefonoContacto == null || telefonoContacto.trim().length() < 7) {
                redirectAttributes.addFlashAttribute("error", "El teléfono de contacto es obligatorio y debe tener al menos 7 dígitos");
                return "redirect:/transportista/registro";
            }

            Usuario usuarioSesion = (Usuario) session.getAttribute("usuarioLogueado");

            if (usuarioSesion != null) {
                // Usuario autenticado que solicita convertirse en transportista
                Usuario actualizado = transportistaService.crearPerfilTransportista(
                        usuarioSesion.getId(),
                        tipoVehiculo,
                        placa,
                        capacidadCargaKg,
                        licenciaConduccion,
                        telefonoContacto,
                        ciudadBase,
                        departamentoBase,
                        zonasCobertura,
                        refrigerado,
                        empresa,
                        banco,
                        cuentaBancaria
                );
                session.setAttribute("usuarioLogueado", actualizado);
                redirectAttributes.addFlashAttribute("mensaje", "¡Bienvenido a la red logística de Cultivus! Tu perfil de transportista está activo.");
                return "redirect:/transportista/inicio";
            } else {
                // Registro de nuevo usuario directamente como transportista
                if (nombre == null || nombre.trim().isEmpty() || email == null || email.trim().isEmpty() || contrasena == null || contrasena.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Nombre, correo y contraseña son obligatorios");
                    return "redirect:/transportista/registro";
                }

                Usuario nuevo = transportistaService.registrarNuevoTransportista(
                        nombre,
                        email,
                        contrasena,
                        tipoVehiculo,
                        placa,
                        capacidadCargaKg,
                        licenciaConduccion,
                        telefonoContacto,
                        ciudadBase,
                        departamentoBase,
                        zonasCobertura,
                        refrigerado,
                        empresa,
                        banco,
                        cuentaBancaria
                );

                redirectAttributes.addFlashAttribute("mensaje", "Registro exitoso. Inicia sesión con tus credenciales.");
                return "redirect:/transportista/login";
            }

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/transportista/registro";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error inesperado al registrar transportista: " + e.getMessage());
            return "redirect:/transportista/registro";
        }
    }

    /**
     * Dashboard / Vista MAIN del Transportista.
     */
    @GetMapping({"/inicio", "/dashboard", "/main"})
    public String mostrarDashboard(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Inicia sesión para ingresar al portal logístico");
            return "redirect:/transportista/login";
        }

        if (!usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes tener perfil de transportista para acceder");
            return "redirect:/transportista/registro";
        }

        PerfilTransportista perfil = usuario.getPerfilTransportista();
        List<Pedido> pedidosDisponibles = transportistaService.obtenerPedidosDisponiblesParaEnvio();

        model.addAttribute("usuario", usuario);
        model.addAttribute("perfil", perfil);
        model.addAttribute("pedidos", pedidosDisponibles);
        model.addAttribute("totalPedidosDisponibles", pedidosDisponibles.size());

        return "transportista/inicio-transportista";
    }

    /**
     * Alternar estado de disponibilidad (Disponible / En ruta).
     */
    @PostMapping("/disponibilidad")
    public String alternarDisponibilidad(
            @RequestParam("disponible") boolean disponible,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.hasRole(Role.TRANSPORTISTA)) {
            Usuario actualizado = transportistaService.actualizarDisponibilidad(usuario.getId(), disponible);
            session.setAttribute("usuarioLogueado", actualizado);
            redirectAttributes.addFlashAttribute("mensaje", disponible ? "Ahora estás DISPONIBLE para recibir asignación de envíos." : "Estado cambiado a EN RUTA / NO DISPONIBLE.");
        }
        return "redirect:/transportista/inicio";
    }
}
