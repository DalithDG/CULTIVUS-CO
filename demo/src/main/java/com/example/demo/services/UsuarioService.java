package com.example.demo.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;

@Service
public class UsuarioService implements IUsuarioService {

    @Autowired
    private UsuarioRepository repositorio;

    @Override
    public void actualizarUsuario(Usuario usuario) {
        if (usuario.getId() == null || usuario.getId().isEmpty()) {
            throw new IllegalArgumentException("ID de usuario inválido");
        }
        if (!repositorio.existsById(usuario.getId())) {
            throw new IllegalArgumentException("Usuario no encontrado con ID: " + usuario.getId());
        }
        repositorio.save(usuario);
    }

    @Override
    public Usuario obtenerUsuarioPorId(String id) {
        return repositorio.findById(id).orElse(null);
    }

    @Override
    public Usuario iniciarSesion(String correo, String contrasena) {
        Optional<Usuario> usuarioOpt = repositorio.findByEmail(correo);
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            if (usuario.getContrasena().equals(contrasena)) {
                return usuario;
            }
        }
        return null;
    }

    @Override
    public List<Usuario> obtenerTodos() {
        return repositorio.findAll();
    }

    @Override
    public boolean eliminarUsuario(String id) {
        if (repositorio.existsById(id)) {
            repositorio.deleteById(id);
            return true;
        }
        return false;
    }

    @Override
    public Usuario save(Usuario usuario) {
        // Si no tiene rol asignado, se le asigna COMPRADOR por defecto
        if (usuario.getRol() == null || usuario.getRol().isEmpty()) {
            usuario.setRol("COMPRADOR");
        }
        return repositorio.save(usuario);
    }

    @Override
    public boolean existeEmail(String email) {
        return repositorio.existsByEmail(email);
    }

    @Override
    public Usuario findByEmail(String email) {
        return repositorio.findByEmail(email).orElse(null);
    }

    @Override
    public Usuario findUsuario(String email) {
        return repositorio.findByEmail(email).orElse(null);
    }

    // Obtener todos los vendedores
    public List<Usuario> obtenerVendedores() {
        return repositorio.findByRol("VENDEDOR");
    }

    // Obtener todos los compradores
    public List<Usuario> obtenerCompradores() {
        return repositorio.findByRol("COMPRADOR");
    }
}