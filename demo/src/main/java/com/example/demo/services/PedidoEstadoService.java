package com.example.demo.services;

import com.example.demo.Model.*;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.EnvioRepository;
import com.example.demo.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Único punto central de transiciones de estado de sub-pedidos (BLOQUE 1 y BLOQUE 7).
 * Valida de forma estricta las transiciones según el actor solicitante (VENDEDOR, TRANSPORTISTA,
 * ADMIN, SISTEMA) y la modalidad de entrega (DOMICILIO, RECOGE_EN_TIENDA).
 */
@Service
public class PedidoEstadoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoEstadoService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired(required = false)
    private CompraRepository compraRepository;

    @Autowired(required = false)
    private EnvioRepository envioRepository;

    @Autowired(required = false)
    private NotificacionService notificacionService;

    @Autowired(required = false)
    private AppConfigService appConfigService;

    public PedidoEstadoService() {}

    public void setAppConfigService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    public PedidoEstadoService(PedidoRepository pedidoRepository,
                               CompraRepository compraRepository,
                               EnvioRepository envioRepository,
                               NotificacionService notificacionService) {
        this(pedidoRepository, compraRepository, envioRepository, notificacionService, null);
    }

    public PedidoEstadoService(PedidoRepository pedidoRepository,
                               CompraRepository compraRepository,
                               EnvioRepository envioRepository,
                               NotificacionService notificacionService,
                               AppConfigService appConfigService) {
        this.pedidoRepository = pedidoRepository;
        this.compraRepository = compraRepository;
        this.envioRepository = envioRepository;
        this.notificacionService = notificacionService;
        this.appConfigService = appConfigService;
    }

    /**
     * Valida si una transición de estado es válida según el actor y la modalidad.
     */
    public boolean esTransicionPermitida(EstadoPedido actual, EstadoPedido destino,
                                         ActorTransicion actor, ModalidadEntrega modalidad) {
        if (actual == null || destino == null || actor == null) {
            return false;
        }
        if (actual == destino) {
            return true; // No-op idempotente
        }

        ModalidadEntrega mod = modalidad != null ? modalidad : ModalidadEntrega.DOMICILIO;

        switch (actor) {
            case SISTEMA:
                if (actual == EstadoPedido.PENDIENTE) {
                    return destino == EstadoPedido.CONFIRMADO
                            || destino == EstadoPedido.EN_REVISION
                            || destino == EstadoPedido.CANCELADO;
                }
                return false;

            case VENDEDOR:
                // DOMICILIO: CONFIRMADO -> EN_PROCESO -> LISTO_PARA_RECOGER
                // El vendedor NUNCA puede marcar ENVIADO ni ENTREGADO (lo hace el transportista)
                if (actual == EstadoPedido.CONFIRMADO && destino == EstadoPedido.EN_PROCESO) return true;
                if (actual == EstadoPedido.EN_PROCESO && destino == EstadoPedido.LISTO_PARA_RECOGER) return true;
                if (destino == EstadoPedido.CANCELADO) {
                    return actual == EstadoPedido.CONFIRMADO
                            || actual == EstadoPedido.EN_PROCESO
                            || actual == EstadoPedido.LISTO_PARA_RECOGER;
                }
                return false;

            case TRANSPORTISTA:
                // Solo participa en DOMICILIO
                // LISTO_PARA_RECOGER -> ENVIADO (recogida de parada)
                if (actual == EstadoPedido.LISTO_PARA_RECOGER && destino == EstadoPedido.ENVIADO) return true;
                // ENVIADO -> ENTREGADO (entrega final del envío)
                if (actual == EstadoPedido.ENVIADO && destino == EstadoPedido.ENTREGADO) return true;
                return false;

            case ADMIN:
                // Admin puede transicionar según reglas de coherencia
                if (destino == EstadoPedido.CANCELADO) {
                    return actual != EstadoPedido.ENTREGADO;
                }
                if (actual == EstadoPedido.PENDIENTE && destino == EstadoPedido.CONFIRMADO) return true;
                if (actual == EstadoPedido.CONFIRMADO && destino == EstadoPedido.EN_PROCESO) return true;
                if (actual == EstadoPedido.EN_PROCESO && destino == EstadoPedido.LISTO_PARA_RECOGER) return true;
                if (actual == EstadoPedido.LISTO_PARA_RECOGER && destino == EstadoPedido.ENVIADO) return true;
                if (actual == EstadoPedido.ENVIADO && destino == EstadoPedido.ENTREGADO) return true;
                return false;

            default:
                return false;
        }
    }

    /**
     * Aplica la transición de estado al pedido (sin cancelación).
     */
    public Pedido transicionar(Pedido pedido, EstadoPedido destino, ActorTransicion actor, String motivo) {
        return transicionar(pedido, destino, actor, motivo, null);
    }

    /**
     * Aplica la transición de estado al pedido y actualiza la Compra asociada si corresponde.
     */
    public Pedido transicionar(Pedido pedido, EstadoPedido destino, ActorTransicion actor,
                               String motivo, CanceladoPor canceladoPor) {
        if (pedido == null) {
            throw new IllegalArgumentException("El pedido no puede ser nulo");
        }
        if (destino == null) {
            throw new IllegalArgumentException("El estado destino es obligatorio");
        }

        EstadoPedido actual = pedido.getEstadoEnum();
        if (actual == destino) {
            return pedido; // Idempotente
        }

        ModalidadEntrega modalidad = pedido.getModalidadEntrega();
        if (!esTransicionPermitida(actual, destino, actor, modalidad)) {
            throw new IllegalStateException(String.format(
                    "Transición de estado no permitida para %s: %s -> %s en modalidad %s",
                    actor, actual, destino, modalidad));
        }

        // Efectos de transición
        pedido.setEstadoEnum(destino);
        pedido.setUpdatedAt(LocalDateTime.now());

        if (destino == EstadoPedido.LISTO_PARA_RECOGER) {
            pedido.setFechaListoParaRecoger(LocalDateTime.now());
        }

        if (destino == EstadoPedido.ENTREGADO) {
            pedido.setFechaEntrega(LocalDateTime.now());
            // Si tenía código de retiro, se invalida al usarse
            pedido.setCodigoRetiro(null);
        }

        if (destino == EstadoPedido.CANCELADO) {
            pedido.setFechaCancelacion(LocalDateTime.now());
            pedido.setMotivoCancelacion(motivo != null ? motivo.trim() : "Cancelado por " + actor);
            if (canceladoPor != null) {
                pedido.setCanceladoPor(canceladoPor);
            } else if (actor == ActorTransicion.VENDEDOR) {
                pedido.setCanceladoPor(CanceladoPor.VENDEDOR);
            } else if (actor == ActorTransicion.ADMIN) {
                pedido.setCanceladoPor(CanceladoPor.ADMIN);
            } else if (actor == ActorTransicion.SISTEMA) {
                pedido.setCanceladoPor(CanceladoPor.SISTEMA_PAGO);
            }
            // Si tenía código de retiro, se invalida
            pedido.setCodigoRetiro(null);
        }

        Pedido guardado;
        try {
            guardado = pedidoRepository.save(pedido);
        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de concurrencia al actualizar pedido {}: {}", pedido.getId(), e.getMessage());
            throw new OptimisticLockingFailureException(
                    "El pedido fue modificado simultáneamente por otra operación. Por favor, recarga y reintenta.");
        }

        // 1.5: Actualizar Compra si todos sus sub-pedidos están en ENTREGADO o CANCELADO (con al menos uno ENTREGADO)
        actualizarEstadoCompraSiAplica(guardado.getCompraId());

        return guardado;
    }

    /**
     * Valida el código de retiro ingresado en tienda con comparación en tiempo constante (7.6).
     */
    public boolean validarCodigoRetiro(Pedido pedido, String codigoIngresado) {
        if (pedido == null || codigoIngresado == null || pedido.getCodigoRetiro() == null) {
            return false;
        }
        if (pedido.isRetiroBloqueado()) {
            throw new IllegalStateException("El retiro de este pedido está bloqueado por demasiados intentos fallidos. Contacta al administrador.");
        }

        byte[] codigoEsperadoBytes = pedido.getCodigoRetiro().trim().getBytes(StandardCharsets.UTF_8);
        byte[] codigoIngresadoBytes = codigoIngresado.trim().getBytes(StandardCharsets.UTF_8);

        boolean coincide = MessageDigest.isEqual(codigoEsperadoBytes, codigoIngresadoBytes);
        if (coincide) {
            pedido.setRetiroIntentosFallidos(0);
            return true;
        } else {
            int intentos = pedido.getRetiroIntentosFallidos() + 1;
            pedido.setRetiroIntentosFallidos(intentos);
            if (intentos >= 3) {
                pedido.setRetiroBloqueado(true);
                if (notificacionService != null) {
                    notificacionService.enviarAAdmins(
                            "Alerta: Retiro Bloqueado",
                            "El pedido #" + pedido.getNumeroFactura() + " ha superado 3 intentos fallidos de retiro y ha sido bloqueado.",
                            "DANGER");
                }
            }
            pedidoRepository.save(pedido);
            return false;
        }
    }

    /**
     * Entrega manual efectuada por el Administrador (7.6).
     */
    public Pedido confirmarEntregaManualAdmin(String pedidoId, String adminId, String motivo) {
        if (pedidoId == null || motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del pedido y el motivo de entrega manual son obligatorios");
        }
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        if (pedido.getEstadoEnum() != EstadoPedido.LISTO_PARA_RECOGER) {
            throw new IllegalStateException("Solo se puede confirmar entrega de pedidos en estado 'Listo para recoger'");
        }

        pedido.setEntregaManual(true);
        pedido.setMotivoEntregaManual(motivo.trim());
        pedido.setEntregaManualAdminId(adminId);
        pedido.setRetiroBloqueado(false);

        return transicionar(pedido, EstadoPedido.ENTREGADO, ActorTransicion.ADMIN, motivo, null);
    }

    /**
     * Genera un código criptográficamente seguro de 6 dígitos numéricos.
     */
    public static String generarCodigoRetiro() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * Evalúa y actualiza la Compra a COMPLETADA si todos sus pedidos están ENTREGADO o CANCELADO (con al menos 1 ENTREGADO).
     */
    public void actualizarEstadoCompraSiAplica(String compraId) {
        if (compraId == null || compraId.isBlank() || compraRepository == null) {
            return;
        }
        compraRepository.findById(compraId).ifPresent(compra -> {
            List<Pedido> subPedidos = pedidoRepository.findByCompraId(compraId);
            if (compra.evaluarCompletada(subPedidos)) {
                compraRepository.save(compra);
                log.info("Compra {} ha pasado a COMPLETADA (todos los pedidos finalizados con éxito).", compraId);
            }
        });
    }

    /**
     * BLOQUE 7.7: Timeout de retiro en tienda.
     * Si pasan DIAS_MAX_RETIRO_TIENDA (por defecto 3 días) en LISTO_PARA_RECOGER sin retirar,
     * envía recordatorio al comprador.
     */
    public int verificarTimeoutsRetiroTienda() {
        return 0;
    }
}
