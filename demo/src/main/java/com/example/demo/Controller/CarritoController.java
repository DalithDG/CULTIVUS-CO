package com.example.demo.Controller;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.ProductoCarrito;
import com.example.demo.Model.embebidos.UnidadMedida;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.CalculoEnvioService;
import com.example.demo.services.ValidacionCantidad;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    private static final String CARRITO_ANONIMO_KEY = "carritoAnonimo";

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private CalculoEnvioService calculoEnvioService;

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    private Usuario obtenerUsuarioActual(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.getId() != null && usuarioRepository != null) {
            Usuario fresco = usuarioRepository.findById(usuario.getId()).orElse(usuario);
            session.setAttribute("usuarioLogueado", fresco);
            return fresco;
        }
        return usuario;
    }

    /**
     * Obtiene o crea el carrito activo.
     * Si el usuario está autenticado, busca su carrito en MongoDB y si tenía un carrito anónimo previo
     * en la sesión HTTP, fusiona sus ítems al carrito persistido.
     * Si el visitante es anónimo (no logueado), almacena el carrito en la sesión HTTP (carritoAnonimo).
     */
    private Carrito obtenerOCrearCarrito(HttpSession session, Usuario usuario) {
        if (usuario != null && usuario.getId() != null) {
            Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                    .orElseGet(() -> {
                        Carrito nuevo = new Carrito(usuario.getId());
                        return carritoRepository.save(nuevo);
                    });

            // Fusionar ítems del carrito anónimo si existían
            Carrito anonimo = (Carrito) session.getAttribute(CARRITO_ANONIMO_KEY);
            if (anonimo != null && anonimo.getItems() != null && !anonimo.getItems().isEmpty()) {
                for (ProductoCarrito itemAnonimo : anonimo.getItems()) {
                    Optional<ProductoCarrito> existente = carrito.getItems().stream()
                            .filter(i -> itemAnonimo.getOfertaId() != null && itemAnonimo.getOfertaId().equals(i.getOfertaId()))
                            .findFirst();
                    if (existente.isPresent()) {
                        existente.get().setCantidad(ValidacionCantidad.sumar(existente.get().getCantidad(), itemAnonimo.getCantidad()));
                    } else {
                        carrito.getItems().add(itemAnonimo);
                    }
                }
                carrito.recalcularTotal();
                carrito.setUpdatedAt(LocalDateTime.now());
                carrito = carritoRepository.save(carrito);
                session.removeAttribute(CARRITO_ANONIMO_KEY);
            }
            return carrito;
        } else {
            Carrito anonimo = (Carrito) session.getAttribute(CARRITO_ANONIMO_KEY);
            if (anonimo == null) {
                anonimo = new Carrito("anonimo_" + session.getId());
                session.setAttribute(CARRITO_ANONIMO_KEY, anonimo);
            }
            return anonimo;
        }
    }

    private void guardarCarrito(HttpSession session, Usuario usuario, Carrito carrito) {
        carrito.recalcularTotal();
        carrito.setUpdatedAt(LocalDateTime.now());
        if (usuario != null && usuario.getId() != null) {
            carritoRepository.save(carrito);
        } else {
            session.setAttribute(CARRITO_ANONIMO_KEY, carrito);
        }
    }

    /**
     * Valida (en el servidor, siempre desde la unidad de la OFERTA) la cantidad que quedaría en el carrito.
     */
    private String validarCantidadCarrito(OfertaVendedor oferta, double cantidadAgregada, double cantidadTotal) {
        if (!oferta.isDisponible() || !oferta.estaPublicada()) {
            return "Este producto ya no está disponible.";
        }
        UnidadMedida unidad = oferta.unidadEfectiva();
        // 1) lo enviado: > 0, entero/decimales según la unidad
        String error = ValidacionCantidad.validarCantidad(unidad, cantidadAgregada, null, null);
        if (error != null) {
            return error;
        }
        // 2) el total resultante: mínimo y stock
        double stock = oferta.getStock() != null ? oferta.getStock() : 0.0;
        return ValidacionCantidad.validarCantidad(unidad, cantidadTotal, oferta.getCompraMinima(), stock);
    }

    /** Copia al ítem los datos de la oferta que se pintan en el carrito (unidad, mínimo, step). */
    private void sincronizarItemConOferta(ProductoCarrito item, OfertaVendedor oferta) {
        UnidadMedida unidad = oferta.unidadEfectiva();
        item.setUnidadAbreviatura(unidad.getAbreviatura());
        item.setPermiteDecimales(unidad.permiteDecimales());
        item.setCompraMinima(oferta.getCompraMinima());
    }

    private ProductoCarrito crearItem(OfertaVendedor oferta, double cantidad) {
        String vendedorNombre = oferta.getVendedor() != null ? oferta.getVendedor().getNombre() : "";
        String imagen = oferta.tieneFotoPropia()
                ? oferta.getImagenUrl()
                : com.example.demo.services.ImagenUrlHelper.rutaReferenciaProducto(oferta.getNombreProducto());
        ProductoCarrito nuevoItem = new ProductoCarrito(
                oferta.getProductoCatalogoId(),
                oferta.getId(),
                oferta.getVendedor() != null ? oferta.getVendedor().getId() : null,
                vendedorNombre,
                oferta.getNombreProducto(),
                imagen,
                oferta.getPrecio(),
                cantidad,
                oferta.unidadEfectiva().getAbreviatura(),
                oferta.getCompraMinima()
        );
        nuevoItem.setPermiteDecimales(oferta.unidadEfectiva().permiteDecimales());
        return nuevoItem;
    }

    /**
     * Muestra el carrito de compras (permite visitantes anónimos y compradores).
     */
    @GetMapping
    public String mostrarCarrito(HttpSession session, Model model,
                                 RedirectAttributes redirectAttributes) {

        Usuario usuario = obtenerUsuarioActual(session);
        if (usuario != null && !usuario.puedeComprar()) {
            redirectAttributes.addFlashAttribute("error", Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR);
            return "redirect:/vendedor/dashboard";
        }

        Carrito carrito = obtenerOCrearCarrito(session, usuario);

        // Agrupar items por vendedor para la vista (US-21 Item 6)
        Map<String, List<ProductoCarrito>> itemsPorVendedor = new LinkedHashMap<>();
        for (ProductoCarrito item : carrito.getItems()) {
            String nombreVendedor = item.getVendedorNombre();
            if (nombreVendedor == null || nombreVendedor.isBlank()) {
                if (item.getOfertaId() != null) {
                    OfertaVendedor of = ofertaRepository.findById(item.getOfertaId()).orElse(null);
                    if (of != null && of.getVendedor() != null && of.getVendedor().getNombre() != null) {
                        nombreVendedor = of.getVendedor().getNombre();
                        item.setVendedorNombre(nombreVendedor);
                        if (item.getVendedorId() == null) {
                            item.setVendedorId(of.getVendedor().getId());
                        }
                    }
                }
            }
            if (nombreVendedor == null || nombreVendedor.isBlank()) {
                nombreVendedor = "Productor Local";
            }
            itemsPorVendedor.computeIfAbsent(nombreVendedor, k -> new ArrayList<>()).add(item);
        }

        // Envío estimado basado en la ciudad guardada del comprador (si está autenticado)
        String ciudadComprador = (usuario != null && usuario.getUbicacion() != null && usuario.getUbicacion().getCiudad() != null
                && !usuario.getUbicacion().getCiudad().isBlank())
                ? usuario.getUbicacion().getCiudad().trim() : null;

        CalculoEnvioService.ResultadoCalculoEnvio calculoEnvio =
                calculoEnvioService.calcularTarifaCarrito(carrito, ciudadComprador != null ? ciudadComprador : "");
        double subtotal = carrito.getTotalEstimado();
        double envioEstimado = (ciudadComprador != null) ? calculoEnvio.getTotalCop() : 0.0;
        double totalEstimado = (ciudadComprador != null) ? subtotal + envioEstimado : subtotal;

        model.addAttribute("usuario", usuario);
        model.addAttribute("carrito", carrito);
        model.addAttribute("detalles", carrito.getItems());
        model.addAttribute("itemsPorVendedor", itemsPorVendedor);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("envioEstimado", envioEstimado);
        model.addAttribute("tieneCiudad", ciudadComprador != null);
        model.addAttribute("ciudadComprador", ciudadComprador);
        model.addAttribute("desgloseEnvio", calculoEnvio.desglose());
        model.addAttribute("total", totalEstimado);

        return "comprador/carrito";
    }

    /**
     * Agrega una oferta al carrito (recibe ofertaId).
     * Permite usuarios anónimos (invitados) y compradores registrados.
     */
    @PostMapping("/agregar")
    public String agregarAlCarrito(
            @RequestParam("ofertaId") String ofertaId,
            @RequestParam(value = "cantidad", defaultValue = "1") Double cantidad,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = obtenerUsuarioActual(session);
            if (usuario != null && !usuario.puedeComprar()) {
                redirectAttributes.addFlashAttribute("error", Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR);
                return "redirect:/vendedor/dashboard";
            }

            OfertaVendedor oferta = ofertaRepository.findById(ofertaId)
                    .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));

            // Rechazar si la oferta pertenece al propio usuario vendedor
            if (oferta.getVendedor() != null && usuario != null && usuario.getId() != null
                    && usuario.getId().equals(oferta.getVendedor().getId())) {
                redirectAttributes.addFlashAttribute("error", "No puedes comprar tus propios productos.");
                return "redirect:/producto/" + oferta.getProductoCatalogoId();
            }

            Carrito carrito = obtenerOCrearCarrito(session, usuario);

            Optional<ProductoCarrito> itemExistente = carrito.getItems().stream()
                    .filter(i -> ofertaId.equals(i.getOfertaId()))
                    .findFirst();

            double cantidadSolicitada = cantidad != null ? cantidad : 0.0;
            double nuevaCantidad = itemExistente
                    .map(i -> ValidacionCantidad.sumar(i.getCantidad(), cantidadSolicitada))
                    .orElse(cantidadSolicitada);

            // Validación única (unidad de la oferta): enteros/decimales, mínimo y stock
            String error = validarCantidadCarrito(oferta, cantidadSolicitada, nuevaCantidad);
            if (error != null) {
                redirectAttributes.addFlashAttribute("error", error);
                return "redirect:/producto/" + oferta.getProductoCatalogoId();
            }

            if (itemExistente.isPresent()) {
                ProductoCarrito item = itemExistente.get();
                item.setCantidad(nuevaCantidad);
                sincronizarItemConOferta(item, oferta);
            } else {
                carrito.getItems().add(crearItem(oferta, cantidadSolicitada));
            }

            guardarCarrito(session, usuario, carrito);

            redirectAttributes.addFlashAttribute("mensaje", "Producto agregado al carrito");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al agregar producto al carrito");
        }

        return "redirect:/carrito";
    }

    /**
     * Endpoint AJAX para agregar al carrito sin recargar la página.
     * Permite usuarios anónimos (visitantes) y compradores registrados.
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
            Usuario usuario = obtenerUsuarioActual(session);
            if (usuario != null && !usuario.puedeComprar()) {
                response.put("success", false);
                response.put("error", Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            OfertaVendedor oferta = ofertaRepository.findById(ofertaId)
                    .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));

            // Rechazar si la oferta pertenece al propio usuario vendedor
            if (oferta.getVendedor() != null && usuario != null && usuario.getId() != null
                    && usuario.getId().equals(oferta.getVendedor().getId())) {
                response.put("success", false);
                response.put("error", "No puedes comprar tus propios productos.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Carrito carrito = obtenerOCrearCarrito(session, usuario);

            Optional<ProductoCarrito> itemExistente = carrito.getItems().stream()
                    .filter(i -> ofertaId.equals(i.getOfertaId()))
                    .findFirst();

            boolean yaEnCarrito = itemExistente.isPresent();

            double cantidadSolicitada = cantidad != null ? cantidad : 0.0;
            double nuevaCantidad = itemExistente
                    .map(i -> ValidacionCantidad.sumar(i.getCantidad(), cantidadSolicitada))
                    .orElse(cantidadSolicitada);

            // Validación única (unidad de la oferta): enteros/decimales, mínimo y stock
            String error = validarCantidadCarrito(oferta, cantidadSolicitada, nuevaCantidad);
            if (error != null) {
                response.put("success", false);
                response.put("error", error);
                return ResponseEntity.ok(response);
            }

            if (itemExistente.isPresent()) {
                ProductoCarrito item = itemExistente.get();
                item.setCantidad(nuevaCantidad);
                sincronizarItemConOferta(item, oferta);
            } else {
                carrito.getItems().add(crearItem(oferta, cantidadSolicitada));
            }

            guardarCarrito(session, usuario, carrito);

            response.put("success", true);
            response.put("mensaje", "¡" + oferta.getNombreProducto() + " agregado al carrito!");
            response.put("totalItems", carrito.getTotalArticulos());
            response.put("totalEstimado", carrito.getTotalEstimado());
            response.put("yaEnCarrito", yaEnCarrito);
            response.put("cantidadEnCarrito", nuevaCantidad);
            response.put("unidad", oferta.unidadEfectiva().getAbreviatura());
            response.put("nombreProducto", oferta.getNombreProducto());

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
     * Actualiza la cantidad de un producto en el carrito (anónimo o registrado).
     */
    @PostMapping("/actualizar")
    public String actualizarCantidad(
            @RequestParam("ofertaId") String ofertaId,
            @RequestParam("cantidad") Double cantidad,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = obtenerUsuarioActual(session);
            if (usuario != null && !usuario.puedeComprar()) {
                redirectAttributes.addFlashAttribute("error", Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR);
                return "redirect:/vendedor/dashboard";
            }

            Carrito carrito = obtenerOCrearCarrito(session, usuario);

            if (cantidad == null || cantidad <= 0) {
                carrito.getItems().removeIf(i -> ofertaId.equals(i.getOfertaId()));
                redirectAttributes.addFlashAttribute("mensaje", "Producto eliminado del carrito");
            } else {
                OfertaVendedor oferta = ofertaRepository.findById(ofertaId)
                        .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));

                String error = validarCantidadCarrito(oferta, cantidad, cantidad);
                if (error != null) {
                    redirectAttributes.addFlashAttribute("error", error);
                    return "redirect:/carrito";
                }

                carrito.getItems().stream()
                        .filter(i -> ofertaId.equals(i.getOfertaId()))
                        .findFirst()
                        .ifPresent(i -> {
                            i.setCantidad(cantidad);
                            sincronizarItemConOferta(i, oferta);
                        });

                redirectAttributes.addFlashAttribute("mensaje", "Cantidad actualizada");
            }

            guardarCarrito(session, usuario, carrito);

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el carrito");
        }

        return "redirect:/carrito";
    }

    /**
     * Elimina un producto del carrito (anónimo o registrado).
     */
    @PostMapping("/eliminar")
    public String eliminarDelCarrito(
            @RequestParam("ofertaId") String ofertaId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = obtenerUsuarioActual(session);
            if (usuario != null && !usuario.puedeComprar()) {
                redirectAttributes.addFlashAttribute("error", Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR);
                return "redirect:/vendedor/dashboard";
            }

            Carrito carrito = obtenerOCrearCarrito(session, usuario);
            carrito.getItems().removeIf(i -> ofertaId.equals(i.getOfertaId()));
            guardarCarrito(session, usuario, carrito);

            redirectAttributes.addFlashAttribute("mensaje", "Producto eliminado del carrito");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar el producto");
        }

        return "redirect:/carrito";
    }

    /**
     * Vacía todos los productos del carrito.
     */
    @PostMapping("/vaciar")
    public String vaciarCarrito(HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = obtenerUsuarioActual(session);
            if (usuario != null && !usuario.puedeComprar()) {
                redirectAttributes.addFlashAttribute("error", Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR);
                return "redirect:/vendedor/dashboard";
            }

            Carrito carrito = obtenerOCrearCarrito(session, usuario);
            carrito.getItems().clear();
            guardarCarrito(session, usuario, carrito);

            redirectAttributes.addFlashAttribute("mensaje", "Carrito vaciado");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al vaciar el carrito");
        }
        return "redirect:/carrito";
    }
}
