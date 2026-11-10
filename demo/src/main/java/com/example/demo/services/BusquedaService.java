package com.example.demo.services;

import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.repository.OfertaRepository;
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
    private OfertaRepository ofertaRepository;

    @Autowired(required = false)
    private CatalogoService catalogoService;

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
     * Obtiene solo ofertas públicas (disponible = true, estado PUBLICADO y precio no nulo).
     */
    private List<OfertaVendedor> obtenerOfertasPublicas() {
        List<OfertaVendedor> todas = ofertaRepository.findAll();
        if (todas == null) {
            return Collections.emptyList();
        }
        List<OfertaVendedor> publicas = todas.stream()
                .filter(o -> o != null
                        && o.isDisponible()
                        && o.getEstadoPublicacion() == EstadoPublicacion.PUBLICADO
                        && o.getPrecio() != null)
                .collect(Collectors.toList());
        if (catalogoService != null) {
            catalogoService.enriquecerConReferencia(publicas);
        }
        return publicas;
    }

    /**
     * Buscar ofertas públicas por query (nombre del producto, descripción o vendedor).
     */
    public List<OfertaVendedor> buscarProductos(String query) {
        List<OfertaVendedor> base = obtenerOfertasPublicas();
        if (query == null || query.trim().isEmpty()) {
            return base;
        }

        String queryNormalizada = normalizar(query);
        String[] palabras = queryNormalizada.split("\\s+");

        return base.stream()
                .filter(o -> {
                    String nombreNorm = normalizar(o.getNombreProducto());
                    String catNorm = normalizar(o.getCategoriaNombre());
                    String vendedorNorm = (o.getVendedor() != null) ? normalizar(o.getVendedor().getNombre()) : "";
                    for (String palabra : palabras) {
                        if (!palabra.isEmpty() && (nombreNorm.contains(palabra)
                                || catNorm.contains(palabra)
                                || vendedorNorm.contains(palabra))) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Buscar ofertas públicas por categoría.
     */
    public List<OfertaVendedor> buscarPorCategoria(String categoriaId) {
        List<OfertaVendedor> base = obtenerOfertasPublicas();
        if (categoriaId == null || categoriaId.isBlank()) {
            return base;
        }
        return base.stream()
                .filter(o -> categoriaId.equals(o.getCategoriaId()))
                .collect(Collectors.toList());
    }

    /**
     * Buscar todas las ofertas públicas disponibles.
     */
    public List<OfertaVendedor> buscarConStock() {
        return obtenerOfertasPublicas();
    }

    /**
     * Búsqueda avanzada con filtros sobre ofertas públicas.
     */
    public List<OfertaVendedor> busquedaAvanzada(String query, String categoriaId,
                                                  Double precioMin, Double precioMax) {
        List<OfertaVendedor> resultados = buscarProductos(query);

        if (categoriaId != null && !categoriaId.isEmpty()) {
            resultados = resultados.stream()
                    .filter(o -> o != null && categoriaId.equals(o.getCategoriaId()))
                    .collect(Collectors.toList());
        }

        if (precioMin != null && precioMax != null) {
            resultados = resultados.stream()
                    .filter(o -> o != null && o.getPrecio() != null
                            && o.getPrecio() >= precioMin && o.getPrecio() <= precioMax)
                    .collect(Collectors.toList());
        } else if (precioMin != null) {
            resultados = resultados.stream()
                    .filter(o -> o != null && o.getPrecio() != null && o.getPrecio() >= precioMin)
                    .collect(Collectors.toList());
        } else if (precioMax != null) {
            resultados = resultados.stream()
                    .filter(o -> o != null && o.getPrecio() != null && o.getPrecio() <= precioMax)
                    .collect(Collectors.toList());
        }

        return resultados;
    }
}
