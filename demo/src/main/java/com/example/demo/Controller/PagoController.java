package com.example.demo.Controller;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.Producto;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DireccionPedido;
import com.example.demo.Model.embebidos.ProductoCarrito;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.ProductoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/pago")
public class PagoController {

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    /**
     * Muestra el formulario de pago
     */
    @GetMapping
    public String mostrarPago(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Debe iniciar sesión para realizar el pago");
            return "redirect:/usuario/login";
        }

        // Obtener carrito del usuario
        Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                .orElse(null);

        if (carrito == null || carrito.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Su carrito está vacío");
            return "redirect:/carrito";
        }

        // Validar total mínimo
        if (carrito.getTotalEstimado() < 200000) {
            redirectAttributes.addFlashAttribute("error",
                    "El total debe ser mayor a $200.000");
            return "redirect:/carrito";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("carrito", carrito);
        model.addAttribute("detalles", carrito.getItems());
        model.addAttribute("total", carrito.getTotalEstimado());

        return "pago";
    }

    /**
     * Procesa el pago y crea el pedido
     */
    @PostMapping("/procesar")
    public String procesarPago(
            // ✅ required = false para capturar cuando no se selecciona nada
            @RequestParam(value = "metodoPago", required = false) String metodoPago,
            @RequestParam(value = "direccionEnvio", required = false) String direccionEnvio,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error",
                        "Debe iniciar sesión para realizar el pago");
                return "redirect:/usuario/login";
            }

            // ✅ Validar que se seleccionó un método de pago
            if (metodoPago == null || metodoPago.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error",
                        "Debe seleccionar un método de pago");
                return "redirect:/pago";
            }

            // ✅ Validar que el método sea uno de los permitidos
            List<String> metodosValidos = List.of(
                    "TARJETA_CREDITO", "TARJETA_DEBITO", "TRANSFERENCIA", "EFECTIVO");
            if (!metodosValidos.contains(metodoPago)) {
                redirectAttributes.addFlashAttribute("error",
                        "Método de pago no válido");
                return "redirect:/pago";
            }

            // Obtener carrito
            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Carrito no encontrado"));

            if (carrito.getItems().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Su carrito está vacío");
                return "redirect:/carrito";
            }

            // Verificar stock de todos los productos antes de procesar
            for (ProductoCarrito item : carrito.getItems()) {
                Producto producto = productoRepository.findById(item.getProductoId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Producto no encontrado: " + item.getNombre()));
                if (producto.getStock() < item.getCantidad()) {
                    redirectAttributes.addFlashAttribute("error",
                            "El producto " + item.getNombre() + " no tiene suficiente stock");
                    return "redirect:/carrito";
                }
            }

            // Agrupar items por vendedor para crear subórdenes (Modelo Amazon)
            java.util.Map<String, java.util.List<ProductoPedido>> itemsPorVendedor = new java.util.HashMap<>();

            for (ProductoCarrito item : carrito.getItems()) {
                Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
                String vendedorIdLocal = producto.getVendedor().getId();
                
                ProductoPedido itemPedido = new ProductoPedido(
                        item.getProductoId(),
                        item.getNombre(),
                        item.getImagenUrl(),
                        item.getPrecioUnitario(),
                        item.getCantidad()
                );
                
                itemsPorVendedor.computeIfAbsent(vendedorIdLocal, k -> new java.util.ArrayList<>()).add(itemPedido);
            }

            // Construir comprador embebido
            DatosComprador comprador = new DatosComprador(
                    usuario.getId(),
                    usuario.getNombre()
            );

            // Construir dirección de entrega embebida
            DireccionPedido direccionEntrega = new DireccionPedido(
                    direccionEnvio != null ? direccionEnvio : "",
                    usuario.getUbicacion() != null ? usuario.getUbicacion().getCiudad() : "",
                    usuario.getUbicacion() != null ? usuario.getUbicacion().getDepartamento() : ""
            );

            java.util.List<String> pedidosGenerados = new java.util.ArrayList<>();

            // Crear un pedido por cada vendedor
            for (java.util.Map.Entry<String, java.util.List<ProductoPedido>> entry : itemsPorVendedor.entrySet()) {
                String vendedorIdLocal = entry.getKey();
                java.util.List<ProductoPedido> itemsVendedor = entry.getValue();
                
                double subtotalVendedor = itemsVendedor.stream().mapToDouble(ProductoPedido::getSubtotal).sum();
                DatosPago datosPagoVendedor = new DatosPago(metodoPago, subtotalVendedor);
                
                Pedido pedidoVendedor = new Pedido(comprador, vendedorIdLocal, direccionEntrega, itemsVendedor, datosPagoVendedor);
                pedidoVendedor = pedidoRepository.save(pedidoVendedor);
                pedidosGenerados.add(pedidoVendedor.getId());
            }

            // Actualizar stock de cada producto
            for (ProductoCarrito item : carrito.getItems()) {
                productoRepository.findById(item.getProductoId()).ifPresent(producto -> {
                    producto.setStock(producto.getStock() - item.getCantidad());
                    // Marcar como no disponible si se agotó el stock
                    if (producto.getStock() == 0) {
                        producto.setDisponible(false);
                    }
                    productoRepository.save(producto);
                });
            }

            // Limpiar carrito después del pago
            carritoRepository.delete(carrito);

            String mensajeConfirmacion = "¡Pago procesado exitosamente! ";
            if (pedidosGenerados.size() > 1) {
                mensajeConfirmacion += "Se dividió su compra en " + pedidosGenerados.size() + " pedidos separados por vendedor.";
            } else {
                mensajeConfirmacion += "Pedido #" + pedidosGenerados.get(0);
            }

            redirectAttributes.addFlashAttribute("mensaje", mensajeConfirmacion);
            return "redirect:/pago/confirmacion/" + pedidosGenerados.get(0);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al procesar el pago: " + e.getMessage());
            return "redirect:/pago";
        }
    }

    /**
     * Muestra la confirmación del pago
     */
    @GetMapping("/confirmacion/{pedidoId}")
    public String mostrarConfirmacion(@PathVariable String pedidoId,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            return "redirect:/usuario/login";
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        // Verificar que el pedido pertenece al usuario
        if (!pedido.getComprador().getId().equals(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tiene permiso para ver este pedido");
            return "redirect:/";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("pedido", pedido);
        model.addAttribute("detalles", pedido.getItems());
        model.addAttribute("pago", pedido.getPago());

        return "confirmacion-pago";
    }
}