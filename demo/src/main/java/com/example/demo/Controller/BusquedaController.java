package com.example.demo.Controller;

import com.example.demo.Model.Categoria;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.services.BusquedaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class BusquedaController {

    @Autowired
    private BusquedaService busquedaService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @GetMapping("/buscar")
    public String buscarProductos(
            @RequestParam(value = "q", required = false, defaultValue = "") String query,
            @RequestParam(value = "categoria", required = false) String categoriaId,
            @RequestParam(value = "precioMin", required = false) Double precioMin,
            @RequestParam(value = "precioMax", required = false) Double precioMax,
            @RequestParam(value = "orden", required = false, defaultValue = "relevancia") String orden,
            Model model) {

        List<OfertaVendedor> ofertas;

        if (categoriaId != null || precioMin != null || precioMax != null) {
            ofertas = busquedaService.busquedaAvanzada(query, categoriaId, precioMin, precioMax);
        } else if (query != null && !query.trim().isEmpty()) {
            ofertas = busquedaService.buscarProductos(query);
        } else {
            ofertas = busquedaService.buscarConStock();
        }

        if (ofertas != null && !ofertas.isEmpty()) {
            switch (orden) {
                case "precio-asc":
                case "precio-menor":
                    ofertas = ofertas.stream()
                            .sorted(Comparator.comparingDouble(o ->
                                    o.getPrecio() != null ? o.getPrecio() : Double.MAX_VALUE))
                            .collect(Collectors.toList());
                    break;
                case "precio-desc":
                case "precio-mayor":
                    ofertas = ofertas.stream()
                            .sorted(Comparator.comparingDouble((OfertaVendedor o) ->
                                    o.getPrecio() != null ? o.getPrecio() : 0.0).reversed())
                            .collect(Collectors.toList());
                    break;
                case "nombre":
                    ofertas = ofertas.stream()
                            .sorted(Comparator.comparing(
                                    o -> o.getNombreProducto() != null ? o.getNombreProducto() : "",
                                    String.CASE_INSENSITIVE_ORDER))
                            .collect(Collectors.toList());
                    break;
                case "recientes":
                default:
                    ofertas = ofertas.stream()
                            .sorted(Comparator.comparing(
                                    (OfertaVendedor o) -> o.getCreatedAt() != null ? o.getCreatedAt() : LocalDateTime.MIN)
                                    .reversed())
                            .collect(Collectors.toList());
                    break;
            }
        }

        List<Categoria> categorias = categoriaRepository.findAll();
        List<OfertaVendedor> listaSegura = ofertas != null ? ofertas : new ArrayList<>();

        model.addAttribute("ofertas", listaSegura);
        model.addAttribute("productos", listaSegura);
        model.addAttribute("categorias", categorias);
        model.addAttribute("query", query);
        model.addAttribute("categoriaSeleccionada", categoriaId);
        model.addAttribute("precioMin", precioMin);
        model.addAttribute("precioMax", precioMax);
        model.addAttribute("orden", orden);
        model.addAttribute("totalResultados", listaSegura.size());

        return "public/busqueda-resultados";
    }
}
