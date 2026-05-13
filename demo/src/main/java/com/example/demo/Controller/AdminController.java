package com.example.demo.Controller;

import com.example.demo.Model.Producto;
import com.example.demo.Model.Resena;
import com.example.demo.Model.Usuario;
import com.example.demo.services.AdminService;
import com.example.demo.services.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AppConfigService configService;

    // Roles disponibles — ya no hay tabla de roles
    private static final List<String> ROLES = Arrays.asList(
            "COMPRADOR", "VENDEDOR", "ADMIN");

    // Verificar que el usuario es admin
    private boolean verificarAdmin(HttpSession session,
            RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");

        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return false;
        }
        if (!adminService.esAdmin(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permisos de administrador");
            return false;
        }
        return true;
    }

    private void cargarDatosDashboard(Model model, Usuario admin) {
        model.addAttribute("admin", admin);
        model.addAttribute("estadisticas", adminService.obtenerEstadisticas());
        model.addAttribute("usuarios", adminService.obtenerTodosLosUsuarios());
        model.addAttribute("roles", ROLES);
        model.addAttribute("productos", adminService.obtenerTodosLosProductos());
        model.addAttribute("resenas", adminService.obtenerTodasLasResenas());
        model.addAttribute("categorias", adminService.obtenerTodasLasCategorias());
        model.addAttribute("pedidos", adminService.obtenerTodosLosPedidos());
        model.addAttribute("vendedoresPendientes", adminService.obtenerVendedoresPendientes());
        model.addAttribute("vendedoresVerificados", adminService.obtenerVendedoresVerificados());
        model.addAttribute("usuariosActivos", adminService.obtenerUsuariosActivos());
        model.addAttribute("notificaciones", adminService.obtenerTodasLasNotificaciones());
        model.addAttribute("mensajesRecibidos", adminService.obtenerTodosLosMensajes());
        model.addAttribute("configuraciones", configService.obtenerTodas());
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public String mostrarDashboard(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "dashboard");

        return "admin-dashboard";
    }

    // ==================== GESTIÓN DE USUARIOS ====================

    @GetMapping("/usuarios")
    public String mostrarUsuarios(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "usuarios");

        return "admin-dashboard";
    }

    @PostMapping("/usuarios/{id}/cambiar-rol")
    public String cambiarRolUsuario(@PathVariable String id,
            @RequestParam("nuevoRol") String nuevoRol,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.cambiarRolUsuario(id, nuevoRol);
            redirectAttributes.addFlashAttribute("mensaje", "Rol actualizado exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al cambiar rol: " + e.getMessage());
        }

        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/eliminar")
    public String eliminarUsuario(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.eliminarUsuario(id);
            redirectAttributes.addFlashAttribute("mensaje", "Usuario eliminado exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al eliminar usuario: " + e.getMessage());
        }

        return "redirect:/admin/usuarios";
    }

    // ==================== GESTIÓN DE PRODUCTOS ====================

    @GetMapping("/productos")
    public String mostrarProductos(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "catalogo");

        return "admin-dashboard";
    }

    @PostMapping("/productos/{id}/eliminar")
    public String eliminarProducto(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.eliminarProducto(id);
            redirectAttributes.addFlashAttribute("mensaje", "Producto eliminado exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al eliminar producto: " + e.getMessage());
        }

        return "redirect:/admin/productos";
    }

    // ==================== GESTIÓN DE RESEÑAS ====================

    @GetMapping("/resenas")
    public String mostrarResenas(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "actividad");

        return "admin-dashboard";
    }

    @PostMapping("/resenas/{id}/eliminar")
    public String eliminarResena(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.eliminarResena(id);
            redirectAttributes.addFlashAttribute("mensaje", "Reseña eliminada exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al eliminar reseña: " + e.getMessage());
        }

        return "redirect:/admin/resenas";
    }

    // ==================== VERIFICACIÓN DE TIENDAS ====================

    @GetMapping("/verificacion-tiendas")
    public String mostrarVerificacionTiendas(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "verificacion-tiendas");

        return "admin-dashboard";
    }

    @PostMapping("/tiendas/{id}/aprobar")
    public String aprobarVendedor(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.aprobarVendedor(id);
            redirectAttributes.addFlashAttribute("mensaje", "Tienda aprobada exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al aprobar tienda: " + e.getMessage());
        }

        return "redirect:/admin/verificacion-tiendas";
    }

    @PostMapping("/tiendas/{id}/rechazar")
    public String rechazarVendedor(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.rechazarVendedor(id);
            redirectAttributes.addFlashAttribute("mensaje", "Verificación revocada");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al rechazar tienda: " + e.getMessage());
        }

        return "redirect:/admin/verificacion-tiendas";
    }

    // ==================== SESIONES ====================

    @GetMapping("/sesiones")
    public String mostrarSesiones(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "sesiones");

        return "admin-dashboard";
    }

    // ==================== NOTIFICACIONES ====================

    @GetMapping("/notificaciones")
    public String mostrarNotificaciones(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "notificaciones");

        return "admin-dashboard";
    }

    @PostMapping("/notificaciones/enviar")
    public String enviarNotificacion(@RequestParam("titulo") String titulo,
            @RequestParam("mensaje") String mensaje,
            @RequestParam("tipo") String tipo,
            @RequestParam(value = "usuarioId", required = false) String usuarioId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            // Normalizar usuarioId: si está vacío, tratar como null (notificación global)
            String uid = (usuarioId != null && !usuarioId.trim().isEmpty()) ? usuarioId : null;
            adminService.enviarNotificacion(titulo, mensaje, tipo, uid);
            redirectAttributes.addFlashAttribute("mensaje", "Notificación enviada");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }

        return "redirect:/admin/notificaciones";
    }

    @PostMapping("/notificaciones/{id}/eliminar")
    public String eliminarNotificacion(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        adminService.eliminarNotificacion(id);
        redirectAttributes.addFlashAttribute("mensaje", "Notificación eliminada");
        return "redirect:/admin/notificaciones";
    }

    // ==================== MENSAJES ====================

    @GetMapping("/mensajes")
    public String mostrarMensajes(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "mensajes");

        return "admin-dashboard";
    }

    @PostMapping("/mensajes/{id}/eliminar")
    public String eliminarMensaje(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        adminService.eliminarMensaje(id);
        redirectAttributes.addFlashAttribute("mensaje", "Mensaje eliminado");
        return "redirect:/admin/mensajes";
    }

    // ==================== CONFIGURACIÓN ====================

    @GetMapping("/configuracion")
    public String mostrarConfiguracion(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin);
        model.addAttribute("activePage", "configuracion");

        return "admin-dashboard";
    }

    @PostMapping("/configuracion/actualizar")
    public String actualizarConfig(@RequestParam("id") String id,
            @RequestParam("valor") String valor,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            configService.actualizarValor(id, valor);
            redirectAttributes.addFlashAttribute("mensaje", "Parámetro actualizado correctamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar: " + e.getMessage());
        }

        return "redirect:/admin/configuracion";
    }
}