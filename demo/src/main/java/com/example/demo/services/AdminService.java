package com.example.demo.services;

import com.example.demo.Model.Producto;
import com.example.demo.Model.Resena;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilAdmin;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.ProductoRepository;
import com.example.demo.repository.ResenaRepository;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminService {

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
        usuario.setRol("ADMIN");
        usuario.setPerfilAdmin(perfilAdmin);

        return usuarioRepository.save(usuario);
    }

    /**
     * Verifica si un usuario es administrador
     */
    public boolean esAdmin(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(u -> "ADMIN".equals(u.getRol()))
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

        if (nivelPermisos != null) perfil.setNivelPermisos(nivelPermisos);
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
        if (!"ADMIN".equals(usuario.getRol())) {
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

        usuario.setRol(nuevoRol);
        usuarioRepository.save(usuario);
    }

    /**
     * Eliminar usuario
     */
    public void eliminarUsuario(String usuarioId) {
        usuarioRepository.deleteById(usuarioId);
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
        return productoRepository.findByVendedorId(vendedorId);
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
                (long) usuarioRepository.findByRol("COMPRADOR").size());
        usuariosPorRol.put("VENDEDOR",
                (long) usuarioRepository.findByRol("VENDEDOR").size());
        usuariosPorRol.put("ADMIN",
                (long) usuarioRepository.findByRol("ADMIN").size());

        estadisticas.put("usuariosPorRol", usuariosPorRol);

        return estadisticas;
    }
}