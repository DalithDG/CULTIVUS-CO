package com.example.demo.Controller;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.DireccionPedido;
import com.example.demo.Model.embebidos.ProductoCarrito;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.services.AppConfigService;
import com.example.demo.services.CatalogoService;
import com.example.demo.services.NotificacionService;
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
    private OfertaRepository ofertaRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private AppConfigService configService;

    @Autowired
    private CatalogoService catalogoService;

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

        // 1. Validar total mínimo usando parámetro de configuración (Global)
        double compraMinimaGlobal = configService.obtenerValorDouble("COMPRA_MINIMA_GLOBAL", 200000.0);
        if (carrito.getTotalEstimado() < compraMinimaGlobal) {
            redirectAttributes.addFlashAttribute("error",
                    "El total debe ser mayor a $" + String.format("%,.0f", compraMinimaGlobal));
            return "redirect:/carrito";
        }

        // 2. Validar compra mínima por OFERTA (Vendedor)
        for (ProductoCarrito item : carrito.getItems()) {
            if (item.getOfertaId() != null) {
                OfertaVendedor oferta = ofertaRepository.findById(item.getOfertaId()).orElse(null);
                if (oferta != null && item.getCantidad() < oferta.getCompraMinima()) {
                    String unidad = item.getUnidadAbreviatura() != null ? item.getUnidadAbreviatura() : "unid";
                    redirectAttributes.addFlashAttribute("error",
                            "El producto '" + item.getNombre() + "' requiere una compra mínima de " +
                            oferta.getCompraMinima() + " " + unidad + ". Tienes " + item.getCantidad());
                    return "redirect:/carrito";
                }
            }
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

            // Validar límite máximo de compra
            double limiteMaximo = configService.obtenerValorDouble("LIMITE_COMPRA_MAX", 5000000.0);
            if (carrito.getTotalEstimado() > limiteMaximo) {
                redirectAttributes.addFlashAttribute("error",
                        "El total de su compra excede el límite permitido de $" + String.format("%,.0f", limiteMaximo));
                return "redirect:/pago";
            }

            // Verificar stock y COMPRA MÍNIMA de todas las ofertas antes de procesar
            for (ProductoCarrito item : carrito.getItems()) {
                if (item.getOfertaId() != null) {
                    OfertaVendedor oferta = ofertaRepository.findById(item.getOfertaId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Oferta no encontrada para: " + item.getNombre()));

                    // Validar Stock
                    if (oferta.getStock() < item.getCantidad()) {
                        redirectAttributes.addFlashAttribute("error",
                                "El producto " + item.getNombre() + " no tiene suficiente stock");
                        return "redirect:/carrito";
                    }

                    // Validar Compra Mínima
                    if (item.getCantidad() < oferta.getCompraMinima()) {
                        String unidad = item.getUnidadAbreviatura() != null ? item.getUnidadAbreviatura() : "unid";
                        redirectAttributes.addFlashAttribute("error",
                                "No cumples con la compra mínima de " + oferta.getCompraMinima() + " " + unidad
                                        + " para el producto: " + item.getNombre());
                        return "redirect:/carrito";
                    }
                }
            }

            // Agrupar items por vendedor para crear subórdenes (Modelo Amazon)
            // Ahora usamos vendedorId del ProductoCarrito directamente
            java.util.Map<String, java.util.List<ProductoPedido>> itemsPorVendedor = new java.util.HashMap<>();
            java.util.Map<String, DatosVendedor> snapshotsVendedores = new java.util.HashMap<>();

            for (ProductoCarrito item : carrito.getItems()) {
                String vendedorIdLocal = item.getVendedorId();

                // Si vendedorId es null (carritos antiguos pre-migración), intentar obtenerlo de la oferta
                if (vendedorIdLocal == null && item.getOfertaId() != null) {
                    OfertaVendedor oferta = ofertaRepository.findById(item.getOfertaId()).orElse(null);
                    if (oferta != null && oferta.getVendedor() != null) {
                        vendedorIdLocal = oferta.getVendedor().getId();
                    }
                }

                if (vendedorIdLocal == null) {
                    throw new IllegalArgumentException("No se pudo identificar al vendedor del producto: " + item.getNombre());
                }

                String unidadAb = item.getUnidadAbreviatura() != null ? item.getUnidadAbreviatura() : "unid";
                ProductoPedido itemPedido = new ProductoPedido(
                        item.getOfertaId() != null ? item.getOfertaId() : item.getProductoId(),
                        item.getNombre(),
                        item.getImagenUrl(),
                        item.getPrecioUnitario(),
                        item.getCantidad(),
                        unidadAb);

                itemsPorVendedor.computeIfAbsent(vendedorIdLocal, k -> new java.util.ArrayList<>()).add(itemPedido);

                // Obtener snapshot del vendedor
                if (!snapshotsVendedores.containsKey(vendedorIdLocal)) {
                    if (item.getOfertaId() != null) {
                        OfertaVendedor oferta = ofertaRepository.findById(item.getOfertaId()).orElse(null);
                        if (oferta != null && oferta.getVendedor() != null) {
                            snapshotsVendedores.put(vendedorIdLocal, oferta.getVendedor());
                        }
                    }
                }
            }

            // Construir comprador embebido
            DatosComprador comprador = new DatosComprador(
                    usuario.getId(),
                    usuario.getNombre());

            // Construir dirección de entrega embebida
            DireccionPedido direccionEntrega = new DireccionPedido(
                    direccionEnvio != null ? direccionEnvio : "",
                    usuario.getUbicacion() != null ? usuario.getUbicacion().getCiudad() : "",
                    usuario.getUbicacion() != null ? usuario.getUbicacion().getDepartamento() : "");

            java.util.List<String> pedidosGenerados = new java.util.ArrayList<>();

            // Crear un pedido por cada vendedor
            for (java.util.Map.Entry<String, java.util.List<ProductoPedido>> entry : itemsPorVendedor.entrySet()) {
                String vendedorIdLocal = entry.getKey();
                java.util.List<ProductoPedido> itemsVendedor = entry.getValue();
                DatosVendedor snapshotVendedor = snapshotsVendedores.get(vendedorIdLocal);

                double subtotalVendedor = itemsVendedor.stream().mapToDouble(ProductoPedido::getSubtotal).sum();
                DatosPago datosPagoVendedor = new DatosPago(metodoPago, subtotalVendedor);

                Pedido pedidoVendedor = new Pedido(comprador, snapshotVendedor, direccionEntrega, itemsVendedor,
                        datosPagoVendedor);
                pedidoVendedor = pedidoRepository.save(pedidoVendedor);
                pedidosGenerados.add(pedidoVendedor.getId());

                // Notificar al Vendedor
                notificacionService.enviar(
                        vendedorIdLocal,
                        "¡Nueva Venta!",
                        "Has recibido un nuevo pedido #" + pedidoVendedor.getId() + " de " + usuario.getNombre(),
                        "SUCCESS");
            }

            // Notificar al Comprador
            notificacionService.enviar(
                    usuario.getId(),
                    "Pedido Confirmado",
                    "Tu compra ha sido procesada con éxito. Pedidos generados: " + pedidosGenerados.size(),
                    "SUCCESS");

            // Actualizar stock de cada oferta
            for (ProductoCarrito item : carrito.getItems()) {
                if (item.getOfertaId() != null) {
                    ofertaRepository.findById(item.getOfertaId()).ifPresent(oferta -> {
                        oferta.setStock(oferta.getStock() - item.getCantidad());
                        // Marcar como no disponible si se agotó el stock
                        if (oferta.getStock() <= 0) {
                            oferta.setDisponible(false);
                        }
                        ofertaRepository.save(oferta);

                        // Recalcular estadísticas del catálogo
                        catalogoService.actualizarEstadisticas(oferta.getProductoCatalogoId());
                    });
                }
            }

            // Limpiar carrito después del pago
            carritoRepository.delete(carrito);

            String mensajeConfirmacion = "¡Pago procesado exitosamente! ";
            if (pedidosGenerados.size() > 1) {
                mensajeConfirmacion += "Se dividió su compra en " + pedidosGenerados.size()
                        + " pedidos separados por vendedor.";
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