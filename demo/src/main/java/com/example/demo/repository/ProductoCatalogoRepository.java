package com.example.demo.repository;

import com.example.demo.Model.ProductoCatalogo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoCatalogoRepository extends MongoRepository<ProductoCatalogo, String> {

    /** Buscar por nombre normalizado (para evitar duplicados) */
    Optional<ProductoCatalogo> findByNombreNormalizado(String nombreNormalizado);

    boolean existsByNombreNormalizado(String nombreNormalizado);

    /** Catálogo público: solo productos aprobados y activos */
    List<ProductoCatalogo> findByAprobadoTrueAndActivoTrue();

    /** Catálogo público paginado */
    @Query(value = "{ 'aprobado': true, 'activo': true }", collation = "{ 'locale': 'es', 'strength': 2 }")
    Page<ProductoCatalogo> findPublico(Pageable pageable);

    /** Catálogo por categoría(es) paginado */
    @Query(value = "{ 'aprobado': true, 'activo': true, 'categoria.id': { $in: ?0 } }", collation = "{ 'locale': 'es', 'strength': 2 }")
    Page<ProductoCatalogo> findPublicoByCategorias(List<String> categoriaIds, Pageable pageable);

    /** Catálogo con precio máximo paginado */
    @Query(value = "{ 'aprobado': true, 'activo': true, 'precioMinimo': { $lte: ?0 } }", collation = "{ 'locale': 'es', 'strength': 2 }")
    Page<ProductoCatalogo> findPublicoByPrecioMax(Double precioMax, Pageable pageable);

    /** Catálogo por categoría(es) y precio máximo paginado */
    @Query(value = "{ 'aprobado': true, 'activo': true, 'categoria.id': { $in: ?0 }, 'precioMinimo': { $lte: ?1 } }", collation = "{ 'locale': 'es', 'strength': 2 }")
    Page<ProductoCatalogo> findPublicoByCategoriasAndPrecioMax(List<String> categoriaIds, Double precioMax, Pageable pageable);

    /** Catálogo por categoría */
    List<ProductoCatalogo> findByCategoriaIdAndAprobadoTrueAndActivoTrue(String categoriaId);

    /** Búsqueda por nombre parcial (catálogo público) */
    List<ProductoCatalogo> findByNombreContainingIgnoreCaseAndAprobadoTrueAndActivoTrue(String nombre);

    /** Admin: productos pendientes de aprobación */
    List<ProductoCatalogo> findByAprobadoFalseAndActivoTrue();

    // Paginación para Admin de catálogo pendiente
    org.springframework.data.domain.Page<ProductoCatalogo> findByAprobadoFalseAndActivoTrue(org.springframework.data.domain.Pageable pageable);

    /** Productos sugeridos por un vendedor específico */
    List<ProductoCatalogo> findBySugeridoPor(String vendedorId);
}