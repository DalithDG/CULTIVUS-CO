package com.example.demo.services;

import com.example.demo.Model.ActorTransicion;
import com.example.demo.Model.Compra;
import com.example.demo.Model.Envio;
import com.example.demo.Model.EstadoPedido;
import com.example.demo.Model.CanceladoPor;
import com.example.demo.Model.ModalidadEntrega;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DireccionPedido;
import com.example.demo.Model.embebidos.ParadaEnvio;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pieza compartida por el webhook de Wompi y por el retorno del comprador
 * (/pago/resultado): aplica el resultado de una transacción a las compras, pagos y pedidos.
 *
 * APPROVED                  -> COMPLETADO (pago), PAGADA (compra), CONFIRMADO (pedidos con stock),
 *                              descuenta stock de forma atómica y condicionada (stock >= cant),
 *                              notifica y limpia carrito.
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

    @Autowired(required = false)
    private com.example.demo.repository.EnvioRepository envioRepository;

    @Autowired(required = false)
    private com.example.demo.repository.UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private CalculoEnvioService calculoEnvioService;

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private PedidoEstadoService pedidoEstadoService;

    public PagoConfirmacionService() {
    }

    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void setPedidoEstadoService(PedidoEstadoService pedidoEstadoService) {
        this.pedidoEstadoService = pedidoEstadoService;
    }

    /** Para pruebas: inyecta el servicio de billetera (US-22). */
    public void setBilleteraService(BilleteraService billeteraService) {
        this.billeteraService = billeteraService;
    }

    public void setEnvioRepository(com.example.demo.repository.EnvioRepository envioRepository) {
        this.envioRepository = envioRepository;
    }

    public void setUsuarioRepository(com.example.demo.repository.UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public void setCalculoEnvioService(CalculoEnvioService calculoEnvioService) {
        this.calculoEnvioService = calculoEnvioService;
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
            // US-03 / BLOQUE 6: Descuento atómico condicionado de stock (stock >= cantidad)
            // y transición centralizada PENDIENTE -> CONFIRMADO vía PedidoEstadoService.
            // Si el stock no alcanza al confirmar, marca EN_REVISION (requiereRevision=true) y notifica al admin.
            List<Pedido> pedidosAprobados = new ArrayList<>();
            Map<Pedido, String> pedidosEnRevision = new LinkedHashMap<>();

            if (pedidos != null) {
                for (Pedido p : pedidos) {
                    // Idempotencia: si ya está CONFIRMADO o posterior, o stock ya descontado, no re-descontar
                    EstadoPedido est = p.getEstadoEnum();
                    if (est == EstadoPedido.CONFIRMADO || est == EstadoPedido.EN_PROCESO
                            || est == EstadoPedido.LISTO_PARA_RECOGER || est == EstadoPedido.ENVIADO
                            || est == EstadoPedido.ENTREGADO || p.isStockDescontado()) {
                        log.info("Pedido {} ya en estado {} (stockDescontado={}), se omite descuento de stock.",
                                p.getId(), est, p.isStockDescontado());
                        pedidosAprobados.add(p);
                        continue;
                    }

                    if (est == EstadoPedido.EN_REVISION) {
                        pedidosEnRevision.put(p, p.getMotivoRevision() != null ? p.getMotivoRevision() : "En revisión de stock previa");
                        continue;
                    }

                    if (est == EstadoPedido.CANCELADO) {
                        continue;
                    }

                    // Intento de descuento atómico condicionado de stock por cada ítem del pedido
                    boolean stockAlcanza = true;
                    String motivoFalla = null;
                    List<ItemDescontado> itemsDescontados = new ArrayList<>();

                    if (p.getItems() != null && !p.getItems().isEmpty()) {
                        for (ProductoPedido item : p.getItems()) {
                            String idOferta = item.getProductoId();
                            double cant = item.getCantidad() != null ? item.getCantidad() : 0.0;
                            if (idOferta == null || cant <= 0.0) {
                                continue;
                            }

                            if (mongoTemplate != null) {
                                Query q = new Query(Criteria.where("_id").is(idOferta).and("stock").gte(cant));
                                Update u = new Update().inc("stock", -cant);
                                OfertaVendedor mod = mongoTemplate.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), OfertaVendedor.class);
                                if (mod != null) {
                                    if (mod.getStock() != null && mod.getStock() <= 0.0) {
                                        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(idOferta)),
                                                new Update().set("disponible", false), OfertaVendedor.class);
                                    }
                                    itemsDescontados.add(new ItemDescontado(idOferta, cant, mod.getProductoCatalogoId()));
                                } else {
                                    stockAlcanza = false;
                                    motivoFalla = "Stock insuficiente para '" + item.getNombreSnapshot() + "'. Requerido: " + cant;
                                    break;
                                }
                            } else if (ofertaRepository != null) {
                                synchronized (this) {
                                    OfertaVendedor of = ofertaRepository.findById(idOferta).orElse(null);
                                    double actual = (of != null && of.getStock() != null) ? of.getStock() : 0.0;
                                    if (of != null && actual >= cant) {
                                        double nuevo = actual - cant;
                                        of.setStock(nuevo);
                                        if (nuevo <= 0.0) of.setDisponible(false);
                                        ofertaRepository.save(of);
                                        itemsDescontados.add(new ItemDescontado(idOferta, cant, of.getProductoCatalogoId()));
                                    } else {
                                        stockAlcanza = false;
                                        motivoFalla = "Stock insuficiente para '" + item.getNombreSnapshot()
                                                + "'. Requerido: " + cant + ", disponible: " + actual;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (stockAlcanza) {
                        p.setStockDescontado(true);
                        pedidosAprobados.add(p);
                        if (catalogoService != null) {
                            for (ItemDescontado idesc : itemsDescontados) {
                                if (idesc.catalogoId != null) {
                                    try {
                                        catalogoService.actualizarEstadisticas(idesc.catalogoId);
                                    } catch (Exception ex) {
                                        log.warn("Error al actualizar estadísticas de catálogo {}: {}", idesc.catalogoId, ex.getMessage());
                                    }
                                }
                            }
                        }
                    } else {
                        // Rollback de ítems que alcanzaron a descontarse para este pedido fallido
                        for (ItemDescontado idesc : itemsDescontados) {
                            if (mongoTemplate != null) {
                                mongoTemplate.findAndModify(
                                        new Query(Criteria.where("_id").is(idesc.ofertaId)),
                                        new Update().inc("stock", idesc.cantidad).set("disponible", true),
                                        OfertaVendedor.class);
                            } else if (ofertaRepository != null) {
                                synchronized (this) {
                                    ofertaRepository.findById(idesc.ofertaId).ifPresent(o -> {
                                        o.setStock((o.getStock() != null ? o.getStock() : 0.0) + idesc.cantidad);
                                        o.setDisponible(true);
                                        ofertaRepository.save(o);
                                    });
                                }
                            }
                        }
                        pedidosEnRevision.put(p, motivoFalla != null ? motivoFalla : "Stock insuficiente");
                    }
                }
            }

            // US-22: reparto a billeteras (idempotente)
            abonarBilleteraPorCompra(compra, pedidosAprobados);

            // 4. Guardar pedidos aprobados y notificar a cada vendedor
            for (Pedido p : pedidosAprobados) {
                p.setStockDescontado(true);
                p.setRequiereRevision(false);
                p.setMotivoRevision(null);

                boolean yaEstabaConfirmado = p.getEstadoEnum() != null && p.getEstadoEnum() != EstadoPedido.PENDIENTE;
                if (!yaEstabaConfirmado) {
                    if (pedidoEstadoService != null) {
                        pedidoEstadoService.transicionar(p, EstadoPedido.CONFIRMADO, ActorTransicion.SISTEMA, "Pago confirmado por pasarela");
                    } else {
                        p.setEstadoEnum(EstadoPedido.CONFIRMADO);
                        p.setUpdatedAt(LocalDateTime.now());
                        if (pedidoRepository != null) {
                            pedidoRepository.save(p);
                        }
                    }

                    if (notificacionService != null && p.getVendedor() != null && p.getVendedor().getId() != null) {
                        String nombreComprador = p.getComprador() != null ? p.getComprador().getNombre() : "un comprador";
                        String refPed = p.getNumeroFactura() != null ? p.getNumeroFactura() : p.getId();
                        notificacionService.enviar(
                                p.getVendedor().getId(),
                                "¡Nueva Venta!",
                                "Has recibido un nuevo pedido #" + refPed + " de " + nombreComprador,
                                "SUCCESS");
                    }
                }
            }

            // 5. Guardar pedidos en revisión y notificar al ADMIN (reembolso manual)
            for (Map.Entry<Pedido, String> entry : pedidosEnRevision.entrySet()) {
                Pedido p = entry.getKey();
                String motivo = entry.getValue();

                p.setStockDescontado(false);
                p.setRequiereRevision(true);
                p.setMotivoRevision(motivo);

                if (pedidoEstadoService != null) {
                    pedidoEstadoService.transicionar(p, EstadoPedido.EN_REVISION, ActorTransicion.SISTEMA, motivo);
                } else {
                    p.setEstadoEnum(EstadoPedido.EN_REVISION);
                    p.setUpdatedAt(LocalDateTime.now());
                    if (pedidoRepository != null) {
                        pedidoRepository.save(p);
                    }
                }

                if (notificacionService != null) {
                    String idRef = p.getId() != null ? p.getId() : p.getNumeroFactura();
                    notificacionService.enviarAAdmins(
                            "Revisión requerida: Pedido #" + idRef,
                            "Stock insuficiente al confirmar el pedido #" + idRef + " de la compra " + refLimpia
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

            // 9. Crear Envio logístico en PENDIENTE_ASIGNACION (uno por compra, con una parada por pedido confirmado)
            try {
                crearEnvioParaCompra(compra, pedidosAprobados);
            } catch (Exception ex) {
                log.error("Error al crear Envio para compra {}: {}", refLimpia, ex.getMessage(), ex);
                if (notificacionService != null) {
                    notificacionService.enviarAAdmins(
                            "Error en logística: Compra " + refLimpia,
                            "El pago fue aprobado pero ocurrió un error al generar el envío logístico: " + ex.getMessage(),
                            "DANGER");
                }
            }

        } else if (FALLIDO.equals(destino)) {
            // US-03 Item 3 / BLOQUE 1 / BLOQUE 6: En pago fallido, cada pedido se cancela sin tocar stock,
            // pasando por PedidoEstadoService si está disponible
            if (pedidos != null) {
                for (Pedido p : pedidos) {
                    if (p.getEstadoEnum() == EstadoPedido.CANCELADO) {
                        continue;
                    }
                    if (pedidoEstadoService != null) {
                        pedidoEstadoService.transicionar(p, EstadoPedido.CANCELADO, ActorTransicion.SISTEMA,
                                "Pago no aprobado por pasarela (" + estadoNuevo + ")", CanceladoPor.SISTEMA_PAGO);
                    } else {
                        p.setEstadoEnum(EstadoPedido.CANCELADO);
                        p.setCanceladoPor(CanceladoPor.SISTEMA_PAGO);
                        p.setMotivoCancelacion("Pago no aprobado por pasarela (" + estadoNuevo + ")");
                        p.setFechaCancelacion(LocalDateTime.now());
                        p.setStockDescontado(false);
                        p.setUpdatedAt(LocalDateTime.now());
                        if (pedidoRepository != null) {
                            pedidoRepository.save(p);
                        }
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
     * FASE 1 Logística: crea el Envio agrupado para la compra en estado PENDIENTE_ASIGNACION.
     * Idempotente: si ya existe para este compraId, no duplica.
     * Solo incluye paradas para pedidos confirmados (excluye EN_REVISION).
     */
    protected void crearEnvioParaCompra(Compra compra, List<Pedido> pedidosAprobados) {
        if (envioRepository == null || pedidosAprobados == null || pedidosAprobados.isEmpty()) {
            return;
        }

        String compraId = (compra != null && compra.getId() != null)
                ? compra.getId()
                : pedidosAprobados.get(0).getCompraId();

        if (compraId == null || compraId.isBlank()) {
            log.warn("No se pudo determinar compraId para crear Envio.");
            return;
        }

        if (envioRepository.existsByCompraId(compraId)) {
            log.info("Envio ya existe para compraId {}, se omite duplicado.", compraId);
            return;
        }

        List<ParadaEnvio> paradas = new ArrayList<>();
        long costoTotalCentavos = 0L;
        DireccionPedido puntoEntrega = null;

        for (Pedido p : pedidosAprobados) {
            if (puntoEntrega == null && p.getDireccionEntrega() != null) {
                puntoEntrega = new DireccionPedido(
                        p.getDireccionEntrega().getDireccion(),
                        p.getDireccionEntrega().getCiudad(),
                        p.getDireccionEntrega().getDepartamento());
            }

            String vendedorId = p.getVendedor() != null ? p.getVendedor().getId() : null;
            String nombreVendedor = (p.getVendedor() != null)
                    ? (p.getVendedor().getRazonSocial() != null && !p.getVendedor().getRazonSocial().isBlank()
                            ? p.getVendedor().getRazonSocial()
                            : p.getVendedor().getNombre())
                    : "Vendedor";

            String ciudadRecogida = "Cartagena";
            String direccionRecogida = "Cartagena, Bolívar";

            if (vendedorId != null && usuarioRepository != null) {
                Usuario vUser = usuarioRepository.findById(vendedorId).orElse(null);
                if (vUser != null) {
                    if (vUser.getUbicacion() != null && vUser.getUbicacion().getCiudad() != null && !vUser.getUbicacion().getCiudad().isBlank()) {
                        ciudadRecogida = vUser.getUbicacion().getCiudad().trim();
                    }
                    if (vUser.getPerfilVendedor() != null && vUser.getPerfilVendedor().getDireccionNegocio() != null
                            && !vUser.getPerfilVendedor().getDireccionNegocio().isBlank()) {
                        direccionRecogida = vUser.getPerfilVendedor().getDireccionNegocio().trim();
                    } else {
                        direccionRecogida = ciudadRecogida + ", Bolívar";
                    }
                }
            }

            Integer zona = null;
            if (calculoEnvioService != null) {
                zona = calculoEnvioService.obtenerZonaPorCiudad(ciudadRecogida).orElse(null);
            }

            long tarifaCentavos = p.getEnvioCentavos() != null ? p.getEnvioCentavos() : 0L;
            costoTotalCentavos += tarifaCentavos;

            ParadaEnvio parada = new ParadaEnvio(
                    p.getId(),
                    vendedorId,
                    nombreVendedor,
                    direccionRecogida,
                    ciudadRecogida,
                    zona,
                    tarifaCentavos
            );

            if (p.getItems() != null) {
                List<String> itemsResumen = p.getItems().stream()
                        .map(i -> {
                            String cant = (i.getCantidad() != null && i.getCantidad() % 1 == 0)
                                    ? String.format(java.util.Locale.US, "%.0f", i.getCantidad())
                                    : String.valueOf(i.getCantidad());
                            String unidad = i.getUnidadMedida() != null ? (" " + i.getUnidadMedida()) : "";
                            String nom = i.getNombreSnapshot() != null ? i.getNombreSnapshot() : "Producto";
                            return cant + unidad + " " + nom;
                        })
                        .collect(Collectors.toList());
                parada.setItemsResumen(itemsResumen);
            }

            paradas.add(parada);
        }

        if (compra != null && compra.getEnvioCentavos() != null && compra.getEnvioCentavos() > 0) {
            costoTotalCentavos = compra.getEnvioCentavos();
        }

        Envio envio = new Envio(compraId, paradas, puntoEntrega, costoTotalCentavos);
        envio.setEstado(Envio.PENDIENTE_ASIGNACION);
        envioRepository.save(envio);
        log.info("✅ Envio creado exitosamente para compraId {} con {} paradas y costo total {} centavos.",
                compraId, paradas.size(), costoTotalCentavos);
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

    private static class ItemDescontado {
        final String ofertaId;
        final double cantidad;
        final String catalogoId;

        ItemDescontado(String ofertaId, double cantidad, String catalogoId) {
            this.ofertaId = ofertaId;
            this.cantidad = cantidad;
            this.catalogoId = catalogoId;
        }
    }
}

