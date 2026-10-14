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
    @GetMapping({"/category", "/categoria"})
    public String listarCatalogo(
            @RequestParam(value = "categoria", required = false) List<String> categoriaIds,
            @RequestParam(value = "orden", required = false, defaultValue = "relevancia") String orden,
            @RequestParam(value = "precioMax", required = false) Double precioMax,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size,
            Model model, HttpSession session) {

        // Validar parámetros de paginación
        if (size < 1) size = 1;
        if (size > 48) size = 48;
        if (page < 0) page = 0;

        org.springframework.data.domain.Page<ProductoCatalogo> pagina =
                catalogoService.listarCatalogo(page, size, categoriaIds, orden, precioMax);

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");

        model.addAttribute("catalogo", pagina.getContent());
        model.addAttribute("currentPage", pagina.getNumber());
        model.addAttribute("totalPages", pagina.getTotalPages() > 0 ? pagina.getTotalPages() : 1);
        model.addAttribute("totalItems", pagina.getTotalElements());

        model.addAttribute("usuario", usuario);
        model.addAttribute("categorias", categoriaRepository.findAll());
        model.addAttribute("categoriaSeleccionada", categoriaIds);
        model.addAttribute("ordenSeleccionado", orden);
        model.addAttribute("precioMax", precioMax);
        model.addAttribute("size", size);

        return "public/categoria";
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

            return "public/producto-detalle";

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

            return "public/oferta-detalle";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/category";
        }
    }
}