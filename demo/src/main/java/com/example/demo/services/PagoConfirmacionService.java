package com.example.demo.services;

import com.example.demo.Model.Compra;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Pieza compartida por el webhook de Wompi y por el retorno del comprador
 * (/pago/resultado): aplica el resultado de una transacción a las compras, pagos y pedidos.
 *
 * APPROVED                  -> COMPLETADO (pago), PAGADA (compra), CONFIRMADO (pedidos con stock),
 *                              descuenta stock en una sola operación, notifica y limpia carrito.
 *                              Si el stock no alcanza al confirmar, marca el pedido EN_REVISION y notifica al admin.
 * DECLINED / ERROR / VOIDED -> FALLIDO (pago), RECHAZADA (compra), CANCELADO (pedidos), sin tocar stock ni carrito,
 *                              y envía UNA SOLA notificación al comprador por compra.
 * PENDING                   -> sin cambios
 */
@Service
public class PagoConfirmacionService {

    private static final Logger log = LoggerFactory.getLogger(PagoConfirmacionService.class);

    public static final String COMPLETADO = "COMPLETADO";
    public static final String FALLIDO = "FALLIDO";

    public enum Resultado {
        /** Se actualizó al menos un pago. */
        APLICADO,
        /** Estado PENDING u otro no terminal: nada que cambiar. */
        SIN_CAMBIOS,
        /** Todos los pagos ya estaban en estado final: no se degrada ni se reaplica. */
        YA_FINALIZADO,
        /** APPROVED pero el monto recibido no coincide con el firmado: no se marca COMPLETADO. */
        MONTO_NO_COINCIDE,
        /** No hay pedidos con esa referencia. */
        REFERENCIA_DESCONOCIDA,
        /** Estado de Wompi no reconocido. */
        ESTADO_DESCONOCIDO
    }

    @Autowired(required = false)
    private PedidoRepository pedidoRepository;

    @Autowired(required = false)
    private OfertaRepository ofertaRepository;

    @Autowired(required = false)
    private CatalogoService catalogoService;

    @Autowired(required = false)
    private NotificacionService notificacionService;

    @Autowired(required = false)
    private CarritoRepository carritoRepository;

    @Autowired(required = false)
    private CompraRepository compraRepository;

    @Autowired(required = false)
    private BilleteraService billeteraService;

    public PagoConfirmacionService() {
    }

    /** Para pruebas: inyecta el servicio de billetera (US-22). */
    public void setBilleteraService(BilleteraService billeteraService) {
        this.billeteraService = billeteraService;
    }

    public PagoConfirmacionService(PedidoRepository pedidoRepository) {
        this(pedidoRepository, null, null, null, null, null);
    }

    public PagoConfirmacionService(PedidoRepository pedidoRepository,
                                  OfertaRepository ofertaRepository,
                                  CatalogoService catalogoService,
                                  NotificacionService notificacionService,
                                  CarritoRepository carritoRepository) {
        this(pedidoRepository, ofertaRepository, catalogoService, notificacionService, carritoRepository, null);
    }

    public PagoConfirmacionService(PedidoRepository pedidoRepository,
                                  OfertaRepository ofertaRepository,
                                  CatalogoService catalogoService,
                                  NotificacionService notificacionService,
                                  CarritoRepository carritoRepository,
                                  CompraRepository compraRepository) {
        this.pedidoRepository = pedidoRepository;
        this.ofertaRepository = ofertaRepository;
        this.catalogoService = catalogoService;
        this.notificacionService = notificacionService;
        this.carritoRepository = carritoRepository;
        this.compraRepository = compraRepository;
    }

    public Resultado aplicarResultado(String referencia, String transaccionId, String estadoWompi,
            long montoEnCentavos) {

        if (referencia == null || referencia.isBlank()) {
            return Resultado.REFERENCIA_DESCONOCIDA;
        }
        String refLimpia = referencia.trim();

        List<Pedido> pedidos = pedidoRepository != null
                ? pedidoRepository.findByPago_Referencia(refLimpia)
                : Collections.emptyList();

        Compra compra = compraRepository != null
                ? compraRepository.findByReferenciaPago(refLimpia).orElse(null)
                : null;

        if ((pedidos == null || pedidos.isEmpty()) && compra != null && compra.getPedidoIds() != null && pedidoRepository != null) {
            pedidos = pedidoRepository.findAllById(compra.getPedidoIds());
        }

        if ((pedidos == null || pedidos.isEmpty()) && compra == null) {
            return Resultado.REFERENCIA_DESCONOCIDA;
        }

        String estadoNuevo = estadoWompi == null ? "" : estadoWompi.trim().toUpperCase();
        String destino;
        switch (estadoNuevo) {
            case "APPROVED":
                destino = COMPLETADO;
                break;
            case "DECLINED":
            case "ERROR":
            case "VOIDED":
                destino = FALLIDO;
                break;
            case "PENDING":
                return Resultado.SIN_CAMBIOS;
            default:
                return Resultado.ESTADO_DESCONOCIDO;
        }

        // Si todos los pagos ya están en estado final, no se degradan ni se reaplican
        boolean todosFinales = (pedidos != null && !pedidos.isEmpty())
                && pedidos.stream().allMatch(p -> p.getPago() != null && esFinal(p.getPago().getEstado()));
        if (todosFinales && (compra == null || compra.esFinal())) {
            return Resultado.YA_FINALIZADO;
        }

        // Validación de monto en centavos para pagos aprobados
        if (COMPLETADO.equals(destino)) {
            if (compra != null) {
                if (compra.getTotalCentavos() == null || compra.getTotalCentavos() != montoEnCentavos) {
                    log.warn("Monto de Wompi ({}) no coincide con el total de la compra ({}) para ref {}",
                            montoEnCentavos, compra.getTotalCentavos(), refLimpia);
                    return Resultado.MONTO_NO_COINCIDE;
                }
            } else if (pedidos != null) {
                for (Pedido p : pedidos) {
                    if (p.getPago() != null) {
                        Long firmado = p.getPago().getMontoCentavosFirmado();
                        if (firmado == null || firmado.longValue() != montoEnCentavos) {
                            log.warn("Monto de Wompi ({}) no coincide con el monto firmado ({}) en pedido {} para ref {}",
                                    montoEnCentavos, firmado, p.getId(), refLimpia);
                            return Resultado.MONTO_NO_COINCIDE;
                        }
                    }
                }
            }
        }

        String compradorId = null;
        if (compra != null && compra.getComprador() != null) {
            compradorId = compra.getComprador().getId();
        } else if (pedidos != null && !pedidos.isEmpty() && pedidos.get(0).getComprador() != null) {
            compradorId = pedidos.get(0).getComprador().getId();
        }

        // Actualizar datos de pago en pedidos
        if (pedidos != null) {
            for (Pedido p : pedidos) {
                DatosPago pago = p.getPago();
                if (pago != null && !esFinal(pago.getEstado())) {
                    pago.setEstado(destino);
                    pago.setTransaccionId(transaccionId);
                    pago.setFechaPago(LocalDateTime.now());
                }
            }
        }

        if (COMPLETADO.equals(destino)) {
            // US-03 Item 2: Valida primero el stock de todos los pedidos, luego aplica en una sola operación.
            // Si el stock no alcanza al confirmar, marca ese pedido para revisión del admin (reembolso manual).
            List<Pedido> pedidosAprobados = new ArrayList<>();
            Map<Pedido, String> pedidosEnRevision = new LinkedHashMap<>();

            // 1. Cargar ofertas involucradas
            Map<String, OfertaVendedor> ofertasMap = new HashMap<>();
            if (ofertaRepository != null && pedidos != null) {
                Set<String> ofertaIds = new HashSet<>();
                for (Pedido p : pedidos) {
                    if (p.getItems() != null) {
                        for (ProductoPedido item : p.getItems()) {
                            if (item.getProductoId() != null) {
                                ofertaIds.add(item.getProductoId());
                            }
                        }
                    }
                }
                for (String oid : ofertaIds) {
                    ofertaRepository.findById(oid).ifPresent(o -> ofertasMap.put(oid, o));
                }
            }

            // 2. Validar pedido por pedido si el stock disponible alcanza para todos sus ítems
            if (pedidos != null) {
                for (Pedido p : pedidos) {
                    boolean stockAlcanza = true;
                    String motivoFalla = null;

                    if (p.getItems() != null && ofertaRepository != null) {
                        for (ProductoPedido item : p.getItems()) {
                            String idOferta = item.getProductoId();
                            OfertaVendedor oferta = ofertasMap.get(idOferta);
                            double cant = item.getCantidad() != null ? item.getCantidad() : 0.0;
                            double stockActual = (oferta != null && oferta.getStock() != null) ? oferta.getStock() : 0.0;

                            if (oferta == null || stockActual < cant) {
                                stockAlcanza = false;
                                motivoFalla = "Stock insuficiente para '" + item.getNombreSnapshot()
                                        + "'. Requerido: " + cant + ", disponible: " + stockActual;
                                break;
                            }
                        }
                    }

                    if (stockAlcanza) {
                        pedidosAprobados.add(p);
                    } else {
                        pedidosEnRevision.put(p, motivoFalla != null ? motivoFalla : "Stock insuficiente");
                    }
                }
            }

            // US-22: reparto a billeteras. Va ANTES de persistir stock y pedidos para que, si algo falla,
            // el reintento (aún con pedidos PENDIENTE) vuelva a entrar y el abono, idempotente, no se duplique.
            abonarBilleteraPorCompra(compra, pedidosAprobados);

            // 3. Aplicar descuento de stock SOLO a los pedidos aprobados en una sola operación
            Set<OfertaVendedor> ofertasParaGuardar = new HashSet<>();
            for (Pedido p : pedidosAprobados) {
                if (p.getItems() != null) {
                    for (ProductoPedido item : p.getItems()) {
                        String idOferta = item.getProductoId();
                        OfertaVendedor oferta = ofertasMap.get(idOferta);
                        if (oferta != null) {
                            double cant = item.getCantidad() != null ? item.getCantidad() : 0.0;
                            double stockActual = oferta.getStock() != null ? oferta.getStock() : 0.0;
                            double nuevoStock = Math.max(0.0, stockActual - cant);
                            oferta.setStock(nuevoStock);
                            if (nuevoStock <= 0.0) {
                                oferta.setDisponible(false);
                            }
                            ofertasParaGuardar.add(oferta);
                        }
                    }
                }
            }

            if (ofertaRepository != null) {
                for (OfertaVendedor of : ofertasParaGuardar) {
                    ofertaRepository.save(of);
                    if (catalogoService != null && of.getProductoCatalogoId() != null) {
                        catalogoService.actualizarEstadisticas(of.getProductoCatalogoId());
                    }
                }
            }

            // 4. Guardar pedidos aprobados y notificar a cada vendedor
            for (Pedido p : pedidosAprobados) {
                p.setEstado("CONFIRMADO");
                p.setRequiereRevision(false);
                p.setMotivoRevision(null);
                p.setUpdatedAt(LocalDateTime.now());
                if (pedidoRepository != null) {
                    pedidoRepository.save(p);
                }

                if (notificacionService != null && p.getVendedor() != null && p.getVendedor().getId() != null) {
                    String nombreComprador = p.getComprador() != null ? p.getComprador().getNombre() : "un comprador";
                    notificacionService.enviar(
                            p.getVendedor().getId(),
                            "¡Nueva Venta!",
                            "Has recibido un nuevo pedido #" + p.getId() + " de " + nombreComprador,
                            "SUCCESS");
                }
            }

            // 5. Guardar pedidos en revisión y notificar al ADMIN (reembolso manual)
            for (Map.Entry<Pedido, String> entry : pedidosEnRevision.entrySet()) {
                Pedido p = entry.getKey();
                String motivo = entry.getValue();

                p.setEstado(Pedido.EN_REVISION);
                p.setRequiereRevision(true);
                p.setMotivoRevision(motivo);
                p.setUpdatedAt(LocalDateTime.now());
                if (pedidoRepository != null) {
                    pedidoRepository.save(p);
                }

                if (notificacionService != null) {
                    notificacionService.enviarAAdmins(
                            "Revisión requerida: Pedido #" + p.getId(),
                            "Stock insuficiente al confirmar el pedido #" + p.getId() + " de la compra " + refLimpia
                                    + ". " + motivo + ". Requiere reembolso manual.",
                            "DANGER");
                }
            }

            // 6. Notificar al Comprador (una sola notificación por la compra)
            if (notificacionService != null && compradorId != null) {
                if (pedidosEnRevision.isEmpty()) {
                    notificacionService.enviar(
                            compradorId,
                            "Pedido Confirmado",
                            "Tu compra con referencia " + refLimpia + " ha sido aprobada con éxito.",
                            "SUCCESS");
                } else if (!pedidosAprobados.isEmpty()) {
                    notificacionService.enviar(
                            compradorId,
                            "Compra Aprobada con Revisión",
                            "Tu compra con referencia " + refLimpia
                                    + " fue aprobada, pero algunos productos no tienen stock suficiente y están en revisión para reembolso.",
                            "WARNING");
                } else {
                    notificacionService.enviar(
                            compradorId,
                            "Compra en Revisión de Stock",
                            "Tu pago fue recibido, pero el stock de tus productos ya no alcanza. Tu pedido está en revisión del administrador para reembolso.",
                            "WARNING");
                }
            }

            // 7. Limpiar carrito
            if (carritoRepository != null && compradorId != null) {
                carritoRepository.findByUsuarioId(compradorId).ifPresent(carritoRepository::delete);
            }

            // 8. Actualizar entidad Compra
            if (compra != null && compraRepository != null) {
                compra.setEstado(Compra.PAGADA);
                compra.setTransaccionId(transaccionId);
                compra.setFechaPago(LocalDateTime.now());
                compra.setRequiereRevision(!pedidosEnRevision.isEmpty());
                compra.setUpdatedAt(LocalDateTime.now());
                compraRepository.save(compra);
            }


        } else if (FALLIDO.equals(destino)) {
            // US-03 Item 3: En pago fallido, cada pedido se cancela sin tocar stock,
            // y se envía UNA SOLA notificación al comprador por compra, no una por pedido.
            if (pedidos != null) {
                for (Pedido p : pedidos) {
                    p.setEstado("CANCELADO");
                    p.setUpdatedAt(LocalDateTime.now());
                    if (pedidoRepository != null) {
                        pedidoRepository.save(p);
                    }
                }
            }

            if (compra != null && compraRepository != null) {
                compra.setEstado(Compra.RECHAZADA);
                compra.setTransaccionId(transaccionId);
                compra.setUpdatedAt(LocalDateTime.now());
                compraRepository.save(compra);
            }

            if (notificacionService != null && compradorId != null) {
                notificacionService.enviar(
                        compradorId,
                        "Pago No Aprobado",
                        "El pago de tu compra con referencia " + refLimpia
                                + " no fue aprobado por la pasarela (" + estadoNuevo
                                + "). Tu carrito se mantiene intacto.",
                        "WARNING");
            }
        }

        return Resultado.APLICADO;
    }

    /**
     * US-22: abona a la billetera de cada vendedor (en retención) el subtotal de productos de sus
     * sub-pedidos aprobados menos la comisión. Idempotente. Solo se invoca con pago aprobado.
     */
    protected void abonarBilleteraPorCompra(Compra compra, List<Pedido> pedidosAprobados) {
        if (billeteraService == null) {
            return;
        }
        billeteraService.registrarRepartoVentas(compra, pedidosAprobados);
    }

    public static boolean esFinal(String estadoPago) {
        return COMPLETADO.equals(estadoPago) || FALLIDO.equals(estadoPago);
    }
}
