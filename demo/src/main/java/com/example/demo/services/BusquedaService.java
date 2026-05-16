package com.example.demo.services;

import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.repository.ProductoCatalogoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BusquedaService {

    @Autowired
    private ProductoCatalogoRepository catalogoRepository;

    /**
     * Buscar productos del catálogo por query (nombre).
     * Retorna ProductoCatalogo (1 por tipo de producto).
     */
    public List<ProductoCatalogo> buscarProductos(String query) {
        if (query == null || query.trim().isEmpty()) {
            return catalogoRepository.findByAprobadoTrueAndActivoTrue();
        }

        String queryLimpio = query.trim().toLowerCase();
        List<ProductoCatalogo> todos = catalogoRepository.findByAprobadoTrueAndActivoTrue();

        // Filtrado flexible: el nombre debe contener todas las palabras de la query
        String[] palabras = queryLimpio.split("\\s+");
        
        return todos.stream()
                .filter(p -> {
                    String nombre = p.getNombre().toLowerCase();
                    for (String palabra : palabras) {
                        if (nombre.contains(palabra)) return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Buscar productos del catálogo por categoría.
     */
    public List<ProductoCatalogo> buscarPorCategoria(String categoriaId) {
        return catalogoRepository.findByCategoriaIdAndAprobadoTrueAndActivoTrue(categoriaId);
    }

    /**
     * Buscar productos del catálogo.
     */
    public List<ProductoCatalogo> buscarConStock() {
        return catalogoRepository.findByAprobadoTrueAndActivoTrue();
    }

    /**
     * Búsqueda avanzada con filtros sobre el catálogo.
     */
    public List<ProductoCatalogo> busquedaAvanzada(String query, String categoriaId,
                                                    Double precioMin, Double precioMax) {
        List<ProductoCatalogo> resultados = buscarProductos(query);

        // Filtrar por categoría si se especifica
        if (categoriaId != null && !categoriaId.isEmpty()) {
            resultados = resultados.stream()
                    .filter(p -> p.getCategoria() != null &&
                            categoriaId.equals(p.getCategoria().getId()))
                    .collect(Collectors.toList());
        }

        // Filtrar por rango de precio (usando precioMinimo del catálogo)
        if (precioMin != null && precioMax != null) {
            resultados = resultados.stream()
                    .filter(p -> p.getPrecioMinimo() != null &&
                            p.getPrecioMinimo() >= precioMin && p.getPrecioMinimo() <= precioMax)
                    .collect(Collectors.toList());
        } else if (precioMin != null) {
            resultados = resultados.stream()
                    .filter(p -> p.getPrecioMinimo() != null && p.getPrecioMinimo() >= precioMin)
                    .collect(Collectors.toList());
        } else if (precioMax != null) {
            resultados = resultados.stream()
                    .filter(p -> p.getPrecioMinimo() != null && p.getPrecioMinimo() <= precioMax)
                    .collect(Collectors.toList());
        }

        return resultados;
    }
}