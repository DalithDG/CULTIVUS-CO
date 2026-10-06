package com.example.demo.Controller;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Producto;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.Resena;
import com.example.demo.Model.Usuario;
import com.example.demo.services.AdminService;
import com.example.demo.services.AppConfigService;
import com.example.demo.services.CatalogoService;
import com.example.demo.services.OfertaService;
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

    @Autowired
    private CatalogoService catalogoService;

    @Autowired
    private OfertaService ofertaService;

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

    /**
     * Carga todos los datos necesarios para el dashboard
     */
    private void cargarDatosDashboard(Model model, Usuario admin, String activePage, int page, String search, String filterParam) {
        model.addAttribute("admin", admin);
        model.addAttribute("roles", ROLES);
        model.addAttribute("estadisticas", adminService.obtenerEstadisticas());
        model.addAttribute("categorias", adminService.obtenerTodasLasCategorias());
        model.addAttribute("configuraciones", configService.obtenerTodas());
        
        // Counts for dashboard badges
        model.addAttribute("notificacionesCount", adminService.obtenerTotalNotificaciones());
        model.addAttribute("mensajesRecibidosCount", adminService.obtenerTotalMensajes());
        model.addAttribute("vendedoresPendientesCount", adminService.obtenerCantidadVendedoresPendientes());
        model.addAttribute("catalogoPendienteCount", catalogoService.obtenerCantidadCatalogoPendiente());

        // Default empty pages to avoid NPE in Thymeleaf
        model.addAttribute("usuarios", org.springframework.data.domain.Page.empty());
        model.addAttribute("productos", org.springframework.data.domain.Page.empty());
        model.addAttribute("resenas", org.springframework.data.domain.Page.empty());
        model.addAttribute("pedidos", org.springframework.data.domain.Page.empty());
        model.addAttribute("vendedores", org.springframework.data.domain.Page.empty());
        model.addAttribute("usuariosActivos", org.springframework.data.domain.Page.empty());
        model.addAttribute("notificacionesPaginadas", org.springframework.data.domain.Page.empty());
        model.addAttribute("mensajesPaginados", org.springframework.data.domain.Page.empty());
        model.addAttribute("catalogoPendiente", org.springframework.data.domain.Page.empty());

        // Paginación dinámica según la pestaña activa
        if ("usuarios".equals(activePage)) {
            model.addAttribute("usuarios", adminService.obtenerUsuariosPaginados(page, 50, search, filterParam));
        } else if ("catalogo".equals(activePage)) {
            model.addAttribute("productos", adminService.obtenerProductosPaginados(page, 50, search, filterParam));
        } else if ("resenas".equals(activePage)) {
            model.addAttribute("resenas", adminService.obtenerResenasPaginadas(page, 50));
        } else if ("pedidos".equals(activePage)) {
            model.addAttribute("pedidos", adminService.obtenerPedidosPaginados(page, 50));
        } else if ("verificacion-tiendas".equals(activePage)) {
            boolean verificado = "VERIFICADO".equals(filterParam);
            model.addAttribute("vendedores", adminService.obtenerVendedoresPorEstadoVerificacionPaginado(verificado, page, 50));
        } else if ("sesiones".equals(activePage)) {
            model.addAttribute("usuariosActivos", adminService.obtenerUsuariosActivosPaginado(page, 50));
        } else if ("notificaciones".equals(activePage)) {
            model.addAttribute("notificacionesPaginadas", adminService.obtenerNotificacionesPaginadas(page, 50));
        } else if ("mensajes".equals(activePage)) {
            model.addAttribute("mensajesPaginados", adminService.obtenerMensajesPaginados(page, 50));
        } else if ("moderacion".equals(activePage)) {
            model.addAttribute("catalogoPendiente", catalogoService.listarPendientesPaginados(page, 50));
        }
        
        model.addAttribute("searchQuery", search);
        model.addAttribute("filterParam", filterParam);
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public String mostrarDashboard(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "dashboard", 0, null, null);
        model.addAttribute("activePage", "dashboard");

        return "admin/admin-dashboard";
    }

    // ==================== ANALÍTICAS POWER BI ====================

    @GetMapping("/powerbi")
    public String mostrarPowerBi(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "powerbi", 0, null, null);
        model.addAttribute("activePage", "powerbi");

        return "admin/admin-dashboard";
    }

    // ==================== GESTIÓN DE USUARIOS ====================

    @GetMapping("/usuarios")
    public String mostrarUsuarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String rol,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "usuarios", page, search, rol);
        model.addAttribute("activePage", "usuarios");
        model.addAttribute("rolFilter", rol);

        return "admin/admin-dashboard";
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
    public String mostrarProductos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoriaId,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "catalogo", page, search, categoriaId);
        model.addAttribute("activePage", "catalogo");
        model.addAttribute("categoriaFilter", categoriaId);

        return "admin/admin-dashboard";
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
    public String mostrarResenas(
            @RequestParam(defaultValue = "0") int page,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "resenas", page, null, null);
        model.addAttribute("activePage", "actividad");

        return "admin/admin-dashboard";
    }

    // ==================== GESTIÓN DE OFERTAS ====================

    @GetMapping("/ofertas")
    public String mostrarOfertas(
            @RequestParam(defaultValue = "0") int page,
            HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!verificarAdmin(session, redirectAttributes)) return "redirect:/usuario/login";

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "ofertas", page, null, null);
        org.springframework.data.domain.Page<OfertaVendedor> ofertas = ofertaService.obtenerOfertasPaginadas(page, 50);
        model.addAttribute("ofertas", ofertas);
        model.addAttribute("activePage", "ofertas");

        return "admin/admin-dashboard";
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

    // ==================== GESTIÓN DE PEDIDOS ====================

    @GetMapping("/pedidos")
    public String mostrarPedidos(
            @RequestParam(defaultValue = "0") int page,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "pedidos", page, null, null);
        model.addAttribute("activePage", "ordenes");

        return "admin/admin-dashboard";
    }

    // ==================== VERIFICACIÓN DE TIENDAS ====================

    @GetMapping("/verificacion-tiendas")
    public String mostrarVerificacionTiendas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "PENDIENTE") String estado,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "verificacion-tiendas", page, null, estado);
        model.addAttribute("activePage", "verificacion-tiendas");
        model.addAttribute("estadoFilter", estado);

        return "admin/admin-dashboard";
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
    public String mostrarSesiones(
            @RequestParam(defaultValue = "0") int page,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "sesiones", page, null, null);
        model.addAttribute("activePage", "sesiones");

        return "admin/admin-dashboard";
    }

    // ==================== NOTIFICACIONES ====================

    @GetMapping("/notificaciones")
    public String mostrarNotificaciones(
            @RequestParam(defaultValue = "0") int page,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "notificaciones", page, null, null);
        model.addAttribute("activePage", "notificaciones");

        return "admin/admin-dashboard";
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
    public String mostrarMensajes(
            @RequestParam(defaultValue = "0") int page,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "mensajes", page, null, null);
        model.addAttribute("activePage", "mensajes");

        return "admin/admin-dashboard";
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
    public String mostrarConfiguracion(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!verificarAdmin(session, redirectAttributes)) return "redirect:/usuario/login";

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "configuracion", 0, null, null);
        model.addAttribute("activePage", "configuracion");

        return "admin/admin-dashboard";
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

    // ==================== MODERACIÓN DE OFERTAS Y CATÁLOGO ====================

    @GetMapping("/moderacion")
    public String mostrarModeracion(
            @RequestParam(defaultValue = "0") int page,
            HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!verificarAdmin(session, redirectAttributes)) return "redirect:/usuario/login";

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "moderacion", page, null, null);
        model.addAttribute("activePage", "moderacion");

        return "admin/admin-dashboard";
    }


    @PostMapping("/catalogo/{id}/aprobar")
    public String aprobarProductoCatalogo(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            catalogoService.aprobarProducto(id);
            redirectAttributes.addFlashAttribute("mensaje", "Producto aprobado en el catálogo");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al aprobar producto: " + e.getMessage());
        }

        return "redirect:/admin/moderacion";
    }

    @PostMapping("/catalogo/{id}/rechazar")
    public String rechazarProductoCatalogo(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            catalogoService.rechazarProducto(id);
            redirectAttributes.addFlashAttribute("mensaje", "Producto rechazado del catálogo");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al rechazar producto: " + e.getMessage());
        }

        return "redirect:/admin/moderacion";
    }
}