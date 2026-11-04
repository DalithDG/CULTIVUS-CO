package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Carrito;

import java.util.Optional;

@Repository
public interface CarritoRepository extends MongoRepository<Carrito, String> {

    Optional<Carrito> findByUsuarioId(String usuarioId);

    boolean existsByUsuarioId(String usuarioId);

    /** US-06: hay carritos que contienen esta oferta (bloquea cambiar su unidad). */
    boolean existsByItems_OfertaId(String ofertaId);
}