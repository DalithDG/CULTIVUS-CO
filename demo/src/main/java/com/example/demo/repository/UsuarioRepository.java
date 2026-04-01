package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Usuario;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    // Buscar usuario por email
    Optional<Usuario> findByEmail(String email);

    // Verificar si existe un email
    boolean existsByEmail(String email);

    // Buscar usuarios por rol
    List<Usuario> findByRol(String rol);

    // Buscar usuarios por nombre (búsqueda parcial)
    List<Usuario> findByNombreContainingIgnoreCase(String nombre);

    // Buscar usuarios por ciudad
    List<Usuario> findByUbicacionCiudad(String ciudad);

    // Buscar usuarios por departamento
    List<Usuario> findByUbicacionDepartamento(String departamento);
}