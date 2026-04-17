package com.example.demo.Controller;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.Producto;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.ProductoCarrito;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.ProductoRepository;
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
    private ProductoRepository productoRepository;

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
     * Agrega un producto al carrito
     */
    @PostMapping("/agregar")
    public String agregarAlCarrito(
            @RequestParam("productoId") String productoId,
            @RequestParam(value = "cantidad", defaultValue = "1") int cantidad,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error",
                        "Debe iniciar sesión para agregar productos al carrito");
                return "redirect:/usuario/login";
            }

            Producto producto = productoRepository.findById(productoId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

            // Verificar stock
            if (producto.getStock() < cantidad) {
                redirectAttributes.addFlashAttribute("error",
                        "No hay suficiente stock disponible");
                return "redirect:/";
            }

            // Obtener o crear carrito
            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseGet(() -> new Carrito(usuario.getId()));

            // Verificar si el producto ya está en el carrito
            Optional<ProductoCarrito> itemExistente = carrito.getItems().stream()
                    .filter(i -> i.getProductoId().equals(productoId))
                    .findFirst();

            if (itemExistente.isPresent()) {
                // Actualizar cantidad del item existente
                ProductoCarrito item = itemExistente.get();
                int nuevaCantidad = item.getCantidad() + cantidad;

                if (producto.getStock() < nuevaCantidad) {
                    redirectAttributes.addFlashAttribute("error",
                            "No hay suficiente stock disponible");
                    // Bug #2 corregido: era redirect:/ (inicio), debe ser redirect:/carrito
                    return "redirect:/carrito";
                }

                item.setCantidad(nuevaCantidad);
            } else {
                // Agregar nuevo item al carrito
                ProductoCarrito nuevoItem = new ProductoCarrito(
                        producto.getId(),
                        producto.getNombre(),
                        producto.getImagenUrl(),
                        producto.getPrecio(),
                        cantidad
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
     * Es llamado por carrito-ajax.js desde cualquier página.
     */
    @PostMapping("/agregar-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> agregarAlCarritoAjax(
            @RequestParam("productoId") String productoId,
            @RequestParam(value = "cantidad", defaultValue = "1") int cantidad,
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

            Producto producto = productoRepository.findById(productoId)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

            if (producto.getStock() < cantidad) {
                response.put("success", false);
                response.put("error", "No hay suficiente stock disponible");
                return ResponseEntity.ok(response);
            }

            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseGet(() -> new Carrito(usuario.getId()));

            Optional<ProductoCarrito> itemExistente = carrito.getItems().stream()
                    .filter(i -> i.getProductoId().equals(productoId))
                    .findFirst();

            if (itemExistente.isPresent()) {
                ProductoCarrito item = itemExistente.get();
                int nuevaCantidad = item.getCantidad() + cantidad;
                if (producto.getStock() < nuevaCantidad) {
                    response.put("success", false);
                    response.put("error", "Solo quedan " + producto.getStock() + " unidades disponibles");
                    return ResponseEntity.ok(response);
                }
                item.setCantidad(nuevaCantidad);
            } else {
                ProductoCarrito nuevoItem = new ProductoCarrito(
                        producto.getId(),
                        producto.getNombre(),
                        producto.getImagenUrl(),
                        producto.getPrecio(),
                        cantidad
                );
                carrito.getItems().add(nuevoItem);
            }

            carrito.recalcularTotal();
            carrito.setUpdatedAt(LocalDateTime.now());
            carritoRepository.save(carrito);

            response.put("success", true);
            response.put("mensaje", "¡" + producto.getNombre() + " agregado al carrito!");
            response.put("totalItems", carrito.getItems().size());
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
            @RequestParam("productoId") String productoId,
            @RequestParam("cantidad") int cantidad,
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
                carrito.getItems().removeIf(i -> i.getProductoId().equals(productoId));
                redirectAttributes.addFlashAttribute("mensaje",
                        "Producto eliminado del carrito");
            } else {
                // Verificar stock
                Producto producto = productoRepository.findById(productoId)
                        .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

                if (producto.getStock() < cantidad) {
                    redirectAttributes.addFlashAttribute("error",
                            "No hay suficiente stock disponible");
                    return "redirect:/carrito";
                }

                // Actualizar cantidad del item
                carrito.getItems().stream()
                        .filter(i -> i.getProductoId().equals(productoId))
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
            @RequestParam("productoId") String productoId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                return "redirect:/usuario/login";
            }

            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Carrito no encontrado"));

            carrito.getItems().removeIf(i -> i.getProductoId().equals(productoId));
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