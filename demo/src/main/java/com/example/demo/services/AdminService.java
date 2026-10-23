package com.example.demo.services;

import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.Producto;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.Resena;
import com.example.demo.Model.Categoria;
import com.example.demo.Model.Notificacion;
import com.example.demo.Model.Mensaje;
import com.example.demo.Model.embebidos.PerfilAdmin;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ResenaRepository resenaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private MensajeRepository mensajeRepository;

    // ==================== GESTIÓN DE ADMINS ====================

    /**
     * Registra un nuevo usuario como administrador
     */
    public Usuario registrarAdmin(String nombre, String email, String contrasena,
            String nivelPermisos) {

        if (usuarioService.existeEmail(email)) {
            throw new IllegalArgumentException("Este correo electrónico ya está registrado");
        }

        // Crear perfil admin embebido
        PerfilAdmin perfilAdmin = new PerfilAdmin(nivelPermisos);

        // Crear usuario con rol ADMIN y perfil embebido
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setEmail(email.toLowerCase().trim());
        usuario.setContrasena(contrasena);
        usuario.setRol(Role.ADMIN);
        usuario.setPerfilAdmin(perfilAdmin);

        return usuarioRepository.save(usuario);
    }

    /**
     * Verifica si un usuario es administrador
     */
    public boolean esAdmin(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(u -> u.hasRole(Role.ADMIN))
                .orElse(false);
    }

    /**
     * Obtiene el perfil de admin de un usuario
     */
    public Optional<PerfilAdmin> obtenerPerfilPorUsuarioId(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getPerfilAdmin);
    }

    /**
     * Actualiza el perfil de administrador
     */
    public Usuario actualizarPerfil(String usuarioId, String nivelPermisos, boolean activo) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        PerfilAdmin perfil = usuario.getPerfilAdmin();
        if (perfil == null) {
            throw new IllegalArgumentException("Este usuario no tiene perfil de administrador");
        }

        if (nivelPermisos != null)
            perfil.setNivelPermisos(nivelPermisos);
        perfil.setActivo(activo);

        usuario.setPerfilAdmin(perfil);
        return usuarioRepository.save(usuario);
    }

    /**
     * Desactiva un administrador
     */
    public void desactivarAdmin(String usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        PerfilAdmin perfil = usuario.getPerfilAdmin();
        if (perfil != null) {
            perfil.setActivo(false);
            usuario.setPerfilAdmin(perfil);
            usuarioRepository.save(usuario);
        }
    }

    /**
     * Login específico para administradores
     */
    public Usuario loginAdmin(String email, String contrasena) {
        Usuario usuario = usuarioService.findByEmail(email.toLowerCase().trim());

        if (usuario == null) {
            throw new IllegalArgumentException("Credenciales incorrectas");
        }
        if (!usuario.getContrasena().equals(contrasena)) {
            throw new IllegalArgumentException("Credenciales incorrectas");
        }
        if (!usuario.hasRole(Role.ADMIN)) {
            throw new IllegalArgumentException("No tienes permisos de administrador");
        }

        PerfilAdmin perfil = usuario.getPerfilAdmin();
        if (perfil != null && !perfil.isActivo()) {
            throw new IllegalArgumentException("Tu cuenta de administrador está inactiva");
        }

        return usuario;
    }

    // ==================== GESTIÓN DE USUARIOS ====================

    /**
     * Obtener usuarios paginados y con filtro de búsqueda
     */
    public org.springframework.data.domain.Page<Usuario> obtenerUsuariosPaginados(int page, int size, String search, String role) {
        org.springframework.data.domain.Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        boolean hasSearch = (search != null && !search.trim().isEmpty());
        boolean hasRole = (role != null && !role.trim().isEmpty());
        
        if (hasSearch && hasRole) {
            return usuarioRepository.findBySearchAndRole(search, Role.valueOf(role), pageable);
        } else if (hasSearch) {
            return usuarioRepository.findByNombreContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable);
        } else if (hasRole) {
            return usuarioRepository.findByRolesContaining(Role.valueOf(role), pageable);
        }
        
        return usuarioRepository.findAll(pageable);
    }

    /**
     * Obtener usuario por ID
     */
    public Optional<Usuario> obtenerUsuarioPorId(String id) {
        return usuarioRepository.findById(id);
    }

    /**
     * Cambiar rol de usuario
     */
    public void cambiarRolUsuario(String usuarioId, String nuevoRol) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        usuario.getRoles().clear(); // Si el admin quiere forzar UN rol específico
        usuario.addRole(Role.valueOf(nuevoRol));
        usuarioRepository.save(usuario);
    }

    /**
     * Eliminar usuario
     */
    public void eliminarUsuario(String usuarioId) {
        usuarioService.eliminarUsuario(usuarioId);
    }

    // ==================== VERIFICACIÓN DE TIENDAS ====================

    /**
     * Obtiene todos los vendedores pendientes de verificación
     */
    public List<Usuario> obtenerVendedoresPendientes() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getPerfilVendedor() != null && !u.getPerfilVendedor().isVerificado())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Obtiene todos los vendedores ya verificados
     */
    public List<Usuario> obtenerVendedoresVerificados() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getPerfilVendedor() != null && u.getPerfilVendedor().isVerificado())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Aprueba la tienda de un vendedor
     */
    public void aprobarVendedor(String usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (usuario.getPerfilVendedor() == null)
            throw new IllegalArgumentException("El usuario no tiene perfil de vendedor");
        usuario.getPerfilVendedor().setVerificado(true);
        usuarioRepository.save(usuario);

        // Notificar al Vendedor
        notificacionService.enviar(
                usuarioId,
                "Tienda Verificada",
                "¡Felicidades! Tu tienda ha sido aprobada por el administrador. Ya puedes empezar a vender.",
                "SUCCESS"
        );
    }

    /**
     * Rechaza/revoca la verificación de un vendedor
     */
    public void rechazarVendedor(String usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (usuario.getPerfilVendedor() == null)
            throw new IllegalArgumentException("El usuario no tiene perfil de vendedor");
        usuario.getPerfilVendedor().setVerificado(false);
        usuarioRepository.save(usuario);

        // Notificar al Vendedor
        notificacionService.enviar(
                usuarioId,
                "Verificación de Tienda",
                "Tu solicitud de verificación de tienda ha sido rechazada o revocada. Contacta con soporte para más detalles.",
                "WARNING"
        );
    }

    // ==================== GESTIÓN DE PRODUCTOS ====================

    /**
     * Obtener productos paginados y con filtro de búsqueda
     */
    public org.springframework.data.domain.Page<Producto> obtenerProductosPaginados(int page, int size, String search, String categoriaId) {
        org.springframework.data.domain.Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        boolean hasSearch = (search != null && !search.trim().isEmpty());
        boolean hasCategoria = (categoriaId != null && !categoriaId.trim().isEmpty());
        
        if (hasSearch && hasCategoria) {
            return productoRepository.findBySearchAndCategoria(search, categoriaId, pageable);
        } else if (hasSearch) {
            return productoRepository.findByNombreContainingIgnoreCaseOrDescripcionContainingIgnoreCase(search, search, pageable);
        } else if (hasCategoria) {
            return productoRepository.findByCategoriaId(categoriaId, pageable);
        }
        
        return productoRepository.findAll(pageable);
    }

    /**
     * Eliminar producto
     */
    public void eliminarProducto(String productoId) {
        productoRepository.deleteById(productoId);
    }

    /**
     * Obtener productos por vendedor
     */
    public List<Producto> obtenerProductosPorVendedor(String vendedorId) {
        return productoRepository.findByVendedor_Id(vendedorId);
    }

    // ==================== GESTIÓN DE RESEÑAS ====================

    /**
     * Obtener todas las reseñas
     */
    public List<Resena> obtenerTodasLasResenas() {
        return resenaRepository.findAll(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "fecha"))).getContent();
    }

    public org.springframework.data.domain.Page<Resena> obtenerResenasPaginadas(int page, int size) {
        return resenaRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fecha")));
    }

    /**
     * Eliminar reseña
     */
    public void eliminarResena(String resenaId) {
        resenaRepository.deleteById(resenaId);
    }

    /**
     * Obtener últimas reseñas
     */
    public List<Resena> obtenerUltimasResenas() {
        return resenaRepository.findTop10ByOrderByFechaDesc();
    }

    // ==================== GESTIÓN DE CATEGORÍAS ====================
    public List<Categoria> obtenerTodasLasCategorias() {
        return categoriaRepository.findAll();
    }

    // ==================== GESTIÓN DE PEDIDOS ====================
    public List<Pedido> obtenerTodosLosPedidos() {
        return pedidoRepository.findAll(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    public org.springframework.data.domain.Page<Pedido> obtenerPedidosPaginados(int page, int size) {
        return pedidoRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public Optional<Pedido> obtenerPedidoPorId(String pedidoId) {
        if (pedidoId == null) return Optional.empty();
        return pedidoRepository.findById(pedidoId);
    }

    /**
     * Permite al administrador cambiar el estado de un pedido sin las restricciones
     * del vendedor/transportista. Valida que el estado sea uno de los permitidos.
     */
    public void actualizarEstadoPedidoAdmin(String pedidoId, String nuevoEstado) {
        if (pedidoId == null || nuevoEstado == null || nuevoEstado.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID de pedido y el nuevo estado son obligatorios");
        }
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        String estadoDeseado = nuevoEstado.trim().toUpperCase();
        List<String> estadosValidos = Arrays.asList(
                Pedido.PENDIENTE, Pedido.PENDIENTE, "PENDIENTE_PAGO", Pedido.CONFIRMADO,
                Pedido.EN_PROCESO, "PROCESANDO", Pedido.LISTO_PARA_RECOGER,
                Pedido.ENVIADO, Pedido.ENTREGADO, "COMPLETADO", Pedido.CANCELADO,
                Pedido.EN_REVISION);
        if (!estadosValidos.contains(estadoDeseado)) {
            throw new IllegalArgumentException("Estado no válido: " + nuevoEstado);
        }

        pedido.setEstado(estadoDeseado);
        pedido.setUpdatedAt(LocalDateTime.now());
        pedidoRepository.save(pedido);

        // Notificar al comprador del cambio de estado
        if (pedido.getComprador() != null && pedido.getComprador().getId() != null && notificacionService != null) {
            try {
                notificacionService.enviar(
                        pedido.getComprador().getId(),
                        "Estado de pedido actualizado",
                        "Tu pedido " + pedido.getNumeroFactura() + " ahora está en estado: " + estadoDeseado,
                        "INFO");
            } catch (Exception ignored) {
                // No bloquear el flujo por fallos de notificación
            }
        }
    }

    /**
     * Acepta un pedido disponible (PENDIENTE) pasándolo a CONFIRMADO.
     */
    public void aceptarPedidoAdmin(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));
        String estadoActual = pedido.getEstado() != null ? pedido.getEstado().toUpperCase() : Pedido.PENDIENTE;
        if (!Pedido.PENDIENTE.equals(estadoActual) && !"PENDIENTE_PAGO".equals(estadoActual)) {
            throw new IllegalStateException("Solo se pueden aceptar pedidos disponibles (PENDIENTE). Estado actual: " + estadoActual);
        }
        actualizarEstadoPedidoAdmin(pedidoId, Pedido.CONFIRMADO);
    }

    // ==================== ESTADÍSTICAS ====================

    /**
     * Obtener estadísticas del sistema
     */
    public Map<String, Object> obtenerEstadisticas() {
        Map<String, Object> estadisticas = new HashMap<>();

        estadisticas.put("totalUsuarios", usuarioRepository.count());
        estadisticas.put("totalProductos", productoRepository.count());
        estadisticas.put("totalPedidos", pedidoRepository.count());
        estadisticas.put("totalResenas", resenaRepository.count());

        // Contar usuarios por rol
        Map<String, Long> usuariosPorRol = new HashMap<>();
        usuariosPorRol.put("COMPRADOR",
                (long) usuarioRepository.findByRolesContaining(Role.COMPRADOR).size());
        usuariosPorRol.put("VENDEDOR",
                (long) usuarioRepository.findByRolesContaining(Role.VENDEDOR).size());
        usuariosPorRol.put("ADMIN",
                (long) usuarioRepository.findByRolesContaining(Role.ADMIN).size());

        estadisticas.put("usuariosPorRol", usuariosPorRol);

        return estadisticas;
    }

    // ==================== GESTIÓN DE NOTIFICACIONES ====================

    public List<Notificacion> obtenerTodasLasNotificaciones() {
        return notificacionRepository.findAll();
    }

    public org.springframework.data.domain.Page<Notificacion> obtenerNotificacionesPaginadas(int page, int size) {
        // Asumiendo que no tiene campo createdAt, ordenamos de forma genérica o podemos no ordenar
        return notificacionRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }

    public Notificacion enviarNotificacion(String titulo, String mensaje, String tipo, String usuarioId) {
        Notificacion notificacion = new Notificacion(titulo, mensaje, tipo);
        notificacion.setUsuarioId(usuarioId);
        return notificacionRepository.save(notificacion);
    }

    public void eliminarNotificacion(String id) {
        notificacionRepository.deleteById(id);
    }

    // ==================== GESTIÓN DE MENSAJES ====================

    public List<Mensaje> obtenerTodosLosMensajes() {
        return mensajeRepository.findAll();
    }

    public org.springframework.data.domain.Page<Mensaje> obtenerMensajesPaginados(int page, int size) {
        // Mensaje suele tener un id secuencial o fecha, ordenamos por id desc
        return mensajeRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }

    public void eliminarMensaje(String id) {
        mensajeRepository.deleteById(id);
    }

    // ==================== SESIONES (Actividad Reciente) ====================

    public List<Usuario> obtenerUsuariosActivos() {
        // Consideramos "activos" a los que se conectaron en las últimas 24 horas
        LocalDateTime hace24Horas = LocalDateTime.now().minusHours(24);
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getUltimaConexion() != null && u.getUltimaConexion().isAfter(hace24Horas))
                .sorted(Comparator.comparing(Usuario::getUltimaConexion).reversed())
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<Usuario> obtenerVendedoresPorEstadoVerificacionPaginado(boolean verificado, int page, int size) {
        return usuarioRepository.findVendedoresPorEstadoVerificacion(verificado, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public org.springframework.data.domain.Page<Usuario> obtenerUsuariosActivosPaginado(int page, int size) {
        LocalDateTime hace24Horas = LocalDateTime.now().minusHours(24);
        return usuarioRepository.findUsuariosActivosPaginados(hace24Horas, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "ultimaConexion")));
    }

    public long obtenerTotalNotificaciones() {
        return notificacionRepository.count();
    }

    public long obtenerTotalMensajes() {
        return mensajeRepository.count();
    }

    public long obtenerCantidadVendedoresPendientes() {
        return usuarioRepository.findVendedoresPorEstadoVerificacion(false, PageRequest.of(0, 1)).getTotalElements();
    }

    // ==================== GESTIÓN DE TRANSPORTISTAS ====================

    public List<Usuario> obtenerTodosLosTransportistas() {
        return usuarioRepository.findByRolesContaining(Role.TRANSPORTISTA);
    }

    public long obtenerCantidadTransportistasPendientes() {
        return usuarioRepository.findByRolesContaining(Role.TRANSPORTISTA).stream()
                .filter(u -> u.getPerfilTransportista() != null && !u.getPerfilTransportista().isVerificado())
                .count();
    }

    public Usuario verificarTransportista(String usuarioId, boolean verificado) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (usuario.getPerfilTransportista() == null) {
            usuario.setPerfilTransportista(new PerfilTransportista());
        }
        usuario.getPerfilTransportista().setVerificado(verificado);
        Usuario guardado = usuarioRepository.save(usuario);
        if (notificacionService != null) {
            String msg = verificado ? "¡Tu cuenta de transportista ha sido verificada! Ya puedes aceptar compras disponibles."
                    : "Tu cuenta de transportista ha dejado de estar verificada.";
            notificacionService.enviar(usuarioId, "Verificación de Transportista", msg, verificado ? "SUCCESS" : "WARNING");
        }
        return guardado;
    }
}