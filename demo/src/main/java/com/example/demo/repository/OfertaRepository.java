package com.example.demo.repository;

import com.example.demo.Model.OfertaVendedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfertaRepository extends MongoRepository<OfertaVendedor, String> {

    /** Ofertas visibles en el catálogo público, paginadas desde la base de datos */
    Page<OfertaVendedor> findByDisponibleTrueAndPrecioNotNull(Pageable pageable);

    // ── Tarjetas públicas: ofertas disponibles, PUBLICADAS y con precio ──

    @Query("{ 'disponible': true, 'estado_publicacion': 'PUBLICADO', 'precio': { $ne: null } }")
    Page<OfertaVendedor> findPublicas(Pageable pageable);

    @Query("{ 'disponible': true, 'estado_publicacion': 'PUBLICADO', 'precio': { $ne: null }, "
            + "'categoria_id': { $in: ?0 } }")
    Page<OfertaVendedor> findPublicasByCategorias(List<String> categoriaIds, Pageable pageable);

    @Query("{ 'disponible': true, 'estado_publicacion': 'PUBLICADO', 'precio': { $ne: null, $lte: ?0 } }")
    Page<OfertaVendedor> findPublicasByPrecioMax(Double precioMax, Pageable pageable);

    @Query("{ 'disponible': true, 'estado_publicacion': 'PUBLICADO', 'precio': { $ne: null, $lte: ?1 }, "
            + "'categoria_id': { $in: ?0 } }")
    Page<OfertaVendedor> findPublicasByCategoriasAndPrecioMax(List<String> categoriaIds, Double precioMax,
            Pageable pageable);

    /** Ofertas publicadas y disponibles de un producto del catálogo, ordenadas por el pageable. */
    @Query("{ 'producto_catalogo_id': ?0, 'disponible': true, 'estado_publicacion': 'PUBLICADO', "
            + "'precio': { $ne: null } }")
    List<OfertaVendedor> findPublicasByProductoCatalogoId(String productoCatalogoId);

    /** Ofertas publicadas cuyo nombre contiene el texto (regex ya escapada, sin distinguir mayúsculas). */
    @Query("{ 'disponible': true, 'estado_publicacion': 'PUBLICADO', 'precio': { $ne: null }, "
            + "'nombre_producto': { $regex: ?0, $options: 'i' } }")
    List<OfertaVendedor> findPublicasByNombreRegex(String regex);

    /** Ofertas con estado de publicación ausente (datos antiguos). */
    @Query("{ 'estado_publicacion': null }")
    List<OfertaVendedor> findSinEstadoPublicacion();

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

    /** Eliminar todas las ofertas asociadas a un producto del catálogo */
    void deleteByProductoCatalogoId(String productoCatalogoId);

    /** Contar todas las ofertas de un producto */
    long countByProductoCatalogoId(String productoCatalogoId);

    /** Contar ofertas aprobadas y disponibles para un producto */
    long countByProductoCatalogoIdAndDisponibleTrue(
            String productoCatalogoId);
}
