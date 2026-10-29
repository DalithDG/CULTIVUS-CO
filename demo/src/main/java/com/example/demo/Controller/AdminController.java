package com.example.demo.Controller;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;



import com.example.demo.Model.Usuario;
import com.example.demo.security.SecurityContextRefreshService;
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

    @Autowired
    private SecurityContextRefreshService securityContextRefreshService;

    @Autowired(required = false)
    private com.example.demo.services.TransportistaService transportistaService;

    @Autowired(required = false)
    private com.example.demo.services.UbicacionService ubicacionService;

    @Autowired(required = false)
    private com.example.demo.services.UsuarioService usuarioService;

    @Autowired(required = false)
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // Roles disponibles — ya no hay tabla de roles
    private static final List<String> ROLES = Arrays.asList(
            "COMPRADOR", "VENDEDOR", "TRANSPORTISTA", "ADMIN");

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
        model.addAttribute("transportistasPendientesCount", adminService.obtenerCantidadTransportistasPendientes());
        model.addAttribute("transportistas", adminService.obtenerTodosLosTransportistas());

        // Default empty pages to avoid NPE in Thymeleaf
        model.addAttribute("usuarios", org.springframework.data.domain.Page.empty());
        model.addAttribute("productos", org.springframework.data.domain.Page.empty());
        model.addAttribute("resenas", org.springframework.data.domain.Page.empty());
        model.addAttribute("pedidos", org.springframework.data.domain.Page.empty());
        model.addAttribute("vendedores", org.springframework.data.domain.Page.empty());
        model.addAttribute("usuariosActivos", org.springframework.data.domain.Page.empty());
        model.addAttribute("notificacionesPaginadas", org.springframework.data.domain.Page.empty());
        model.addAttribute("mensajesPaginados", org.springframework.data.domain.Page.empty());
        model.addAttribute("mensajesRecibidosPaginados", org.springframework.data.domain.Page.empty());
        model.addAttribute("catalogoPendiente", org.springframework.data.domain.Page.empty());

        // Paginación dinámica según la pestaña activa
        if ("usuarios".equals(activePage)) {
            model.addAttribute("usuarios", adminService.obtenerUsuariosPaginados(page, 50, search, filterParam));
        } else if ("catalogo".equals(activePage)) {
            model.addAttribute("productos", adminService.obtenerProductosPaginados(page, 50, search, filterParam));
        } else if ("resenas".equals(activePage) || "actividad".equals(activePage)) {
            model.addAttribute("resenas", adminService.obtenerResenasPaginadas(page, 50));
        } else if ("pedidos".equals(activePage) || "ordenes".equals(activePage)) {
            model.addAttribute("pedidos", adminService.obtenerPedidosPaginados(page, 50));
            model.addAttribute("estadosPedido", Arrays.asList(
                    Pedido.PENDIENTE, "PENDIENTE_PAGO", Pedido.CONFIRMADO, Pedido.EN_PROCESO,
                    Pedido.LISTO_PARA_RECOGER, Pedido.ENVIADO, Pedido.ENTREGADO,
                    Pedido.CANCELADO, Pedido.EN_REVISION));
        } else if ("verificacion-tiendas".equals(activePage)) {
            boolean verificado = "VERIFICADO".equals(filterParam);
            model.addAttribute("vendedores", adminService.obtenerVendedoresPorEstadoVerificacionPaginado(verificado, page, 50));
        } else if ("sesiones".equals(activePage)) {
            model.addAttribute("usuariosActivos", adminService.obtenerUsuariosActivosPaginado(page, 50));
        } else if ("notificaciones".equals(activePage)) {
            model.addAttribute("notificacionesPaginadas", adminService.obtenerNotificacionesPaginadas(page, 50));
        } else if ("mensajes".equals(activePage)) {
            org.springframework.data.domain.Page<?> msgs = adminService.obtenerMensajesPaginados(page, 50);
            model.addAttribute("mensajesPaginados", msgs);
            model.addAttribute("mensajesRecibidosPaginados", msgs);
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

        return "admin/dashboard";
    }

    // ==================== ANALÍTICAS POWER BI ====================

    @GetMapping("/perfil")
    public String mostrarPerfil(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }
        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        if (admin != null) {
            admin = adminService.obtenerUsuarioPorId(admin.getId()).orElse(admin);
            session.setAttribute("usuarioLogueado", admin);
        }
        model.addAttribute("admin", admin);
        model.addAttribute("usuario", admin);
        model.addAttribute("departamentos", ubicacionService != null ? ubicacionService.obtenerTodos() : java.util.List.of());
        model.addAttribute("estadisticas", adminService.obtenerEstadisticas());
        model.addAttribute("notificacionesCount", adminService.obtenerTotalNotificaciones());
        model.addAttribute("mensajesRecibidosCount", adminService.obtenerTotalMensajes());
        model.addAttribute("vendedoresPendientesCount", adminService.obtenerCantidadVendedoresPendientes());
        model.addAttribute("catalogoPendienteCount", catalogoService.obtenerCantidadCatalogoPendiente());
        model.addAttribute("transportistasPendientesCount", adminService.obtenerCantidadTransportistasPendientes());
        model.addAttribute("activePage", "perfil");
        return "admin/perfil-admin";
    }

    @PostMapping("/perfil/actualizar")
    public String actualizarPerfilAdmin(
            @RequestParam("nombre") String nombre,
            @RequestParam("email") String email,
            @RequestParam(value = "departamento", required = false, defaultValue = "") String nombreDepartamento,
            @RequestParam(value = "ciudad", required = false, defaultValue = "") String nombreCiudad,
            @RequestParam(value = "contrasena", required = false) String contrasena,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }
        try {
            Usuario adminSesion = (Usuario) session.getAttribute("usuarioLogueado");
            Usuario admin = adminService.obtenerUsuarioPorId(adminSesion.getId()).orElse(null);
            if (admin == null) {
                redirectAttributes.addFlashAttribute("error", "Usuario administrador no encontrado");
                return "redirect:/usuario/login";
            }
            if (nombre == null || nombre.trim().isEmpty() || email == null || email.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "El nombre y el correo electrónico son obligatorios");
                return "redirect:/admin/perfil";
            }
            String emailLimpio = email.trim().toLowerCase();
            if (usuarioService != null) {
                Usuario existente = usuarioService.findByEmail(emailLimpio);
                if (existente != null && !existente.getId().equals(admin.getId())) {
                    redirectAttributes.addFlashAttribute("error", "Ese correo ya está en uso por otra cuenta");
                    return "redirect:/admin/perfil";
                }
            }
            admin.setNombre(nombre.trim());
            admin.setEmail(emailLimpio);
            if (contrasena != null && !contrasena.trim().isEmpty()) {
                String errorPolitica = com.example.demo.services.PasswordPolicy.validar(contrasena);
                if (errorPolitica != null) {
                    redirectAttributes.addFlashAttribute("error", errorPolitica);
                    return "redirect:/admin/perfil";
                }
                if (passwordEncoder != null) {
                    admin.setContrasena(passwordEncoder.encode(contrasena));
                }
            }
            if (!nombreCiudad.trim().isEmpty() || !nombreDepartamento.trim().isEmpty()) {
                admin.setUbicacion(new com.example.demo.Model.embebidos.UbicacionUsuario(
                        nombreCiudad.trim(), nombreDepartamento.trim()));
            }
            if (usuarioService != null) {
                usuarioService.actualizarUsuario(admin);
            }
            session.setAttribute("usuarioLogueado", admin);
            redirectAttributes.addFlashAttribute("mensaje", "Perfil de administrador actualizado correctamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar perfil: " + e.getMessage());
        }
        return "redirect:/admin/perfil";
    }

    @GetMapping("/powerbi")
    public String mostrarPowerBi(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "powerbi", 0, null, null);
        model.addAttribute("activePage", "powerbi");

        return "admin/powerbi";
    }

    // ==================== GESTIÓN DE TRANSPORTISTAS ====================

    @GetMapping("/transportistas")
    public String mostrarTransportistas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String search,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        cargarDatosDashboard(model, admin, "transportistas", page, search, null);
        model.addAttribute("activePage", "transportistas");

        return "admin/transportistas";
    }

    @PostMapping("/transportistas/verificar/{usuarioId}")
    public String verificarTransportista(
            @PathVariable String usuarioId,
            @RequestParam(value = "verificado", defaultValue = "true") boolean verificado,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.verificarTransportista(usuarioId, verificado);
            redirectAttributes.addFlashAttribute("mensaje",
                    verificado ? "Transportista verificado correctamente. Ya puede aceptar envíos." : "Se revocó la verificación del transportista.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al verificar transportista: " + e.getMessage());
        }

        return "redirect:/admin/transportistas";
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

        return "admin/usuarios";
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
            // Si el administrador cambia su propio rol, refrescar su Authentication
            com.example.demo.Model.Usuario objetivo = adminService.obtenerUsuarioPorId(id).orElse(null);
            com.example.demo.Model.Usuario adminLogueado = (com.example.demo.Model.Usuario) session.getAttribute("usuarioLogueado");
            if (objetivo != null && adminLogueado != null && id.equals(adminLogueado.getId())) {
                securityContextRefreshService.refrescarAutenticacion(objetivo.getEmail());
                session.setAttribute("usuarioLogueado", objetivo);
            }
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

        return "admin/productos";
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

        return "admin/resenas";
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

        return "admin/ofertas";
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

        return "admin/pedidos";
    }

    @PostMapping("/pedidos/{id}/aceptar")
    public String aceptarPedidoAdmin(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.aceptarPedidoAdmin(id);
            redirectAttributes.addFlashAttribute("mensaje", "Pedido aceptado y pasado a CONFIRMADO");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al aceptar pedido: " + e.getMessage());
        }

        return "redirect:/admin/pedidos";
    }

    @PostMapping("/pedidos/{id}/actualizar-estado")
    public String actualizarEstadoPedidoAdmin(@PathVariable String id,
            @RequestParam("estado") String estado,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.actualizarEstadoPedidoAdmin(id, estado);
            redirectAttributes.addFlashAttribute("mensaje", "Estado del pedido actualizado correctamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar estado: " + e.getMessage());
        }

        return "redirect:/admin/pedidos";
    }

    /**
     * 5.3: Acción 'Reembolso realizado' para pedidos en EN_REVISION o CANCELADOS con reembolso pendiente.
     */
    @PostMapping("/pedidos/{id}/reembolso-realizado")
    public String marcarReembolsoRealizado(@PathVariable String id,
            @RequestParam(value = "motivo", required = false) String motivo,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            adminService.marcarReembolsoRealizadoAdmin(id, motivo);
            redirectAttributes.addFlashAttribute("mensaje", "Reembolso registrado exitosamente y pedido finalizado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al procesar reembolso: " + e.getMessage());
        }

        return "redirect:/admin/pedidos";
    }

    // ==================== GESTIÓN DE ENVÍOS (4.4, 4.8) ====================

    @PostMapping("/envios/{id}/reasignar")
    public String reasignarEnvio(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        Usuario admin = (Usuario) session.getAttribute("usuarioLogueado");
        try {
            if (transportistaService != null) {
                transportistaService.reasignarEnvioSinTransportista(id, admin.getId());
                redirectAttributes.addFlashAttribute("mensaje", "Envío reasignado y devuelto a la bolsa de cargas exitosamente.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al reasignar envío: " + e.getMessage());
        }

        return "redirect:/admin/pedidos";
    }

    @PostMapping("/envios/reconciliar")
    public String reconciliarEnvios(
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!verificarAdmin(session, redirectAttributes)) {
            return "redirect:/usuario/login";
        }

        try {
            if (transportistaService != null) {
                int reconciliados = transportistaService.reconciliarEnviosFaltantes();
                redirectAttributes.addFlashAttribute("mensaje", "Reconciliación completada: " + reconciliados + " envíos creados.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error durante la reconciliación: " + e.getMessage());
        }

        return "redirect:/admin/pedidos";
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

        return "admin/verificacion-tiendas";
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

        return "admin/sesiones";
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

        return "admin/notificaciones";
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

        return "admin/mensajes";
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

        return "admin/configuracion";
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

        return "admin/moderacion";
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