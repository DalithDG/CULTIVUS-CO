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
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
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
     * Obtener todos los usuarios
     */
    public List<Usuario> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
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
     * Obtener todos los productos
     */
    public List<Producto> obtenerTodosLosProductos() {
        return productoRepository.findAll();
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
        return resenaRepository.findAll();
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
        return pedidoRepository.findAll();
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
}