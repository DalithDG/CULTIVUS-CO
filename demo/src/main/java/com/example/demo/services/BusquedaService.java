package com.example.demo.services;

import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.repository.ProductoCatalogoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BusquedaService {

    @Autowired
    private ProductoCatalogoRepository catalogoRepository;

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Normaliza un texto removiendo acentos y pasando a minúsculas para búsquedas flexibles.
     */
    private String normalizar(String texto) {
        if (texto == null) return "";
        String descomponer = Normalizer.normalize(texto.toLowerCase().trim(), Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(descomponer).replaceAll("");
    }

    /**
     * Obtiene solo productos aprobados, activos y con ofertas activas de vendedores (totalVendedores > 0).
     */
    private List<ProductoCatalogo> obtenerPublicosConVendedores() {
        List<ProductoCatalogo> todos = catalogoRepository.findByAprobadoTrueAndActivoTrue();
        if (todos == null) {
            return Collections.emptyList();
        }
        return todos.stream()
                .filter(p -> p != null && p.getTotalVendedores() != null && p.getTotalVendedores() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Buscar productos del catálogo por query (nombre).
     * Solo retorna ProductoCatalogo con totalVendedores > 0.
     */
    public List<ProductoCatalogo> buscarProductos(String query) {
        List<ProductoCatalogo> base = obtenerPublicosConVendedores();
        if (query == null || query.trim().isEmpty()) {
            return base;
        }

        String queryNormalizada = normalizar(query);
        String[] palabras = queryNormalizada.split("\\s+");

        return base.stream()
                .filter(p -> {
                    if (p.getNombre() == null) return false;
                    String nombreNorm = normalizar(p.getNombre());
                    String normalizadoDoc = p.getNombreNormalizado() != null ? normalizar(p.getNombreNormalizado()) : "";
                    for (String palabra : palabras) {
                        if (!palabra.isEmpty() && (nombreNorm.contains(palabra) || normalizadoDoc.contains(palabra))) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Buscar productos del catálogo por categoría.
     * Solo retorna ProductoCatalogo con totalVendedores > 0.
     */
    public List<ProductoCatalogo> buscarPorCategoria(String categoriaId) {
        if (categoriaId == null || categoriaId.isBlank()) {
            return obtenerPublicosConVendedores();
        }
        List<ProductoCatalogo> porCat = catalogoRepository.findByCategoriaIdAndAprobadoTrueAndActivoTrue(categoriaId);
        if (porCat == null) {
            return Collections.emptyList();
        }
        return porCat.stream()
                .filter(p -> p != null && p.getTotalVendedores() != null && p.getTotalVendedores() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Buscar productos del catálogo con vendedores disponibles.
     */
    public List<ProductoCatalogo> buscarConStock() {
        return obtenerPublicosConVendedores();
    }

    /**
     * Búsqueda avanzada con filtros sobre el catálogo (solo con totalVendedores > 0).
     */
    public List<ProductoCatalogo> busquedaAvanzada(String query, String categoriaId,
                                                    Double precioMin, Double precioMax) {
        List<ProductoCatalogo> resultados = buscarProductos(query);

        // Filtrar por categoría si se especifica
        if (categoriaId != null && !categoriaId.isEmpty()) {
            resultados = resultados.stream()
                    .filter(p -> p != null && p.getCategoria() != null &&
                            categoriaId.equals(p.getCategoria().getId()))
                    .collect(Collectors.toList());
        }

        // Filtrar por rango de precio (usando precioMinimo del catálogo)
        if (precioMin != null && precioMax != null) {
            resultados = resultados.stream()
                    .filter(p -> p != null && p.getPrecioMinimo() != null &&
                            p.getPrecioMinimo() >= precioMin && p.getPrecioMinimo() <= precioMax)
                    .collect(Collectors.toList());
        } else if (precioMin != null) {
            resultados = resultados.stream()
                    .filter(p -> p != null && p.getPrecioMinimo() != null && p.getPrecioMinimo() >= precioMin)
                    .collect(Collectors.toList());
        } else if (precioMax != null) {
            resultados = resultados.stream()
                    .filter(p -> p != null && p.getPrecioMinimo() != null && p.getPrecioMinimo() <= precioMax)
                    .collect(Collectors.toList());
        }

        return resultados;
    }
}
