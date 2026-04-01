package com.example.demo.Controller;

import com.example.demo.Model.Producto;
import com.example.demo.Model.Resena;
import com.example.demo.Model.Usuario;
import com.example.demo.services.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    // Roles disponibles — ya no hay tabla de roles
    private static final List<String> ROLES = Arrays.asList(
            "COMPRADOR", "VENDEDOR", "ADMIN"
    );

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

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public String mostrarDashboard(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        Map<String, Object> estadisticas = adminService.obtenerEstadisticas();

        model.addAttribute("admin", admin);
        model.addAttribute("estadisticas", estadisticas);

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
        List<Usuario> usuarios = adminService.obtenerTodosLosUsuarios();

        model.addAttribute("admin", admin);
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("roles", ROLES);

        return "admin-usuarios";
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
        List<Producto> productos = adminService.obtenerTodosLosProductos();

        model.addAttribute("admin", admin);
        model.addAttribute("productos", productos);

        return "admin-productos";
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
        List<Resena> resenas = adminService.obtenerTodasLasResenas();

        model.addAttribute("admin", admin);
        model.addAttribute("resenas", resenas);

        return "admin-resenas";
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
}