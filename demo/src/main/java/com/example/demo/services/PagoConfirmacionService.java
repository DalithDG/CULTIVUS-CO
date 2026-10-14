package com.example.demo.services;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pieza compartida por el webhook de Wompi y por el retorno del comprador
 * (/pago/resultado): aplica el resultado de una transacción a los pagos y pedidos.
 *
 * APPROVED                  -> COMPLETADO (pago), CONFIRMADO (pedido), descuenta stock, notifica y limpia carrito
 * DECLINED / ERROR / VOIDED -> FALLIDO (pago), CANCELADO (pedido), sin tocar stock ni carrito
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

    public PagoConfirmacionService() {
    }

    public PagoConfirmacionService(PedidoRepository pedidoRepository) {
        this(pedidoRepository, null, null, null, null);
    }

    @Autowired
    public PagoConfirmacionService(PedidoRepository pedidoRepository,
                                  OfertaRepository ofertaRepository,
                                  CatalogoService catalogoService,
                                  NotificacionService notificacionService,
                                  CarritoRepository carritoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.ofertaRepository = ofertaRepository;
        this.catalogoService = catalogoService;
        this.notificacionService = notificacionService;
        this.carritoRepository = carritoRepository;
    }

    public Resultado aplicarResultado(String referencia, String transaccionId, String estadoWompi,
            long montoEnCentavos) {

        if (referencia == null || referencia.isBlank()) {
            return Resultado.REFERENCIA_DESCONOCIDA;
        }
        List<Pedido> pedidos = pedidoRepository.findByPago_Referencia(referencia.trim());
        if (pedidos == null || pedidos.isEmpty()) {
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

        boolean aplicado = false;
        boolean montoInvalido = false;

        for (Pedido pedido : pedidos) {
            DatosPago pago = pedido.getPago();
            if (pago == null) {
                continue;
            }
            // Estado final: no se degrada (COMPLETADO -> FALLIDO) ni se reaplica
            if (esFinal(pago.getEstado())) {
                continue;
            }
            if (COMPLETADO.equals(destino)) {
                Long firmado = pago.getMontoCentavosFirmado();
                if (firmado == null || firmado.longValue() != montoEnCentavos) {
                    log.warn("Monto de Wompi no coincide con el firmado para la referencia {} (tx {})",
                            referencia, transaccionId);
                    montoInvalido = true;
                    continue;
                }
            }

            pago.setEstado(destino);
            pago.setTransaccionId(transaccionId);
            pago.setFechaPago(LocalDateTime.now());
            pedido.setUpdatedAt(LocalDateTime.now());

            if (COMPLETADO.equals(destino)) {
                // 1. Pasar pedido a CONFIRMADO
                pedido.setEstado("CONFIRMADO");

                // 2. Descontar stock de cada oferta solo ahora que el pago fue aprobado
                if (ofertaRepository != null && pedido.getItems() != null) {
                    for (ProductoPedido item : pedido.getItems()) {
                        String idOferta = item.getProductoId();
                        if (idOferta != null) {
                            ofertaRepository.findById(idOferta).ifPresent(oferta -> {
                                double cantidad = item.getCantidad() != null ? item.getCantidad() : 0.0;
                                double stockActual = oferta.getStock() != null ? oferta.getStock() : 0.0;
                                double nuevoStock = Math.max(0.0, stockActual - cantidad);
                                oferta.setStock(nuevoStock);
                                if (nuevoStock <= 0.0) {
                                    oferta.setDisponible(false);
                                }
                                ofertaRepository.save(oferta);

                                if (catalogoService != null && oferta.getProductoCatalogoId() != null) {
                                    catalogoService.actualizarEstadisticas(oferta.getProductoCatalogoId());
                                }
                            });
                        }
                    }
                }

                // 3. Notificar al Vendedor
                if (notificacionService != null && pedido.getVendedor() != null && pedido.getVendedor().getId() != null) {
                    String nombreComprador = pedido.getComprador() != null ? pedido.getComprador().getNombre() : "un comprador";
                    notificacionService.enviar(
                            pedido.getVendedor().getId(),
                            "¡Nueva Venta!",
                            "Has recibido un nuevo pedido #" + pedido.getId() + " de " + nombreComprador,
                            "SUCCESS");
                }

                // 4. Notificar al Comprador
                if (notificacionService != null && pedido.getComprador() != null && pedido.getComprador().getId() != null) {
                    notificacionService.enviar(
                            pedido.getComprador().getId(),
                            "Pedido Confirmado",
                            "Tu compra ha sido aprobada con éxito. Pedido #" + pedido.getId(),
                            "SUCCESS");
                }

                // 5. Limpiar carrito del comprador
                if (carritoRepository != null && pedido.getComprador() != null && pedido.getComprador().getId() != null) {
                    carritoRepository.findByUsuarioId(pedido.getComprador().getId()).ifPresent(carritoRepository::delete);
                }

            } else if (FALLIDO.equals(destino)) {
                // Si el pago falló o fue rechazado, el pedido se cancela y NO se descuenta stock
                pedido.setEstado("CANCELADO");

                // Notificar al Comprador
                if (notificacionService != null && pedido.getComprador() != null && pedido.getComprador().getId() != null) {
                    notificacionService.enviar(
                            pedido.getComprador().getId(),
                            "Pago No Aprobado",
                            "El pago de tu pedido #" + pedido.getId() + " no fue aprobado por la pasarela (" + estadoNuevo + "). Tu carrito se mantiene intacto.",
                            "WARNING");
                }
            }

            pedidoRepository.save(pedido);
            aplicado = true;
        }

        if (aplicado) {
            return Resultado.APLICADO;
        }
        return montoInvalido ? Resultado.MONTO_NO_COINCIDE : Resultado.YA_FINALIZADO;
    }

    public static boolean esFinal(String estadoPago) {
        return COMPLETADO.equals(estadoPago) || FALLIDO.equals(estadoPago);
    }
}
