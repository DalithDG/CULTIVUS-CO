package com.example.demo.Controller;

import com.example.demo.Model.Pedido;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.ProductoRepository;
import com.example.demo.services.ResenaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/productos")
public class ResenaController {

    @Autowired
    private ResenaService resenaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    /**
     * Crear una reseña
     */
    @PostMapping("/{id}/resena")
    public String crearResena(@PathVariable String id,
            @RequestParam("calificacion") int calificacion,
            @RequestParam(value = "comentario", required = false) String comentario,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Debe iniciar sesión para dejar una reseña");
            return "redirect:/usuario/login";
        }

        try {
            // Validar que el producto existe
            productoRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

            // Validar calificación
            if (calificacion < 1 || calificacion > 5) {
                redirectAttributes.addFlashAttribute("error",
                        "La calificación debe estar entre 1 y 5 estrellas");
                return "redirect:/producto/" + id;
            }

            // Buscar el pedido del usuario que contiene este producto
            List<Pedido> pedidos = pedidoRepository.findByCompradorId(usuario.getId());

            String pedidoIdValido = null;
            for (Pedido pedido : pedidos) {
                // Verificar si el pedido contiene el producto
                boolean contieneProducto = pedido.getItems().stream()
                        .anyMatch(item -> item.getProductoId().equals(id));

                if (contieneProducto &&
                        !resenaService.obtenerResenaPorId(pedido.getId()).isPresent()) {
                    pedidoIdValido = pedido.getId();
                    break;
                }
            }

            if (pedidoIdValido == null) {
                redirectAttributes.addFlashAttribute("error",
                        "No puedes reseñar este producto. Debes haberlo comprado primero.");
                return "redirect:/producto/" + id;
            }

            // Verificar que puede reseñar
            if (!resenaService.puedeResenar(usuario.getId(), id, pedidoIdValido)) {
                redirectAttributes.addFlashAttribute("error",
                        "Ya has reseñado este producto en este pedido.");
                return "redirect:/producto/" + id;
            }

            // Crear la reseña
            resenaService.crearResena(pedidoIdValido, id, usuario.getId(),
                    calificacion, comentario);
            redirectAttributes.addFlashAttribute("mensaje", "¡Gracias por tu reseña!");

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al crear la reseña: " + e.getMessage());
        }

        return "redirect:/producto/" + id;
    }

    /**
     * Eliminar una reseña
     */
    @PostMapping("/resena/{idResena}/eliminar")
    public String eliminarResena(@PathVariable String idResena,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión");
            return "redirect:/usuario/login";
        }

        String productoId = null;
        try {
            // Obtener el productoId ANTES de eliminar
            var resenaOpt = resenaService.obtenerResenaPorId(idResena);
            if (resenaOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Reseña no encontrada");
                return "redirect:/";
            }

            productoId = resenaOpt.get().getProductoId();

            resenaService.eliminarResena(idResena, usuario.getId());
            redirectAttributes.addFlashAttribute("mensaje", "Reseña eliminada exitosamente");

            return "redirect:/producto/" + productoId;

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return productoId != null
                    ? "redirect:/producto/" + productoId
                    : "redirect:/";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al eliminar la reseña: " + e.getMessage());
            return productoId != null
                    ? "redirect:/producto/" + productoId
                    : "redirect:/";
        }
    }
}