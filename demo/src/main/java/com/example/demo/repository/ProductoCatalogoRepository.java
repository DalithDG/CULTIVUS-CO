package com.example.demo.repository;

import com.example.demo.Model.ProductoCatalogo;
import org.springframework.data.mongodb.repository.MongoRepository;
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

    /** Catálogo por categoría */
    List<ProductoCatalogo> findByCategoriaIdAndAprobadoTrueAndActivoTrue(String categoriaId);

    /** Búsqueda por nombre parcial (catálogo público) */
    List<ProductoCatalogo> findByNombreContainingIgnoreCaseAndAprobadoTrueAndActivoTrue(String nombre);

    /** Búsqueda por nombre o descripción (catálogo público) */
    List<ProductoCatalogo> findByAprobadoTrueAndActivoTrueAndNombreContainingIgnoreCaseOrAprobadoTrueAndActivoTrueAndDescripcionContainingIgnoreCase(
            String nombre, String descripcion);

    /** Admin: productos pendientes de aprobación */
    List<ProductoCatalogo> findByAprobadoFalseAndActivoTrue();

    /** Productos sugeridos por un vendedor específico */
    List<ProductoCatalogo> findBySugeridoPor(String vendedorId);
}
