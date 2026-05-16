package com.example.demo.Controller;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.ProductoCarrito;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private ProductoCatalogoRepository catalogoRepository;

    /**
     * Muestra el carrito del usuario
     */
    @GetMapping
    public String mostrarCarrito(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Debe iniciar sesión para ver su carrito");
            return "redirect:/usuario/login";
        }

        // Obtener o crear carrito del usuario
        Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                .orElseGet(() -> {
                    Carrito nuevo = new Carrito(usuario.getId());
                    return carritoRepository.save(nuevo);
                });

        model.addAttribute("usuario", usuario);
        model.addAttribute("carrito", carrito);
        model.addAttribute("detalles", carrito.getItems());
        model.addAttribute("total", carrito.getTotalEstimado());

        return "carrito";
    }

    /**
     * Agrega una oferta al carrito (nuevo flujo: recibe ofertaId en vez de productoId)
     */
    @PostMapping("/agregar")
    public String agregarAlCarrito(
            @RequestParam("ofertaId") String ofertaId,
            @RequestParam(value = "cantidad", defaultValue = "1") Double cantidad,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error",
                        "Debe iniciar sesión para agregar productos al carrito");
                return "redirect:/usuario/login";
            }

            OfertaVendedor oferta = ofertaRepository.findById(ofertaId)
                    .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));

            // Verificar stock
            if (oferta.getStock() < cantidad) {
                redirectAttributes.addFlashAttribute("error",
                        "No hay suficiente stock disponible");
                return "redirect:/producto/" + oferta.getProductoCatalogoId();
            }

            // Obtener o crear carrito
            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseGet(() -> new Carrito(usuario.getId()));

            // Verificar si la oferta ya está en el carrito (por ofertaId)
            Optional<ProductoCarrito> itemExistente = carrito.getItems().stream()
                    .filter(i -> ofertaId.equals(i.getOfertaId()))
                    .findFirst();

            if (itemExistente.isPresent()) {
                // Actualizar cantidad del item existente
                ProductoCarrito item = itemExistente.get();
                Double nuevaCantidad = item.getCantidad() + cantidad;

                if (oferta.getStock() < nuevaCantidad) {
                    redirectAttributes.addFlashAttribute("error",
                            "No hay suficiente stock disponible");
                    return "redirect:/carrito";
                }

                item.setCantidad(nuevaCantidad);
            } else {
                // Agregar nuevo item al carrito
                ProductoCatalogo catalogo = catalogoRepository.findById(oferta.getProductoCatalogoId())
                        .orElse(null);
                String unidadAb = catalogo != null && catalogo.getUnidadMedida() != null
                        ? catalogo.getUnidadMedida() : "unid";
                String vendedorNombre = oferta.getVendedor() != null ? oferta.getVendedor().getNombre() : "";

                ProductoCarrito nuevoItem = new ProductoCarrito(
                        oferta.getProductoCatalogoId(),
                        oferta.getId(),
                        oferta.getVendedor().getId(),
                        vendedorNombre,
                        oferta.getNombreProducto(),
                        oferta.getImagenUrl(),
                        oferta.getPrecio(),
                        cantidad,
                        unidadAb,
                        oferta.getCompraMinima()
                );
                carrito.getItems().add(nuevoItem);
            }

            // Recalcular total y guardar
            carrito.recalcularTotal();
            carrito.setUpdatedAt(LocalDateTime.now());
            carritoRepository.save(carrito);

            redirectAttributes.addFlashAttribute("mensaje", "Producto agregado al carrito");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al agregar producto al carrito");
        }

        return "redirect:/carrito";
    }

    /**
     * Endpoint AJAX para agregar al carrito sin recargar la página.
     * Devuelve JSON con {success, mensaje/error, totalItems}.
     */
    @PostMapping("/agregar-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> agregarAlCarritoAjax(
            @RequestParam("ofertaId") String ofertaId,
            @RequestParam(value = "cantidad", defaultValue = "1") Double cantidad,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                response.put("success", false);
                response.put("error", "Debes iniciar sesión para agregar productos al carrito");
                response.put("redirectLogin", true);
                return ResponseEntity.ok(response);
            }

            OfertaVendedor oferta = ofertaRepository.findById(ofertaId)
                    .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));

            if (oferta.getStock() < cantidad) {
                response.put("success", false);
                response.put("error", "No hay suficiente stock disponible");
                return ResponseEntity.ok(response);
            }

            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseGet(() -> new Carrito(usuario.getId()));

            Optional<ProductoCarrito> itemExistente = carrito.getItems().stream()
                    .filter(i -> ofertaId.equals(i.getOfertaId()))
                    .findFirst();

            if (itemExistente.isPresent()) {
                ProductoCarrito item = itemExistente.get();
                Double nuevaCantidad = item.getCantidad() + cantidad;
                if (oferta.getStock() < nuevaCantidad) {
                    response.put("success", false);
                    response.put("error", "Solo quedan " + oferta.getStock() + " unidades disponibles");
                    return ResponseEntity.ok(response);
                }
                item.setCantidad(nuevaCantidad);
            } else {
                ProductoCatalogo catalogo = catalogoRepository.findById(oferta.getProductoCatalogoId())
                        .orElse(null);
                String unidadAb = catalogo != null && catalogo.getUnidadMedida() != null
                        ? catalogo.getUnidadMedida() : "unid";
                String vendedorNombre = oferta.getVendedor() != null ? oferta.getVendedor().getNombre() : "";

                ProductoCarrito nuevoItem = new ProductoCarrito(
                        oferta.getProductoCatalogoId(),
                        oferta.getId(),
                        oferta.getVendedor().getId(),
                        vendedorNombre,
                        oferta.getNombreProducto(),
                        oferta.getImagenUrl(),
                        oferta.getPrecio(),
                        cantidad,
                        unidadAb,
                        oferta.getCompraMinima()
                );
                carrito.getItems().add(nuevoItem);
            }

            carrito.recalcularTotal();
            carrito.setUpdatedAt(LocalDateTime.now());
            carritoRepository.save(carrito);

            response.put("success", true);
            response.put("mensaje", "¡" + oferta.getNombreProducto() + " agregado al carrito!");
            response.put("totalItems", carrito.getTotalArticulos());
            response.put("totalEstimado", carrito.getTotalEstimado());

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Error al agregar producto al carrito");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Actualiza la cantidad de un producto en el carrito
     */
    @PostMapping("/actualizar")
    public String actualizarCantidad(
            @RequestParam("ofertaId") String ofertaId,
            @RequestParam("cantidad") Double cantidad,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                return "redirect:/usuario/login";
            }

            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Carrito no encontrado"));

            if (cantidad <= 0) {
                // Eliminar item si la cantidad es 0 o menor
                carrito.getItems().removeIf(i -> ofertaId.equals(i.getOfertaId()));
                redirectAttributes.addFlashAttribute("mensaje",
                        "Producto eliminado del carrito");
            } else {
                // Verificar stock
                OfertaVendedor oferta = ofertaRepository.findById(ofertaId)
                        .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));

                if (oferta.getStock() < cantidad) {
                    redirectAttributes.addFlashAttribute("error",
                            "No hay suficiente stock disponible");
                    return "redirect:/carrito";
                }

                // Actualizar cantidad del item
                carrito.getItems().stream()
                        .filter(i -> ofertaId.equals(i.getOfertaId()))
                        .findFirst()
                        .ifPresent(i -> i.setCantidad(cantidad));

                redirectAttributes.addFlashAttribute("mensaje", "Cantidad actualizada");
            }

            carrito.recalcularTotal();
            carrito.setUpdatedAt(LocalDateTime.now());
            carritoRepository.save(carrito);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el carrito");
        }

        return "redirect:/carrito";
    }

    /**
     * Elimina un producto del carrito
     */
    @PostMapping("/eliminar")
    public String eliminarDelCarrito(
            @RequestParam("ofertaId") String ofertaId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                return "redirect:/usuario/login";
            }

            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Carrito no encontrado"));

            carrito.getItems().removeIf(i -> ofertaId.equals(i.getOfertaId()));
            carrito.recalcularTotal();
            carrito.setUpdatedAt(LocalDateTime.now());
            carritoRepository.save(carrito);

            redirectAttributes.addFlashAttribute("mensaje", "Producto eliminado del carrito");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar el producto");
        }

        return "redirect:/carrito";
    }
}