package com.example.demo.services;

import com.example.demo.Model.Pedido;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VendedorService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    /**
     * Crea un perfil de vendedor para un usuario existente
     */
    public Usuario crearPerfilVendedor(String usuarioId, String razonSocial,
                                       String telefonoContacto, String direccionNegocio,
                                       String descripcionNegocio, String cuentaBancaria,
                                       String banco) {

        // Validar que el usuario existe
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Validar que el usuario no tiene ya un perfil de vendedor
        if (usuario.getPerfilVendedor() != null) {
            throw new IllegalArgumentException("Este usuario ya tiene un perfil de vendedor");
        }

        // Validaciones de campos obligatorios
        if (telefonoContacto == null || telefonoContacto.trim().isEmpty()) {
            throw new IllegalArgumentException("El teléfono de contacto es obligatorio");
        }

        // Crear el perfil embebido
        PerfilVendedor perfil = new PerfilVendedor();
        perfil.setRazonSocial(razonSocial);
        perfil.setTelefonoContacto(telefonoContacto);
        perfil.setDireccionNegocio(direccionNegocio);
        perfil.setDescripcionNegocio(descripcionNegocio);
        perfil.setCuentaBancaria(cuentaBancaria);
        perfil.setBanco(banco);
        perfil.setVerificado(false);

        // Embeber el perfil y cambiar el rol dentro del mismo documento
        usuario.setPerfilVendedor(perfil);
        usuario.setRol("VENDEDOR");

        return usuarioRepository.save(usuario);
    }

    /**
     * Obtiene el perfil de vendedor de un usuario
     */
    public Optional<PerfilVendedor> obtenerPerfilPorUsuarioId(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getPerfilVendedor);
    }

    /**
     * Verifica si un usuario es vendedor
     */
    public boolean esVendedor(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(u -> "VENDEDOR".equals(u.getRol()))
                .orElse(false);
    }

    /**
     * Actualiza el perfil de vendedor
     */
    public Usuario actualizarPerfil(String usuarioId, String razonSocial,
                                    String telefonoContacto, String direccionNegocio,
                                    String descripcionNegocio, String cuentaBancaria,
                                    String banco) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        PerfilVendedor perfil = usuario.getPerfilVendedor();
        if (perfil == null) {
            throw new IllegalArgumentException("Este usuario no tiene perfil de vendedor");
        }

        if (razonSocial != null) perfil.setRazonSocial(razonSocial);
        if (telefonoContacto != null) perfil.setTelefonoContacto(telefonoContacto);
        if (direccionNegocio != null) perfil.setDireccionNegocio(direccionNegocio);
        if (descripcionNegocio != null) perfil.setDescripcionNegocio(descripcionNegocio);
        if (cuentaBancaria != null) perfil.setCuentaBancaria(cuentaBancaria);
        if (banco != null) perfil.setBanco(banco);

        usuario.setPerfilVendedor(perfil);
        return usuarioRepository.save(usuario);
    }

    /**
     * Elimina el perfil de vendedor (el usuario deja de ser vendedor)
     */
    public void eliminarPerfilVendedor(String usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        usuario.setPerfilVendedor(null);
        usuario.setRol("COMPRADOR");
        usuarioRepository.save(usuario);
    }

    /**
     * Obtiene todos los pedidos del vendedor
     */
    public List<Pedido> obtenerPedidosDelVendedor(String vendedorId) {
        return pedidoRepository.findByVendedorId(vendedorId);
    }

    /**
     * Obtiene los pedidos del vendedor filtrados por estado
     */
    public List<Pedido> obtenerPedidosDelVendedorPorEstado(String vendedorId, String estado) {
        return pedidoRepository.findByVendedorIdAndEstado(vendedorId, estado);
    }

    /**
     * Actualiza el estado de un pedido
     */
    public void actualizarEstadoPedido(String pedidoId, String nuevoEstado) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        pedido.setEstado(nuevoEstado);
        pedidoRepository.save(pedido);
    }

    /**
     * Calcula el total de ventas del vendedor
     */
    public double calcularTotalVentas(String vendedorId) {
        List<Pedido> pedidos = pedidoRepository.findByVendedorId(vendedorId);
        return pedidos.stream()
                .filter(p -> !"CANCELADO".equals(p.getEstado())) // Consideramos ventas no canceladas
                .mapToDouble(Pedido::getTotal)
                .sum();
    }
}