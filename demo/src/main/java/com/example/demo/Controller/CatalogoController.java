package com.example.demo.Controller;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.Resena;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.services.BusquedaService;
import com.example.demo.services.CatalogoService;
import com.example.demo.services.ResenaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class CatalogoController {

    @Autowired
    private CatalogoService catalogoService;

    @Autowired
    private ResenaService resenaService;

    @Autowired
    private BusquedaService busquedaService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    /**
     * Muestra el catálogo centralizado (1 card por tipo de producto).
     * Cada card muestra el precioMinimo y totalVendedores.
     */
    @GetMapping("/category")
    public String listarCatalogo(
            @RequestParam(value = "categoria", required = false) List<String> categoriaIds,
            @RequestParam(value = "orden", required = false, defaultValue = "relevancia") String orden,
            Model model, HttpSession session) {

        // Obtener productos del catálogo (aprobados y activos)
        List<ProductoCatalogo> catalogo = catalogoService.listarCatalogo();

        // Filtrar por categorías si se especifican
        if (categoriaIds != null && !categoriaIds.isEmpty()) {
            catalogo = catalogo.stream()
                    .filter(p -> p.getCategoria() != null &&
                            categoriaIds.contains(p.getCategoria().getId()))
                    .collect(Collectors.toList());
        }

        // Aplicar ordenamiento
        if (catalogo != null && !catalogo.isEmpty()) {
            switch (orden) {
                case "precio-menor":
                    catalogo = catalogo.stream()
                            .sorted(Comparator.comparingDouble(p ->
                                    p.getPrecioMinimo() != null ? p.getPrecioMinimo() : Double.MAX_VALUE))
                            .collect(Collectors.toList());
                    break;
                case "precio-mayor":
                    catalogo = catalogo.stream()
                            .sorted(Comparator.comparingDouble((ProductoCatalogo p) ->
                                    p.getPrecioMinimo() != null ? p.getPrecioMinimo() : 0.0).reversed())
                            .collect(Collectors.toList());
                    break;
                case "nombre":
                    catalogo = catalogo.stream()
                            .sorted(Comparator.comparing(p -> p.getNombre() != null ? p.getNombre() : ""))
                            .collect(Collectors.toList());
                    break;
                case "recientes":
                    catalogo = catalogo.stream()
                            .sorted(Comparator.comparing((ProductoCatalogo p) -> 
                                    p.getCreatedAt() != null ? p.getCreatedAt() : java.time.LocalDateTime.MIN).reversed())
                            .collect(Collectors.toList());
                    break;
            }
        }

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");

        model.addAttribute("catalogo", catalogo != null ? catalogo : new ArrayList<>());
        model.addAttribute("usuario", usuario);
        model.addAttribute("categorias", categoriaRepository.findAll());
        model.addAttribute("categoriaSeleccionada", categoriaIds);
        model.addAttribute("ordenSeleccionado", orden);

        return "category";
    }

    /**
     * Muestra el detalle de un producto del catálogo con la lista de vendedores.
     */
    @GetMapping("/producto/{id}")
    public String verDetalleProducto(@PathVariable String id,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            ProductoCatalogo producto = catalogoService.obtenerPorId(id);
            if (producto == null) {
                throw new IllegalArgumentException("Producto no encontrado");
            }

            // Obtener ofertas aprobadas y disponibles, ordenadas por precio
            List<OfertaVendedor> ofertas = catalogoService.obtenerOfertasAprobadas(id);

            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");

            // Obtener reseñas del producto del catálogo
            List<Resena> resenas = resenaService.obtenerResenasPorProducto(id);
            double promedioCalificacion = resenaService.calcularPromedioCalificacion(id);
            long totalResenas = resenaService.contarResenas(id);

            model.addAttribute("producto", producto);
            model.addAttribute("ofertas", ofertas);
            model.addAttribute("usuario", usuario);
            model.addAttribute("resenas", resenas);
            model.addAttribute("promedioCalificacion", promedioCalificacion);
            model.addAttribute("totalResenas", totalResenas);

            return "producto-detalle";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/category";
        }
    }

    /**
     * Muestra el detalle de una oferta específica de un vendedor.
     */
    @GetMapping("/oferta/{id}")
    public String verDetalleOferta(@PathVariable String id,
                                   Model model,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        try {
            OfertaVendedor oferta = catalogoService.obtenerOfertaPorId(id);
            if (oferta == null) {
                throw new IllegalArgumentException("Oferta no encontrada");
            }

            // También necesitamos el producto del catálogo relacionado
            ProductoCatalogo producto = catalogoService.obtenerPorId(oferta.getProductoCatalogoId());

            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            List<Resena> resenas = resenaService.obtenerResenasPorProducto(oferta.getProductoCatalogoId());

            model.addAttribute("oferta", oferta);
            model.addAttribute("producto", producto);
            model.addAttribute("usuario", usuario);
            model.addAttribute("resenas", resenas);

            return "oferta-detalle";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/category";
        }
    }
}