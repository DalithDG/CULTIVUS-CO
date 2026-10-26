package com.example.demo.Controller;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.Compra;
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
import com.example.demo.repository.CompraRepository;
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
    private CompraRepository compraRepository;

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

    @Autowired
    private com.example.demo.services.CalculoEnvioService calculoEnvioService;

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

        // 2. Validar compra mínima y STOCK por OFERTA (US-21 Item 7)
        for (ProductoCarrito item : carrito.getItems()) {
            if (item.getOfertaId() != null) {
                OfertaVendedor oferta = ofertaRepository.findById(item.getOfertaId()).orElse(null);
                if (oferta == null || !oferta.isDisponible()) {
                    redirectAttributes.addFlashAttribute("error",
                            "El producto '" + item.getNombre() + "' ya no está disponible.");
                    return "redirect:/carrito";
                }
                // Validar Compra Mínima
                if (item.getCantidad() < oferta.getCompraMinima()) {
                    String unidad = item.getUnidadAbreviatura() != null ? item.getUnidadAbreviatura() : "unid";
                    redirectAttributes.addFlashAttribute("error",
                            "El producto '" + item.getNombre() + "' requiere una compra mínima de " +
                            oferta.getCompraMinima() + " " + unidad + ". Tienes " + item.getCantidad());
                    return "redirect:/carrito";
                }
                // Validar Stock disponible
                double stockDisponible = oferta.getStock() != null ? oferta.getStock() : 0.0;
                if (stockDisponible < item.getCantidad()) {
                    String unidad = item.getUnidadAbreviatura() != null ? item.getUnidadAbreviatura() : "unid";
                    redirectAttributes.addFlashAttribute("error",
                            "El producto '" + item.getNombre() + "' no tiene suficiente stock (disponible: " +
                            (stockDisponible % 1 == 0 ? String.format("%.0f", stockDisponible) : stockDisponible) +
                            " " + unidad + ", en tu carrito: " +
                            (item.getCantidad() % 1 == 0 ? String.format("%.0f", item.getCantidad()) : item.getCantidad()) +
                            " " + unidad + ").");
                    return "redirect:/carrito";
                }
            }
        }

        // Cálculo seguro de envío por zonas y Wompi en el servidor
        double subtotal = carrito.getTotalEstimado();
        String ciudadDefault = (usuario.getUbicacion() != null && usuario.getUbicacion().getCiudad() != null && !usuario.getUbicacion().getCiudad().isBlank())
                ? usuario.getUbicacion().getCiudad().trim()
                : "Cartagena";

        com.example.demo.services.CalculoEnvioService.ResultadoCalculoEnvio calculoEnvio =
                calculoEnvioService.calcularTarifaCarrito(carrito, ciudadDefault);
        double costoEnvio = calculoEnvio.getTotalCop();
        double totalConEnvio = subtotal + costoEnvio;
        long amountInCents = wompiService.convertirACentavos(subtotal) + calculoEnvio.totalCentavos();

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
        model.addAttribute("desgloseEnvio", calculoEnvio.desglose());
        model.addAttribute("ciudadPreseleccionada", ciudadDefault);

        model.addAttribute("usuario", usuario);
        model.addAttribute("carrito", carrito);
        model.addAttribute("detalles", carrito.getItems());
        model.addAttribute("total", totalConEnvio);

        return "comprador/pago";
    }

    /**
     * Muestra la confirmación del pago (US-21 Item 8)
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
        if (pedido.getComprador() == null || !usuario.getId().equals(pedido.getComprador().getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tiene permiso para ver este pedido");
            return "redirect:/";
        }

        // Cargar Compra agrupada y todos los sub-pedidos
        Compra compra = null;
        if (pedido.getCompraId() != null) {
            compra = compraRepository.findById(pedido.getCompraId()).orElse(null);
        }
        if (compra == null && pedido.getPago() != null && pedido.getPago().getReferencia() != null) {
            compra = compraRepository.findByReferenciaPago(pedido.getPago().getReferencia()).orElse(null);
        }

        List<Pedido> subPedidos = new ArrayList<>();
        if (compra != null && compra.getId() != null) {
            subPedidos = pedidoRepository.findByCompraId(compra.getId());
            if (subPedidos.isEmpty() && compra.getPedidoIds() != null && !compra.getPedidoIds().isEmpty()) {
                subPedidos = pedidoRepository.findAllById(compra.getPedidoIds());
            }
        }
        if (subPedidos.isEmpty() && pedido.getPago() != null && pedido.getPago().getReferencia() != null) {
            subPedidos = pedidoRepository.findByPago_Referencia(pedido.getPago().getReferencia());
        }
        if (subPedidos.isEmpty()) {
            subPedidos = List.of(pedido);
        }

        double subtotalProductos = subPedidos.stream()
                .mapToDouble(p -> p.getTotal() != null ? p.getTotal() : 0.0)
                .sum();

        double totalCobrado = subtotalProductos;
        if (compra != null && compra.getTotalCentavos() != null && compra.getTotalCentavos() > 0) {
            totalCobrado = compra.getTotalCentavos() / 100.0;
        } else if (pedido.getPago() != null && pedido.getPago().getMontoCentavosFirmado() != null && pedido.getPago().getMontoCentavosFirmado() > 0) {
            totalCobrado = pedido.getPago().getMontoCentavosFirmado() / 100.0;
        } else if (pedido.getPago() != null && pedido.getPago().getMonto() != null) {
            totalCobrado = pedido.getPago().getMonto();
        }
        double costoEnvio = Math.max(0.0, totalCobrado - subtotalProductos);

        model.addAttribute("usuario", usuario);
        model.addAttribute("pedido", pedido);
        model.addAttribute("compra", compra);
        model.addAttribute("subPedidos", subPedidos);
        model.addAttribute("detalles", pedido.getItems());
        model.addAttribute("pago", pedido.getPago());
        model.addAttribute("subtotalProductos", subtotalProductos);
        model.addAttribute("costoEnvio", costoEnvio);
        model.addAttribute("totalCobrado", totalCobrado);

        return "comprador/confirmacion-pago";
    }

    /**
     * API: Pre-registra la Compra y sus sub-pedidos en estado PENDIENTE, y devuelve datos firmados para el Widget de Wompi.
     * US-21 Item 5: Crea la entidad Compra junto a los pedidos PENDIENTE; cada pedido guarda su compraId.
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

            String receptor = payload != null ? payload.getOrDefault("receptor", "").trim() : "";
            String direccionTexto = payload != null ? payload.getOrDefault("direccion", "").trim() : "";
            String ciudad = payload != null ? payload.getOrDefault("ciudad", "").trim() : "";
            String departamento = payload != null ? payload.getOrDefault("departamento", "Bolívar").trim() : "Bolívar";
            String telefono = payload != null ? payload.getOrDefault("telefono", "").trim() : "";
            String notas = payload != null ? payload.getOrDefault("notas", "").trim() : "";
            String codigoPostal = payload != null ? payload.getOrDefault("codigoPostal", "").trim() : "";

            if (direccionTexto.isEmpty() && payload != null && payload.get("direccionEnvio") != null) {
                direccionTexto = payload.get("direccionEnvio").trim();
            }

            StringBuilder dirBuilder = new StringBuilder();
            if (!direccionTexto.isEmpty()) {
                dirBuilder.append(direccionTexto);
            }
            if (!receptor.isEmpty()) {
                dirBuilder.append(" | Recibe: ").append(receptor);
            }
            if (!telefono.isEmpty()) {
                dirBuilder.append(" (Tel: ").append(telefono).append(")");
            }
            if (!codigoPostal.isEmpty()) {
                dirBuilder.append(" | CP: ").append(codigoPostal);
            }
            if (!notas.isEmpty()) {
                dirBuilder.append(" | Indicaciones: ").append(notas);
            }
            String direccionFinal = dirBuilder.length() > 0 ? dirBuilder.toString() : "Entrega en domicilio";
            String ciudadFinal = !ciudad.isEmpty() ? ciudad : (usuario.getUbicacion() != null && usuario.getUbicacion().getCiudad() != null ? usuario.getUbicacion().getCiudad() : "Cartagena");
            String deptoFinal = !departamento.isEmpty() ? departamento : "Bolívar";

            // Cálculo seguro del total en centavos enteros usando la ciudad de entrega final
            double subtotal = carrito.getTotalEstimado();
            long subtotalCentavos = wompiService.convertirACentavos(subtotal);

            com.example.demo.services.CalculoEnvioService.ResultadoCalculoEnvio calculoEnvio =
                    calculoEnvioService.calcularTarifaCarrito(carrito, ciudadFinal);
            long envioCentavos = calculoEnvio.totalCentavos();
            long amountInCents = subtotalCentavos + envioCentavos;

            // Reutilizar referencia del frontend o generar una nueva
            String reference = (payload != null
                    && payload.containsKey("reference")
                    && payload.get("reference") != null
                    && !payload.get("reference").trim().isEmpty())
                            ? payload.get("reference").trim()
                            : wompiService.generarReferenciaUnica();

            // Si la referencia ya existe y es de otro comprador, generar una nueva
            List<Pedido> existentes = pedidoRepository.findByPago_Referencia(reference);
            java.util.Optional<Compra> optCompraExistente = compraRepository.findByReferenciaPago(reference);

            boolean pedidosPropios = existentes.isEmpty() || existentes.stream().allMatch(
                    p -> p.getComprador() != null && usuario.getId().equals(p.getComprador().getId()));
            boolean compraPropia = optCompraExistente.isEmpty() || (optCompraExistente.get().getComprador() != null
                    && usuario.getId().equals(optCompraExistente.get().getComprador().getId()));

            if (!pedidosPropios || !compraPropia) {
                reference = wompiService.generarReferenciaUnica();
                existentes = new ArrayList<>();
                optCompraExistente = java.util.Optional.empty();
            }

            String currency = wompiService.getCurrency();
            String signatureIntegrity = wompiService.generarFirmaIntegridad(reference, amountInCents, currency);

            // Crear Compra y sus sub-pedidos PENDIENTE si aún no existen para esta referencia
            if (existentes.isEmpty() && optCompraExistente.isEmpty()) {
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
                        direccionFinal,
                        ciudadFinal,
                        deptoFinal);

                // 1. Crear y persistir Compra
                Compra compra = new Compra(comprador, reference, subtotalCentavos, envioCentavos);
                compra.setTotalCentavos(amountInCents);
                compra = compraRepository.save(compra);

                // 2. Crear un sub-pedido por vendedor, vinculando su compraId y envioCentavos
                List<String> pedidoIdsGenerados = new ArrayList<>();
                for (java.util.Map.Entry<String, List<ProductoPedido>> entry : itemsPorVendedor.entrySet()) {
                    String vendedorId = entry.getKey();
                    double subtotalVendedor = entry.getValue().stream().mapToDouble(ProductoPedido::getSubtotal).sum();
                    long subtotalVendedorCentavos = wompiService.convertirACentavos(subtotalVendedor);
                    long envioVendedorCentavos = calculoEnvio.obtenerTarifaVendedor(vendedorId);

                    DatosPago datosPago = new DatosPago("WOMPI", subtotalVendedor);
                    datosPago.setEstado("PENDIENTE");
                    datosPago.setReferencia(reference);
                    datosPago.setMontoCentavosFirmado(amountInCents);

                    Pedido pedido = new Pedido(comprador, snapshotsVendedores.get(vendedorId), direccion,
                            entry.getValue(), datosPago);
                    pedido.setCompraId(compra.getId());
                    pedido.setSubtotalCentavos(subtotalVendedorCentavos);
                    pedido.setEnvioCentavos(envioVendedorCentavos);
                    pedido.setTarifaEnvioCentavos(envioVendedorCentavos);
                    // Snapshot del tramo de envío
                    for (com.example.demo.services.CalculoEnvioService.DesgloseEnvioVendedor dg : calculoEnvio.desglose()) {
                        if (vendedorId.equals(dg.vendedorId())) {
                            pedido.setCiudadOrigenEnvio(dg.ciudadOrigen());
                            pedido.setCiudadDestinoEnvio(dg.ciudadDestino());
                            pedido.setZonaOrigenEnvio(dg.zonaOrigen());
                            pedido.setZonaDestinoEnvio(dg.zonaDestino());
                            break;
                        }
                    }
                    pedido.setEstado("PENDIENTE");
                    pedido = pedidoRepository.save(pedido);

                    pedidoIdsGenerados.add(pedido.getId());
                }

                compra.setPedidoIds(pedidoIdsGenerados);
                compraRepository.save(compra);

            } else {
                // Referencia reutilizada: actualizar monto vigente en Compra y pedidos
                if (optCompraExistente.isPresent()) {
                    Compra compra = optCompraExistente.get();
                    if (!compra.esFinal()) {
                        compra.setSubtotalCentavos(subtotalCentavos);
                        compra.setEnvioCentavos(envioCentavos);
                        compra.setTotalCentavos(amountInCents);
                        compraRepository.save(compra);
                    }
                }
                for (Pedido existente : existentes) {
                    DatosPago pagoExistente = existente.getPago();
                    if (pagoExistente != null && !PagoConfirmacionService.esFinal(pagoExistente.getEstado())) {
                        pagoExistente.setMontoCentavosFirmado(amountInCents);
                    }
                    if (existente.getVendedor() != null) {
                        existente.setEnvioCentavos(calculoEnvio.obtenerTarifaVendedor(existente.getVendedor().getId()));
                    }
                    pedidoRepository.save(existente);
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
     * API: Recalcula dinámicamente el costo de envío al cambiar la ciudad de entrega en el formulario.
     */
    @GetMapping("/calcular-envio")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> calcularEnvio(
            @RequestParam(value = "ciudad", required = false) String ciudad,
            HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            return org.springframework.http.ResponseEntity.status(401).body(
                    java.util.Map.of("ok", false, "error", "No autenticado"));
        }
        Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (carrito == null) {
            return org.springframework.http.ResponseEntity.badRequest().body(
                    java.util.Map.of("ok", false, "error", "Carrito no encontrado"));
        }
        String ciudadFinal = (ciudad != null && !ciudad.isBlank()) ? ciudad.trim() : "Cartagena";
        com.example.demo.services.CalculoEnvioService.ResultadoCalculoEnvio res =
                calculoEnvioService.calcularTarifaCarrito(carrito, ciudadFinal);
        double subtotal = carrito.getTotalEstimado();
        double costoEnvio = res.getTotalCop();
        double total = subtotal + costoEnvio;
        long totalCentavos = wompiService.convertirACentavos(subtotal) + res.totalCentavos();

        java.util.List<java.util.Map<String, Object>> desgloseJson = new java.util.ArrayList<>();
        for (com.example.demo.services.CalculoEnvioService.DesgloseEnvioVendedor d : res.desglose()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("vendedorId", d.vendedorId());
            m.put("vendedorNombre", d.vendedorNombre());
            m.put("ciudadOrigen", d.ciudadOrigen());
            m.put("zonaOrigen", d.zonaOrigen());
            m.put("ciudadDestino", d.ciudadDestino());
            m.put("zonaDestino", d.zonaDestino());
            m.put("tarifaCentavos", d.tarifaCentavos());
            m.put("tarifaPorDefecto", d.tarifaPorDefecto());
            desgloseJson.add(m);
        }
        return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                "ok", true,
                "costoEnvio", costoEnvio,
                "total", total,
                "totalCentavos", totalCentavos,
                "desglose", desgloseJson
        ));
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
     * Cancela la compra pre-registrada cuando el comprador cierra el widget de Wompi sin
     * crear una transacción. Evita que un pago abandonado quede como pedido pendiente.
     *
     * Es idempotente y solo elimina registros que nunca llegaron a tener transacción.
     */
    @PostMapping("/cancelar-iniciado")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> cancelarPagoIniciado(
            @RequestBody(required = false) java.util.Map<String, String> payload,
            HttpSession session) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                return org.springframework.http.ResponseEntity.status(401).body(
                        java.util.Map.of("ok", false, "error", "Debe iniciar sesión"));
            }

            String reference = payload != null && payload.get("reference") != null
                    ? payload.get("reference").trim()
                    : "";
            if (reference.isEmpty()) {
                return org.springframework.http.ResponseEntity.badRequest().body(
                        java.util.Map.of("ok", false, "error", "Referencia de pago requerida"));
            }

            java.util.Optional<Compra> optCompra = compraRepository.findByReferenciaPago(reference);
            if (optCompra.isEmpty()) {
                return org.springframework.http.ResponseEntity.ok(java.util.Map.of("ok", true));
            }

            Compra compra = optCompra.get();
            if (compra.getComprador() == null
                    || !usuario.getId().equals(compra.getComprador().getId())) {
                return org.springframework.http.ResponseEntity.status(403).body(
                        java.util.Map.of("ok", false, "error", "La compra no pertenece al usuario"));
            }

            // Si ya hay una transacción o un estado final, no se toca: el webhook manda.
            if (compra.esFinal() || compra.getTransaccionId() != null) {
                return org.springframework.http.ResponseEntity.status(409).body(
                        java.util.Map.of("ok", false, "error", "La compra ya fue procesada"));
            }

            List<Pedido> pedidos = pedidoRepository.findByCompraId(compra.getId());
            boolean tieneTransaccion = pedidos.stream().anyMatch(p ->
                    p.getPago() != null
                            && (p.getPago().getTransaccionId() != null
                                    || PagoConfirmacionService.esFinal(p.getPago().getEstado())));
            if (tieneTransaccion) {
                return org.springframework.http.ResponseEntity.status(409).body(
                        java.util.Map.of("ok", false, "error", "La compra ya tiene una transacción asociada"));
            }

            pedidoRepository.deleteAll(pedidos);
            compraRepository.delete(compra);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("ok", true));

        } catch (Exception e) {
            log.warn("No se pudo cancelar la compra iniciada: {}", e.getMessage());
            return org.springframework.http.ResponseEntity.badRequest().body(
                    java.util.Map.of("ok", false, "error", "No se pudo cancelar la compra iniciada"));
        }
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