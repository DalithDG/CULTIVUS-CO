package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Ubicacion;

import java.util.Optional;

@Repository
public interface UbicacionRepository extends MongoRepository<Ubicacion, String> {

    // Buscar departamento por nombre
    Optional<Ubicacion> findByNombre(String nombre);

    // Verificar si existe un departamento
    boolean existsByNombre(String nombre);
}