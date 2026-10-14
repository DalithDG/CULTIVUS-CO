package com.example.demo.services;

import com.example.demo.Model.Pedido;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pieza compartida por el webhook de Wompi y por el retorno del comprador
 * (/pago/resultado): aplica el resultado de una transacción a los pagos.
 *
 * APPROVED                  -> COMPLETADO (solo si el monto coincide con el firmado)
 * DECLINED / ERROR / VOIDED -> FALLIDO
 * PENDING                   -> sin cambios
 *
 * Solo modifica DatosPago (estado, transaccionId, fechaPago). Stock, Pedido.estado,
 * compras, billetera y notificaciones quedan fuera de alcance (Sprint 2).
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

    @Autowired
    private PedidoRepository pedidoRepository;

    public PagoConfirmacionService() {
    }

    public PagoConfirmacionService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
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
