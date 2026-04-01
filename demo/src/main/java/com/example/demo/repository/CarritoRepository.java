package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Carrito;

import java.util.Optional;

@Repository
public interface CarritoRepository extends MongoRepository<Carrito, String> {

    Optional<Carrito> findByUsuarioId(String usuarioId);

    boolean existsByUsuarioId(String usuarioId);
}