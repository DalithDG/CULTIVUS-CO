package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Pedido;

import java.util.List;

@Repository
public interface PedidoRepository extends MongoRepository<Pedido, String> {

    // Buscar pedidos por comprador
    List<Pedido> findByCompradorId(String compradorId);

    // Buscar pedidos por estado
    List<Pedido> findByEstado(String estado);

    // Buscar pedidos de un comprador por estado
    List<Pedido> findByCompradorIdAndEstado(String compradorId, String estado);

    // Buscar pedidos de un vendedor específico
    List<Pedido> findByVendedorId(String vendedorId);

    // Buscar pedidos de un vendedor por estado
    List<Pedido> findByVendedorIdAndEstado(String vendedorId, String estado);
}