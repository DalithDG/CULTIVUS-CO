package com.example.demo.services;

import com.example.demo.Model.Pedido;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.security.SecurityContextRefreshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TransportistaService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextRefreshService securityContextRefreshService;

    /**
     * Verifica si un usuario tiene el rol TRANSPORTISTA.
     */
    public boolean esTransportista(String usuarioId) {
        if (usuarioId == null) return false;
        return usuarioRepository.findById(usuarioId)
                .map(u -> u.hasRole(Role.TRANSPORTISTA))
                .orElse(false);
    }

    /**
     * Obtiene el perfil de transportista de un usuario.
     */
    public Optional<PerfilTransportista> obtenerPerfilPorUsuarioId(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getPerfilTransportista);
    }

    /**
     * Permite a un usuario logueado registrarse como transportista.
     */
    public Usuario crearPerfilTransportista(
            String usuarioId,
            String tipoVehiculo,
            String placa,
            Double capacidadCargaKg,
            String licenciaConduccion,
            String telefonoContacto,
            String ciudadBase,
            String departamentoBase,
            String zonasCobertura,
            boolean refrigerado,
            String empresa,
            String banco,
            String cuentaBancaria) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (usuario.getPerfilTransportista() != null) {
            throw new IllegalArgumentException("Este usuario ya tiene un perfil de transportista");
        }

        PerfilTransportista perfil = new PerfilTransportista(
                tipoVehiculo,
                placa != null ? placa.trim().toUpperCase() : null,
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

        usuario.setPerfilTransportista(perfil);
        usuario.addRole(Role.TRANSPORTISTA);

        Usuario guardado = usuarioRepository.save(usuario);
        securityContextRefreshService.refrescarAutenticacion(guardado.getEmail());
        return guardado;
    }

    /**
     * Registra un nuevo usuario directamente como TRANSPORTISTA.
     */
    public Usuario registrarNuevoTransportista(
            String nombre,
            String email,
            String contrasena,
            String tipoVehiculo,
            String placa,
            Double capacidadCargaKg,
            String licenciaConduccion,
            String telefonoContacto,
            String ciudadBase,
            String departamentoBase,
            String zonasCobertura,
            boolean refrigerado,
            String empresa,
            String banco,
            String cuentaBancaria) {

        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El correo ya se encuentra registrado");
        }

        Usuario nuevo = new Usuario();
        nuevo.setNombre(nombre);
        nuevo.setEmail(email.trim().toLowerCase());
        nuevo.setContrasena(passwordEncoder.encode(contrasena));
        nuevo.addRole(Role.TRANSPORTISTA);
        nuevo.addRole(Role.COMPRADOR);

        PerfilTransportista perfil = new PerfilTransportista(
                tipoVehiculo,
                placa != null ? placa.trim().toUpperCase() : null,
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
        nuevo.setPerfilTransportista(perfil);

        return usuarioRepository.save(nuevo);
    }

    /**
     * Cambia la disponibilidad del transportista para recibir pedidos de envío.
     */
    public Usuario actualizarDisponibilidad(String usuarioId, boolean disponible) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (usuario.getPerfilTransportista() != null) {
            usuario.getPerfilTransportista().setDisponible(disponible);
            return usuarioRepository.save(usuario);
        }
        return usuario;
    }

    /**
     * Obtiene pedidos que están listos para despacho/envío.
     */
    public List<Pedido> obtenerPedidosDisponiblesParaEnvio() {
        return pedidoRepository.findAll().stream()
                .filter(p -> "CONFIRMADO".equalsIgnoreCase(p.getEstado()) || "ENVIADO".equalsIgnoreCase(p.getEstado()))
                .sorted((p1, p2) -> p2.getFechaPedido().compareTo(p1.getFechaPedido()))
                .collect(Collectors.toList());
    }
}
