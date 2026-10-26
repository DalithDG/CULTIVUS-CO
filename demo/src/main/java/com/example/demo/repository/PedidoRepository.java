package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Pedido;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends MongoRepository<Pedido, String> {

    // Buscar pedidos por comprador
    List<Pedido> findByCompradorId(String compradorId);

    // Buscar pedidos por estado
    List<Pedido> findByEstado(String estado);

    // Buscar pedidos de un comprador por estado
    List<Pedido> findByCompradorIdAndEstado(String compradorId, String estado);

    // Buscar pedidos de un vendedor específico
    List<Pedido> findByVendedor_Id(String vendedorId);

    // Buscar pedidos de un vendedor por estado
    List<Pedido> findByVendedor_IdAndEstado(String vendedorId, String estado);

    // Ventas del vendedor, más recientes primero
    List<Pedido> findByVendedor_IdOrderByFechaPedidoDesc(String vendedorId);

    // Buscar un pedido garantizando que pertenece al vendedor autenticado
    Optional<Pedido> findByIdAndVendedor_Id(String pedidoId, String vendedorId);

    // Contar pedidos del comprador para el resumen de actividad cruzada
    long countByCompradorId(String compradorId);

    // Buscar pedidos por referencia de pago (Wompi)
    List<Pedido> findByPago_Referencia(String referencia);

    // Sub-pedidos de una compra (US-21)
    List<Pedido> findByCompraId(String compraId);

    // Pedidos pagados que quedaron en revisión del admin (stock insuficiente al confirmar)
    List<Pedido> findByRequiereRevisionTrue();
}