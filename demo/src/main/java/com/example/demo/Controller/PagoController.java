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
import com.example.demo.services.PagoConfirmacionService;
import com.example.demo.services.WompiApiClient;
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

    @Autowired
    private com.example.demo.services.WompiService wompiService;

    @Autowired
    private WompiApiClient wompiApiClient;

    @Autowired
    private PagoConfirmacionService pagoConfirmacionService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PagoController.class);

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

        // Cálculo seguro de Wompi en el servidor
        double subtotal = carrito.getTotalEstimado();
        double costoEnvio = wompiService.obtenerCostoEnvioProvisional();
        double totalConEnvio = subtotal + costoEnvio;
        long amountInCents = wompiService.convertirACentavos(totalConEnvio);
        String reference = wompiService.generarReferenciaUnica();
        String currency = wompiService.getCurrency();
        String signatureIntegrity = "";
        try {
            signatureIntegrity = wompiService.generarFirmaIntegridad(reference, amountInCents, currency);
        } catch (IllegalStateException ex) {
            // Wompi sin configurar: la pasarela simulada sigue disponible
            model.addAttribute("wompiError", "Wompi no está configurado en el servidor");
        }

        model.addAttribute("amountInCents", amountInCents);
        model.addAttribute("reference", reference);
        model.addAttribute("signatureIntegrity", signatureIntegrity);
        model.addAttribute("wompiPublicKey", wompiService.getPublicKey());
        model.addAttribute("currency", currency);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("costoEnvio", costoEnvio);

        model.addAttribute("usuario", usuario);
        model.addAttribute("carrito", carrito);
        model.addAttribute("detalles", carrito.getItems());
        model.addAttribute("total", totalConEnvio);

        return "comprador/pago";
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

        return "comprador/confirmacion-pago";
    }

    /**
     * API: Pre-registra el pedido en estado PENDIENTE y devuelve datos firmados para el Widget de Wompi.
     * Se crea un pedido por vendedor, todos con la misma referencia de pago.
     */
    @PostMapping("/iniciar-wompi")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> iniciarPagoWompi(
            @RequestBody(required = false) java.util.Map<String, String> payload,
            HttpSession session) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                return org.springframework.http.ResponseEntity.status(401).body(
                        java.util.Map.of("ok", false, "error", "Debe iniciar sesión para realizar el pago"));
            }

            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Carrito no encontrado"));

            if (carrito.getItems().isEmpty()) {
                return org.springframework.http.ResponseEntity.badRequest().body(
                        java.util.Map.of("ok", false, "error", "Su carrito está vacío"));
            }

            double limiteMaximo = configService.obtenerValorDouble("LIMITE_COMPRA_MAX", 5000000.0);
            if (carrito.getTotalEstimado() > limiteMaximo) {
                return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("ok", false,
                        "error", "El total de su compra excede el límite permitido de $"
                                + String.format("%,.0f", limiteMaximo)));
            }

            // Verificar stock antes de registrar el pedido
            for (ProductoCarrito item : carrito.getItems()) {
                if (item.getOfertaId() != null) {
                    OfertaVendedor oferta = ofertaRepository.findById(item.getOfertaId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Oferta no encontrada para: " + item.getNombre()));
                    if (oferta.getStock() < item.getCantidad()) {
                        return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("ok", false,
                                "error", "El producto " + item.getNombre() + " no tiene suficiente stock"));
                    }
                }
            }

            // Cálculo seguro del total en centavos
            double subtotal = carrito.getTotalEstimado();
            double costoEnvio = wompiService.obtenerCostoEnvioProvisional();
            double totalConEnvio = subtotal + costoEnvio;
            long amountInCents = wompiService.convertirACentavos(totalConEnvio);

            // Reutilizar referencia del frontend o generar una nueva
            String reference = (payload != null
                    && payload.containsKey("reference")
                    && payload.get("reference") != null
                    && !payload.get("reference").trim().isEmpty())
                            ? payload.get("reference").trim()
                            : wompiService.generarReferenciaUnica();

            // Si la referencia ya existe y es de otro comprador, no se reutiliza
            List<Pedido> existentes = pedidoRepository.findByPago_Referencia(reference);
            boolean pedidosPropios = !existentes.isEmpty() && existentes.stream().allMatch(
                    p -> p.getComprador() != null && usuario.getId().equals(p.getComprador().getId()));
            if (!existentes.isEmpty() && !pedidosPropios) {
                reference = wompiService.generarReferenciaUnica();
                existentes = new ArrayList<>();
            }

            String currency = wompiService.getCurrency();
            String signatureIntegrity = wompiService.generarFirmaIntegridad(reference, amountInCents, currency);
            String direccionEnvio = (payload != null && payload.get("direccionEnvio") != null)
                    ? payload.get("direccionEnvio") : "";

            // Crear pedidos PENDIENTE (uno por vendedor) solo si aún no existen para esta referencia
            if (existentes.isEmpty()) {
                java.util.Map<String, List<ProductoPedido>> itemsPorVendedor = new java.util.HashMap<>();
                java.util.Map<String, DatosVendedor> snapshotsVendedores = new java.util.HashMap<>();

                for (ProductoCarrito item : carrito.getItems()) {
                    String vendedorIdLocal = item.getVendedorId();
                    OfertaVendedor oferta = item.getOfertaId() != null
                            ? ofertaRepository.findById(item.getOfertaId()).orElse(null)
                            : null;
                    if (vendedorIdLocal == null && oferta != null && oferta.getVendedor() != null) {
                        vendedorIdLocal = oferta.getVendedor().getId();
                    }
                    if (vendedorIdLocal == null) {
                        throw new IllegalArgumentException(
                                "No se pudo identificar al vendedor del producto: " + item.getNombre());
                    }

                    String unidadAb = item.getUnidadAbreviatura() != null ? item.getUnidadAbreviatura() : "unid";
                    ProductoPedido itemPedido = new ProductoPedido(
                            item.getOfertaId() != null ? item.getOfertaId() : item.getProductoId(),
                            item.getNombre(),
                            item.getImagenUrl(),
                            item.getPrecioUnitario(),
                            item.getCantidad(),
                            unidadAb);
                    itemsPorVendedor.computeIfAbsent(vendedorIdLocal, k -> new ArrayList<>()).add(itemPedido);

                    if (!snapshotsVendedores.containsKey(vendedorIdLocal) && oferta != null
                            && oferta.getVendedor() != null) {
                        snapshotsVendedores.put(vendedorIdLocal, oferta.getVendedor());
                    }
                }

                DatosComprador comprador = new DatosComprador(usuario.getId(), usuario.getNombre());
                DireccionPedido direccion = new DireccionPedido(
                        direccionEnvio,
                        usuario.getUbicacion() != null ? usuario.getUbicacion().getCiudad() : "",
                        usuario.getUbicacion() != null ? usuario.getUbicacion().getDepartamento() : "");

                for (java.util.Map.Entry<String, List<ProductoPedido>> entry : itemsPorVendedor.entrySet()) {
                    double subtotalVendedor = entry.getValue().stream().mapToDouble(ProductoPedido::getSubtotal).sum();
                    DatosPago datosPago = new DatosPago("WOMPI", subtotalVendedor);
                    datosPago.setEstado("PENDIENTE");
                    datosPago.setReferencia(reference);
                    datosPago.setMontoCentavosFirmado(amountInCents);

                    Pedido pedido = new Pedido(comprador, snapshotsVendedores.get(entry.getKey()), direccion,
                            entry.getValue(), datosPago);
                    pedido.setEstado("PENDIENTE");
                    pedidoRepository.save(pedido);
                }
            } else {
                // Referencia reutilizada: el monto firmado vigente es el de esta solicitud
                for (Pedido existente : existentes) {
                    DatosPago pagoExistente = existente.getPago();
                    if (pagoExistente != null && !PagoConfirmacionService.esFinal(pagoExistente.getEstado())) {
                        pagoExistente.setMontoCentavosFirmado(amountInCents);
                        pedidoRepository.save(existente);
                    }
                }
            }

            return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                    "ok", true,
                    "reference", reference,
                    "amountInCents", amountInCents,
                    "currency", currency,
                    "signatureIntegrity", signatureIntegrity,
                    "publicKey", wompiService.getPublicKey()));

        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(
                    java.util.Map.of("ok", false, "error", "Error al iniciar pago: " + e.getMessage()));
        }
    }

    /**
     * Retorno tras interacción con el Widget de Wompi.
     */
    @GetMapping("/resultado")
    public String resultadoPago(
            @RequestParam(value = "reference", required = false) String reference,
            @RequestParam(value = "id", required = false) String transactionId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            return "redirect:/usuario/login";
        }

        List<Pedido> pedidos = (reference != null && !reference.trim().isEmpty())
                ? pedidoRepository.findByPago_Referencia(reference.trim())
                : java.util.Collections.emptyList();

        // Solo pedidos que pertenezcan al usuario autenticado
        pedidos = pedidos.stream()
                .filter(p -> p.getComprador() != null && usuario.getId().equals(p.getComprador().getId()))
                .collect(java.util.stream.Collectors.toList());

        if (pedidos.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "No se encontró el pedido correspondiente a la transacción.");
            return "redirect:/";
        }

        String referenciaPago = reference.trim();

        // Respaldo del webhook: NUNCA se confia en el id de la URL. Solo se usa para consultar
        // la transaccion a Wompi, y se exige que la reference de la respuesta coincida.
        java.util.Optional<WompiApiClient.TransaccionWompi> transaccion = java.util.Optional.empty();
        if (WompiApiClient.esIdValido(transactionId)) {
            transaccion = wompiApiClient.consultarTransaccion(transactionId.trim());
        }
        if (transaccion.isPresent()) {
            WompiApiClient.TransaccionWompi tx = transaccion.get();
            if (referenciaPago.equals(tx.reference())) {
                pagoConfirmacionService.aplicarResultado(
                        referenciaPago, tx.id(), tx.status(), tx.amountInCents());
            } else {
                log.warn("La reference de la transaccion consultada no coincide con la del retorno");
                transaccion = java.util.Optional.empty();
            }
        }

        // Se relee el estado real (puede haberlo actualizado ya el webhook)
        pedidos = pedidoRepository.findByPago_Referencia(referenciaPago).stream()
                .filter(p -> p.getComprador() != null && usuario.getId().equals(p.getComprador().getId()))
                .collect(java.util.stream.Collectors.toList());
        if (pedidos.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "No se encontró el pedido correspondiente a la transacción.");
            return "redirect:/";
        }

        boolean todosCompletados = pedidos.stream().allMatch(
                p -> p.getPago() != null && PagoConfirmacionService.COMPLETADO.equals(p.getPago().getEstado()));
        boolean todosFallidos = pedidos.stream().allMatch(
                p -> p.getPago() != null && PagoConfirmacionService.FALLIDO.equals(p.getPago().getEstado()));

        if (todosCompletados) {
            // Pago aprobado: solo ahora se limpia el carrito
            carritoRepository.findByUsuarioId(usuario.getId()).ifPresent(carritoRepository::delete);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Pago aprobado. Pedido #" + pedidos.get(0).getId());
            return "redirect:/pago/confirmacion/" + pedidos.get(0).getId();
        }

        // Pendiente, rechazado o sin verificar: el carrito se conserva
        if (todosFallidos) {
            redirectAttributes.addFlashAttribute("error",
                    "Tu pago fue rechazado. Conservamos tu carrito para que puedas intentarlo de nuevo.");
        } else if (transaccion.isPresent() && "PENDING".equalsIgnoreCase(transaccion.get().status())) {
            redirectAttributes.addFlashAttribute("mensaje",
                    "Tu pago está pendiente de confirmación. Conservamos tu carrito; te avisaremos cuando se confirme.");
        } else {
            redirectAttributes.addFlashAttribute("mensaje",
                    "Pago en verificación: aún no pudimos confirmar el estado con Wompi. "
                            + "Conservamos tu carrito; revisa tus pedidos en unos minutos.");
        }
        return "redirect:/carrito";
    }

    /**
     * Página de prueba mínima del widget de Wompi.
     */
    @GetMapping("/test-wompi")
    public String mostrarTestWompi(Model model) {
        double montoCop = 10000.0;
        long amountInCents = wompiService.convertirACentavos(montoCop);
        String reference = wompiService.generarReferenciaUnica();
        String currency = wompiService.getCurrency();
        String signatureIntegrity = wompiService.generarFirmaIntegridad(reference, amountInCents, currency);

        model.addAttribute("publicKey", wompiService.getPublicKey());
        model.addAttribute("reference", reference);
        model.addAttribute("montoCop", montoCop);
        model.addAttribute("amountInCents", amountInCents);
        model.addAttribute("currency", currency);
        model.addAttribute("signatureIntegrity", signatureIntegrity);
        return "test-wompi";
    }
}