package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Producto;

import java.util.List;

@Repository
public interface ProductoRepository extends MongoRepository<Producto, String> {

    // Buscar productos por categoría
    List<Producto> findByCategoriaId(String categoriaId);

    // Buscar productos por vendedor
    List<Producto> findByVendedorId(String vendedorId);

    // Buscar productos por nombre (búsqueda parcial)
    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    // Buscar productos con stock disponible
    List<Producto> findByStockGreaterThan(int stock);

    // Buscar productos por rango de precio
    List<Producto> findByPrecioBetween(Double precioMin, Double precioMax);

    // Búsqueda por nombre o descripción
    List<Producto> findByNombreContainingIgnoreCaseOrDescripcionContainingIgnoreCase(
            String nombre, String descripcion);

    // Buscar productos ordenados por precio ascendente
    List<Producto> findAllByOrderByPrecioAsc();

    // Buscar productos ordenados por precio descendente
    List<Producto> findAllByOrderByPrecioDesc();

    // Buscar solo productos disponibles
    List<Producto> findByDisponibleTrue();

    // Buscar productos disponibles por categoría
    List<Producto> findByCategoriaIdAndDisponibleTrue(String categoriaId);

    // Buscar productos disponibles de un vendedor
    List<Producto> findByVendedorIdAndDisponibleTrue(String vendedorId);

    // Eliminar todos los productos de un vendedor
    void deleteByVendedorId(String vendedorId);
}