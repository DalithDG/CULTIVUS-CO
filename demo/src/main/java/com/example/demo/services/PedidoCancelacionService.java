package com.example.demo.services;

import com.example.demo.Model.ActorTransicion;
import com.example.demo.Model.CanceladoPor;
import com.example.demo.Model.EstadoPedido;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BLOQUE 3 - Rutina centralizada de cancelación de pedidos.
 * Utilizada tanto por el vendedor como por administradores.
 */
@Service
public class PedidoCancelacionService {

    private static final Logger log = LoggerFactory.getLogger(PedidoCancelacionService.class);

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoEstadoService pedidoEstadoService;

    @Autowired(required = false)
    private OfertaRepository ofertaRepository;

    @Autowired(required = false)
    private CatalogoService catalogoService;

    @Autowired(required = false)
    private BilleteraService billeteraService;

    @Autowired(required = false)
    private TransportistaService transportistaService;

    @Autowired(required = false)
    private NotificacionService notificacionService;

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    /**
     * Rutina central de cancelación con todos los efectos colaterales auditados y consistentes:
     * (a) Restaura stock solo si el stock fue previamente descontado.
     * (b) Revierte abono y comisión en la billetera del vendedor (idempotente).
     * (c) Marca requiereReembolso por el total (subtotal + envío del sub-pedido).
     * (d) Si es a domicilio, remueve la parada del transportista y recalcula flete.
     * (e) Actualiza la Compra y notifica a comprador y administradores.
     */
    public Pedido cancelarPedido(Pedido pedido, String motivo, ActorTransicion actor, CanceladoPor canceladoPor) {
        if (pedido == null) {
            throw new IllegalArgumentException("El pedido no puede ser nulo");
        }

        EstadoPedido estadoActual = pedido.getEstadoEnum();
        if (estadoActual == EstadoPedido.CANCELADO) {
            log.info("El pedido {} ya se encuentra cancelado. Operación idempotente.", pedido.getId());
            return pedido;
        }

        if (estadoActual == EstadoPedido.ENVIADO || estadoActual == EstadoPedido.ENTREGADO) {
            throw new IllegalStateException("No se puede cancelar un pedido que ya fue enviado o entregado.");
        }

        String motivoFinal = (motivo != null && !motivo.isBlank())
                ? motivo.trim()
                : "Cancelación registrada por " + actor;

        // 1. Transición central de estado
        pedido.setCanceladoPor(canceladoPor);
        pedido.setMotivoCancelacion(motivoFinal);
        pedido.setFechaCancelacion(LocalDateTime.now());

        pedidoEstadoService.transicionar(pedido, EstadoPedido.CANCELADO, actor, motivoFinal, canceladoPor);

        // 2. (a) Restaurar stock solo si el pedido descontó stock
        if (pedido.isStockDescontado()) {
            restaurarStock(pedido);
            pedido.setStockDescontado(false);
        }

        // 3. (b) Revertir movimientos en la billetera del vendedor
        if (billeteraService != null) {
            try {
                billeteraService.revertirVentaCancelada(pedido);
            } catch (Exception e) {
                log.error("Error al revertir billetera en cancelación del pedido {}: {}", pedido.getId(), e.getMessage(), e);
            }
        }

        // 4. (c) Marcar requiereReembolso por subtotal + envío de este sub-pedido
        long subtotalCent = pedido.getSubtotalCentavos() != null
                ? pedido.getSubtotalCentavos()
                : Math.round(pedido.getSubtotalProductos() * 100.0);
        long envioCent = pedido.getEnvioCentavos() != null ? pedido.getEnvioCentavos() : 0L;
        long totalReembolsoCentavos = subtotalCent + envioCent;

        pedido.setRequiereReembolso(true);
        pedido.setMontoReembolsoCentavos(totalReembolsoCentavos);

        // 5. (d) Si es envío a domicilio, remover parada de la ruta logística
        if (pedido.isDomicilio() && transportistaService != null) {
            try {
                transportistaService.removerParadaPorPedidoCancelado(pedido.getId());
            } catch (Exception e) {
                log.error("Error al remover parada logística del pedido cancelado {}: {}", pedido.getId(), e.getMessage(), e);
            }
        }

        Pedido guardado = pedidoRepository.save(pedido);

        // 6. Notificaciones
        enviarNotificacionesCancelacion(guardado, motivoFinal, totalReembolsoCentavos, actor);

        return guardado;
    }

    private void restaurarStock(Pedido pedido) {
        if (ofertaRepository == null || pedido.getItems() == null) {
            return;
        }

        for (ProductoPedido item : pedido.getItems()) {
            if (item.getProductoId() != null) {
                ofertaRepository.findById(item.getProductoId()).ifPresent(oferta -> {
                    double cantidad = item.getCantidad() != null ? item.getCantidad() : 0.0;
                    double stockActual = oferta.getStock() != null ? oferta.getStock() : 0.0;
                    double nuevoStock = stockActual + cantidad;
                    oferta.setStock(nuevoStock);
                    if (nuevoStock > 0.0) {
                        oferta.setDisponible(true);
                    }
                    ofertaRepository.save(oferta);
                    log.info("Stock restaurado para oferta {}: {} -> {}", oferta.getId(), stockActual, nuevoStock);

                    if (catalogoService != null && oferta.getProductoCatalogoId() != null) {
                        catalogoService.actualizarEstadisticas(oferta.getProductoCatalogoId());
                    }
                });
            }
        }
    }

    private void enviarNotificacionesCancelacion(Pedido pedido, String motivo, long montoReembolsoCentavos, ActorTransicion actor) {
        if (notificacionService == null) {
            return;
        }

        double montoPesos = montoReembolsoCentavos / 100.0;

        // Comprador
        if (pedido.getComprador() != null && pedido.getComprador().getId() != null) {
            String mensajeComprador = String.format(
                    "Tu pedido #%s ha sido cancelado (%s). Motivo: %s. Se ha programado un reembolso a tu favor por $%,.0f COP.",
                    pedido.getNumeroFactura(), actor, motivo, montoPesos);
            notificacionService.enviar(
                    pedido.getComprador().getId(),
                    "Pedido Cancelado - Reembolso Pendiente",
                    mensajeComprador,
                    "WARNING"
            );
        }

        // Vendedor (si no fue cancelado por él mismo)
        if (actor != ActorTransicion.VENDEDOR && pedido.getVendedor() != null && pedido.getVendedor().getId() != null) {
            notificacionService.enviar(
                    pedido.getVendedor().getId(),
                    "Pedido Cancelado",
                    "El pedido #" + pedido.getNumeroFactura() + " ha sido cancelado por " + actor + ". Motivo: " + motivo,
                    "INFO"
            );
        }

        // Administradores
        if (usuarioRepository != null) {
            List<Usuario> admins = usuarioRepository.findByRolesContaining(com.example.demo.Model.Role.ADMIN);
            for (Usuario adm : admins) {
                String mensajeAdmin = String.format(
                        "El pedido #%s fue cancelado por %s. Requiere gestionar reembolso manual de $%,.0f COP al comprador. Motivo: %s",
                        pedido.getNumeroFactura(), actor, montoPesos, motivo);
                notificacionService.enviar(
                        adm.getId(),
                        "Reembolso Manual Requerido",
                        mensajeAdmin,
                        "DANGER"
                );
            }
        }
    }
}
