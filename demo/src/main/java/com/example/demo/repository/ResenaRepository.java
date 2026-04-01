package com.example.demo.repository;

import com.example.demo.Model.Resena;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResenaRepository extends MongoRepository<Resena, String> {

    // Buscar reseñas por producto
    List<Resena> findByProductoId(String productoId);

    // Buscar reseñas por pedido
    List<Resena> findByPedidoId(String pedidoId);

    // Buscar reseñas por usuario
    List<Resena> findByUsuarioId(String usuarioId);

    // Buscar reseña específica por pedido y producto
    Optional<Resena> findByPedidoIdAndProductoId(String pedidoId, String productoId);

    // Verificar si existe reseña para un pedido y producto
    boolean existsByPedidoIdAndProductoId(String pedidoId, String productoId);

    // Calcular promedio de calificaciones por producto
    @Query("{ 'producto_id': ?0 }")
    List<Resena> findAllByProductoIdParaPromedio(String productoId);

    // Contar reseñas por producto
    long countByProductoId(String productoId);

    // Obtener últimas reseñas ordenadas por fecha
    List<Resena> findTop10ByOrderByFechaDesc();
}