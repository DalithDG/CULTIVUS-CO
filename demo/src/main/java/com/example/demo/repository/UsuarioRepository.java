package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    // Buscar usuario por email
    Optional<Usuario> findByEmail(String email);

    // Buscar usuario por email (insensible a mayúsculas/minúsculas)
    Optional<Usuario> findByEmailIgnoreCase(String email);

    // Verificar si existe un email
    boolean existsByEmail(String email);

    // Buscar usuarios que tengan un rol específico en su lista
    List<Usuario> findByRolesContaining(Role rol);

    // Buscar usuarios por nombre (búsqueda parcial)
    List<Usuario> findByNombreContainingIgnoreCase(String nombre);

    // Búsqueda paginada por nombre o email
    org.springframework.data.domain.Page<Usuario> findByNombreContainingIgnoreCaseOrEmailContainingIgnoreCase(String nombre, String email, org.springframework.data.domain.Pageable pageable);

    // Búsqueda paginada por rol
    org.springframework.data.domain.Page<Usuario> findByRolesContaining(Role rol, org.springframework.data.domain.Pageable pageable);

    // Búsqueda paginada por nombre/email Y rol (usando @Query)
    @org.springframework.data.mongodb.repository.Query("{ '$and': [ { '$or': [ { 'nombre': { $regex: ?0, $options: 'i' } }, { 'email': { $regex: ?0, $options: 'i' } } ] }, { 'roles': ?1 } ] }")
    org.springframework.data.domain.Page<Usuario> findBySearchAndRole(String search, Role rol, org.springframework.data.domain.Pageable pageable);

    // Buscar usuarios por ciudad
    List<Usuario> findByUbicacionCiudad(String ciudad);

    // Buscar usuarios por departamento
    List<Usuario> findByUbicacionDepartamento(String departamento);

    // Buscar vendedores (pendientes o verificados) de forma paginada
    @org.springframework.data.mongodb.repository.Query("{ 'roles': 'VENDEDOR', 'perfilVendedor.verificado': ?0 }")
    org.springframework.data.domain.Page<Usuario> findVendedoresPorEstadoVerificacion(boolean verificado, org.springframework.data.domain.Pageable pageable);

    // Buscar usuarios activos paginados
    @org.springframework.data.mongodb.repository.Query("{ 'ultimaConexion': { '$gt': ?0 } }")
    org.springframework.data.domain.Page<Usuario> findUsuariosActivosPaginados(java.time.LocalDateTime desde, org.springframework.data.domain.Pageable pageable);
}