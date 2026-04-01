package com.example.demo.Controller;

import com.example.demo.Model.Producto;
import com.example.demo.Model.Resena;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.ProductoRepository;
import com.example.demo.services.ResenaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class CatalogoController {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ResenaService resenaService;

    /**
     * Muestra todos los productos disponibles (página pública)
     */
    @GetMapping("/category")
    public String listarProductos(Model model, HttpSession session) {

        // Solo mostrar productos disponibles
        List<Producto> productos = productoRepository.findByDisponibleTrue();

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");

        model.addAttribute("productos", productos);
        model.addAttribute("usuario", usuario);

        return "category";
    }

    /**
     * Muestra el detalle de un producto específico (página pública)
     */
    @GetMapping("/producto/{id}")
    public String verDetalleProducto(@PathVariable String id,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Producto producto = productoRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");

            // Obtener reseñas del producto
            List<Resena> resenas = resenaService.obtenerResenasPorProducto(id);
            double promedioCalificacion = resenaService.calcularPromedioCalificacion(id);
            long totalResenas = resenaService.contarResenas(id);

            model.addAttribute("producto", producto);
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
}