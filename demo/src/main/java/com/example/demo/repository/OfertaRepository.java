package com.example.demo.repository;

import com.example.demo.Model.OfertaVendedor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfertaRepository extends MongoRepository<OfertaVendedor, String> {

    /** Ofertas aprobadas y disponibles para un producto del catálogo (vista pública) */
    List<OfertaVendedor> findByProductoCatalogoIdAndDisponibleTrue(
            String productoCatalogoId);

    /** Todas las ofertas activas de un producto (para recalcular estadísticas) */
    List<OfertaVendedor> findByProductoCatalogoId(
            String productoCatalogoId);

    /** Todas las ofertas de un vendedor */
    List<OfertaVendedor> findByVendedor_Id(String vendedorId);

    /** Ofertas disponibles de un vendedor */
    List<OfertaVendedor> findByVendedor_IdAndDisponibleTrue(String vendedorId);

    /** Verificar si un vendedor ya tiene oferta para un producto */
    Optional<OfertaVendedor> findByProductoCatalogoIdAndVendedor_Id(
            String productoCatalogoId, String vendedorId);

    boolean existsByProductoCatalogoIdAndVendedor_Id(
            String productoCatalogoId, String vendedorId);



    /** Eliminar todas las ofertas de un vendedor */
    void deleteByVendedor_Id(String vendedorId);

    /** Contar ofertas aprobadas y disponibles para un producto */
    long countByProductoCatalogoIdAndDisponibleTrue(
            String productoCatalogoId);
}
