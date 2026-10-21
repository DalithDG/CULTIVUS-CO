package com.example.demo.repository;

import com.example.demo.Model.Compra;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompraRepository extends MongoRepository<Compra, String> {

    // Una compra por referencia de pago (Wompi)
    Optional<Compra> findByReferenciaPago(String referenciaPago);

    // Compras de un comprador, más recientes primero
    List<Compra> findByComprador_IdOrderByFechaDesc(String compradorId);
}
