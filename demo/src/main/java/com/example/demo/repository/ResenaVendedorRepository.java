package com.example.demo.repository;

import com.example.demo.Model.ResenaVendedor;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ResenaVendedorRepository extends MongoRepository<ResenaVendedor, String> {
    List<ResenaVendedor> findByVendedorId(String vendedorId);
    Optional<ResenaVendedor> findByPedidoId(String pedidoId);
    boolean existsByPedidoId(String pedidoId);
    List<ResenaVendedor> findByUsuarioId(String usuarioId);
}
