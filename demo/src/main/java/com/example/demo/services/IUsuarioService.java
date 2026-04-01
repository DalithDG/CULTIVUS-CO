package com.example.demo.services;

import java.util.List;
import com.example.demo.Model.Usuario;

public interface IUsuarioService {

    // Métodos de autenticación
    Usuario iniciarSesion(String correo, String contrasena);

    // Métodos de búsqueda
    Usuario obtenerUsuarioPorId(String id);
    Usuario findByEmail(String email);
    Usuario findUsuario(String email);
    List<Usuario> obtenerTodos();

    // Métodos de modificación
    void actualizarUsuario(Usuario usuario);
    Usuario save(Usuario usuario);
    boolean eliminarUsuario(String id);

    // Validaciones
    boolean existeEmail(String email);
}