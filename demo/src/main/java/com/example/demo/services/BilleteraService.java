package com.example.demo.services;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.MovimientoBilletera;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.Compra;
import com.example.demo.Model.embebidos.ProductoPedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * US-22: reparto de una compra aprobada entre los vendedores.
 *
 * Por cada sub-pedido aprobado abona al vendedor el subtotal de SUS PRODUCTOS (sin envío) y
 * descuenta la comisión de la plataforma, ambos en saldo EN_RETENCION y como movimientos
 * separados (ABONO positivo, COMISION negativo). Todo en centavos enteros.
 *
 * Comisión: porcentaje configurable (COMISION_VENTA_PORCENTAJE, 8 % por defecto), solo sobre
 * productos. El vendedor recibe el piso de su parte y la plataforma se queda con el resto, de modo
 * que ABONO + COMISION = subtotal exacto y el residuo de centavos queda siempre en la plataforma.
 *
 * Idempotente: cada movimiento lleva una clave única ("ABONO:pedidoId", "COMISION:pedidoId");
 * un reintento (webhook repetido o consulta de respaldo) no inserta de nuevo ni toca el saldo.
 */
@Service
public class BilleteraService {

    private static final Logger log = LoggerFactory.getLogger(BilleteraService.class);

    public static final String CLAVE_COMISION = "COMISION_VENTA_PORCENTAJE";
    public static final double COMISION_POR_DEFECTO = 8.0;

    public static final String CLAVE_COMISION_FLETE = "COMISION_FLETE_PORCENTAJE";
    public static final double COMISION_FLETE_POR_DEFECTO = 0.0;

    public static final String CLAVE_VENTANA_RECLAMOS = "VENTANA_RECLAMOS_MINUTOS";
    public static final int VENTANA_RECLAMOS_DEFECTO = 1440;

    private final BilleteraAlmacen almacen;
    private final AppConfigService configService;

    @Autowired(required = false)
    private com.example.demo.repository.PedidoRepository pedidoRepository;

    @Autowired(required = false)
    private com.example.demo.repository.EnvioRepository envioRepository;

    @Autowired
    public BilleteraService(BilleteraAlmacen almacen, AppConfigService configService) {
        this.almacen = almacen;
        this.configService = configService;
    }

    public BilleteraService(BilleteraAlmacen almacen, AppConfigService configService,
                            com.example.demo.repository.PedidoRepository pedidoRepository,
                            com.example.demo.repository.EnvioRepository envioRepository) {
        this.almacen = almacen;
        this.configService = configService;
        this.pedidoRepository = pedidoRepository;
        this.envioRepository = envioRepository;
    }

    /** Comisión en puntos básicos (8 % = 800), acotada a [0, 10000]. */
    public int comisionPuntosBasicos() {
        double pct = configService != null
                ? configService.obtenerValorDouble(CLAVE_COMISION, COMISION_POR_DEFECTO)
                : COMISION_POR_DEFECTO;
        long bps = Math.round(pct * 100.0);
        return (int) Math.max(0, Math.min(10_000, bps));
    }

    public int comisionFletePuntosBasicos() {
        double pct = configService != null
                ? configService.obtenerValorDouble(CLAVE_COMISION_FLETE, COMISION_FLETE_POR_DEFECTO)
                : COMISION_FLETE_POR_DEFECTO;
        long bps = Math.round(pct * 100.0);
        return (int) Math.max(0, Math.min(10_000, bps));
    }

    /** Parte del vendedor: piso de subtotal * (1 - comisión). Todo en enteros. */
    public static long calcularNeto(long subtotalCentavos, int comisionBps) {
        return subtotalCentavos * (10_000L - comisionBps) / 10_000L;
    }

    /**
     * Abona a cada vendedor los productos de sus sub-pedidos aprobados. Solo debe llamarse con
     * pedidos cuyo pago quedó APROBADO; con un pago fallido no se invoca y no se registra nada.
     */
    public void registrarRepartoVentas(Compra compra, List<Pedido> pedidosAprobados) {
        if (pedidosAprobados == null || pedidosAprobados.isEmpty()) {
            return;
        }
        int bps = comisionPuntosBasicos();
        String compraId = compra != null ? compra.getId() : null;

        for (Pedido pedido : pedidosAprobados) {
            if (pedido.getId() == null || pedido.getVendedor() == null || pedido.getVendedor().getId() == null) {
                log.warn("Pedido sin id o sin vendedor: no se puede abonar a la billetera");
                continue;
            }
            long subtotal = subtotalProductosCentavos(pedido);
            if (subtotal <= 0) {
                continue;
            }
            long neto = calcularNeto(subtotal, bps);
            long comision = subtotal - neto; // incluye el residuo de centavos: queda para la plataforma
            String cid = compraId != null ? compraId : pedido.getCompraId();

            Billetera billetera = almacen.obtenerOCrear(pedido.getVendedor().getId(), Billetera.Rol.VENDEDOR);

            MovimientoBilletera abono = MovimientoBilletera.nuevo(billetera, MovimientoBilletera.Tipo.ABONO,
                    Billetera.EstadoSaldo.EN_RETENCION, subtotal, pedido.getId(), cid,
                    "Venta del pedido " + pedido.getNumeroFactura(), "ABONO:" + pedido.getId());
            if (almacen.insertarMovimiento(abono)) {
                almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.EN_RETENCION, subtotal);
            }

            if (comision > 0) {
                MovimientoBilletera mov = MovimientoBilletera.nuevo(billetera, MovimientoBilletera.Tipo.COMISION,
                        Billetera.EstadoSaldo.EN_RETENCION, -comision, pedido.getId(), cid,
                        "Comisión de plataforma (" + (bps / 100.0) + "%) del pedido " + pedido.getNumeroFactura(),
                        "COMISION:" + pedido.getId());
                if (almacen.insertarMovimiento(mov)) {
                    almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.EN_RETENCION, -comision);
                }
            }
        }
    }

    /**
     * US-22 / 3.2: Revierte los movimientos de billetera (ABONO y COMISION) de un pedido cancelado.
     * Solo opera si el movimiento original de ABONO existe.
     * Idempotente mediante claves "REVERSO_ABONO:pedidoId" y "REVERSO_COMISION:pedidoId".
     */
    public void revertirVentaCancelada(Pedido pedido) {
        if (pedido == null || pedido.getId() == null || pedido.getVendedor() == null || pedido.getVendedor().getId() == null) {
            return;
        }

        String claveAbonoOriginal = "ABONO:" + pedido.getId();
        if (!almacen.existeMovimientoPorClave(claveAbonoOriginal)) {
            log.info("No existe movimiento de ABONO para el pedido {}, omitiendo reverso en billetera", pedido.getId());
            return;
        }

        long subtotal = subtotalProductosCentavos(pedido);
        if (subtotal <= 0) {
            return;
        }

        int bps = comisionPuntosBasicos();
        long neto = calcularNeto(subtotal, bps);
        long comision = subtotal - neto;
        String cid = pedido.getCompraId();

        Billetera billetera = almacen.obtenerOCrear(pedido.getVendedor().getId(), Billetera.Rol.VENDEDOR);

        // Reverso del ABONO (-subtotal en retención)
        MovimientoBilletera revAbono = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.REVERSO,
                Billetera.EstadoSaldo.EN_RETENCION,
                -subtotal,
                pedido.getId(),
                cid,
                "Reverso de venta por cancelación del pedido #" + pedido.getNumeroFactura(),
                "REVERSO_ABONO:" + pedido.getId()
        );
        if (almacen.insertarMovimiento(revAbono)) {
            almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.EN_RETENCION, -subtotal);
            log.info("Reverso de abono aplicado al vendedor {} por {} centavos (pedido {})",
                    pedido.getVendedor().getId(), subtotal, pedido.getId());
        }

        // Reverso de la COMISIÓN (+comision en retención para devolver el descuento previo)
        if (comision > 0) {
            MovimientoBilletera revComision = MovimientoBilletera.nuevo(
                    billetera,
                    MovimientoBilletera.Tipo.REVERSO,
                    Billetera.EstadoSaldo.EN_RETENCION,
                    comision,
                    pedido.getId(),
                    cid,
                    "Reverso de comisión por cancelación del pedido #" + pedido.getNumeroFactura(),
                    "REVERSO_COMISION:" + pedido.getId()
            );
            if (almacen.insertarMovimiento(revComision)) {
                almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.EN_RETENCION, comision);
                log.info("Reverso de comisión aplicado al vendedor {} por {} centavos (pedido {})",
                        pedido.getVendedor().getId(), comision, pedido.getId());
            }
        }
    }

    /**
     * Abona al transportista el flete de un envío entregado. Idempotente por envío.
     */
    public void registrarFleteEntregado(com.example.demo.Model.Envio envio) {
        if (envio == null || envio.getId() == null || envio.getTransportistaId() == null) {
            return;
        }
        long flete = envio.getCostoTotalCentavos() != null ? envio.getCostoTotalCentavos() : 0L;
        if (flete <= 0) {
            return;
        }
        int bps = comisionFletePuntosBasicos();
        long comision = flete * bps / 10_000L;
        long abono = flete - comision;

        String compraId = envio.getCompraId();
        Billetera billetera = almacen.obtenerOCrear(envio.getTransportistaId(), Billetera.Rol.TRANSPORTISTA);

        MovimientoBilletera movAbono = MovimientoBilletera.nuevo(billetera, MovimientoBilletera.Tipo.ABONO,
                Billetera.EstadoSaldo.EN_RETENCION, abono, null, compraId,
                "Flete de la compra #" + (compraId != null ? compraId : envio.getId()),
                "FLETE:" + envio.getId());
        if (almacen.insertarMovimiento(movAbono)) {
            almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.EN_RETENCION, abono);
        }

        if (comision > 0) {
            MovimientoBilletera movComision = MovimientoBilletera.nuevo(billetera, MovimientoBilletera.Tipo.COMISION,
                    Billetera.EstadoSaldo.EN_RETENCION, -comision, null, compraId,
                    "Comisión de plataforma del flete de la compra #" + (compraId != null ? compraId : envio.getId()),
                    "COMISION_FLETE:" + envio.getId());
            if (almacen.insertarMovimiento(movComision)) {
                almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.EN_RETENCION, -comision);
            }
        }
    }

    /** Subtotal de productos del sub-pedido en centavos (el envío nunca entra aquí). */
    static long subtotalProductosCentavos(Pedido pedido) {
        if (pedido.getSubtotalCentavos() != null) {
            return pedido.getSubtotalCentavos();
        }
        long suma = 0;
        if (pedido.getItems() != null) {
            for (ProductoPedido item : pedido.getItems()) {
                if (item.getSubtotal() != null) {
                    suma += Math.round(item.getSubtotal() * 100.0);
                }
            }
        }
        if (suma == 0 && pedido.getTotal() != null) {
            suma = Math.round(pedido.getTotal() * 100.0);
        }
        return suma;
    }

    // ── Lectura para la vista del vendedor ──

    public Optional<Billetera> obtenerBilleteraVendedor(String vendedorId) {
        return almacen.buscar(vendedorId, Billetera.Rol.VENDEDOR);
    }

    public Billetera obtenerOCrearBilleteraVendedor(String vendedorId) {
        return almacen.obtenerOCrear(vendedorId, Billetera.Rol.VENDEDOR);
    }

    public List<MovimientoBilletera> listarMovimientosVendedor(String vendedorId) {
        return almacen.listarMovimientos(vendedorId, Billetera.Rol.VENDEDOR);
    }

    // --- Transportista ---

    public Optional<Billetera> obtenerBilleteraTransportista(String transportistaId) {
        return almacen.buscar(transportistaId, Billetera.Rol.TRANSPORTISTA);
    }

    public Billetera obtenerOCrearBilleteraTransportista(String transportistaId) {
        return almacen.obtenerOCrear(transportistaId, Billetera.Rol.TRANSPORTISTA);
    }

    public List<MovimientoBilletera> listarMovimientosTransportista(String transportistaId) {
        return almacen.listarMovimientos(transportistaId, Billetera.Rol.TRANSPORTISTA);
    }

    // ── US-24 / Fase 0: Liberación de fondos a DISPONIBLE tras ventana de reclamos ──

    public int ventanaReclamosMinutos() {
        return configService != null
                ? configService.obtenerValorInt(CLAVE_VENTANA_RECLAMOS, VENTANA_RECLAMOS_DEFECTO)
                : VENTANA_RECLAMOS_DEFECTO;
    }

    /**
     * Mueve el saldo neto retenido de un pedido de EN_RETENCION a DISPONIBLE.
     * Solo opera para pedidos ENTREGADOS que no estén en revisión ni tengan reclamo abierto.
     * El monto es lo realmente registrado para ese pedido (abono + comisiones históricas).
     * Idempotente mediante claves únicas por pedido.
     */
    public boolean liberarSaldoPedido(Pedido pedido) {
        if (pedido == null || pedido.getId() == null || pedido.getVendedor() == null || pedido.getVendedor().getId() == null) {
            return false;
        }

        if (!Pedido.ENTREGADO.equals(pedido.getEstado()) && !"ENTREGADO".equals(pedido.getEstado())) {
            return false;
        }

        if (pedido.isRequiereRevision() || "EN_REVISION".equals(pedido.getEstado())) {
            log.info("Pedido {} en revisión o con reclamo: no se libera saldo", pedido.getId());
            return false;
        }

        String claveRet = "LIBERACION_RETENCION:PEDIDO:" + pedido.getId();
        String claveDisp = "LIBERACION_DISPONIBLE:PEDIDO:" + pedido.getId();

        if (almacen.existeMovimientoPorClave(claveRet) || almacen.existeMovimientoPorClave(claveDisp)) {
            log.debug("Saldo de pedido {} ya liberado previamente", pedido.getId());
            return false;
        }

        Optional<MovimientoBilletera> abonoOpt = almacen.buscarMovimientoPorClave("ABONO:" + pedido.getId());
        if (abonoOpt.isEmpty()) {
            log.debug("No existe movimiento de abono para pedido {}, nada que liberar", pedido.getId());
            return false;
        }

        long montoAbono = abonoOpt.get().getMontoCentavos();
        long montoComision = almacen.buscarMovimientoPorClave("COMISION:" + pedido.getId())
                .map(MovimientoBilletera::getMontoCentavos)
                .orElse(0L); // negativo

        long montoNeto = montoAbono + montoComision;
        if (montoNeto <= 0) {
            return false;
        }

        Billetera billetera = almacen.obtenerOCrear(pedido.getVendedor().getId(), Billetera.Rol.VENDEDOR);
        String factura = pedido.getNumeroFactura() != null ? pedido.getNumeroFactura() : pedido.getId();

        MovimientoBilletera movRet = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.LIBERACION,
                Billetera.EstadoSaldo.EN_RETENCION,
                -montoNeto,
                pedido.getId(),
                pedido.getCompraId(),
                "Liberación a disponible por entrega del pedido #" + factura,
                claveRet
        );
        if (almacen.insertarMovimiento(movRet)) {
            almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.EN_RETENCION, -montoNeto);
        }

        MovimientoBilletera movDisp = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.LIBERACION,
                Billetera.EstadoSaldo.DISPONIBLE,
                montoNeto,
                pedido.getId(),
                pedido.getCompraId(),
                "Liberación a disponible por entrega del pedido #" + factura,
                claveDisp
        );
        if (almacen.insertarMovimiento(movDisp)) {
            almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.DISPONIBLE, montoNeto);
        }

        log.info("Fondos liberados a DISPONIBLE para vendedor {} por {} centavos (pedido {})",
                pedido.getVendedor().getId(), montoNeto, pedido.getId());
        return true;
    }

    /**
     * Mueve el saldo neto de flete de EN_RETENCION a DISPONIBLE para el transportista.
     * Solo opera para envíos ENTREGADOS.
     * Idempotente mediante claves únicas por envío.
     */
    public boolean liberarSaldoEnvio(com.example.demo.Model.Envio envio) {
        if (envio == null || envio.getId() == null || envio.getTransportistaId() == null) {
            return false;
        }

        if (!com.example.demo.Model.Envio.ENTREGADO.equals(envio.getEstado()) && !"ENTREGADO".equals(envio.getEstado())) {
            return false;
        }

        String claveRet = "LIBERACION_RETENCION:ENVIO:" + envio.getId();
        String claveDisp = "LIBERACION_DISPONIBLE:ENVIO:" + envio.getId();

        if (almacen.existeMovimientoPorClave(claveRet) || almacen.existeMovimientoPorClave(claveDisp)) {
            log.debug("Flete de envío {} ya liberado previamente", envio.getId());
            return false;
        }

        Optional<MovimientoBilletera> fleteOpt = almacen.buscarMovimientoPorClave("FLETE:" + envio.getId());
        if (fleteOpt.isEmpty()) {
            log.debug("No existe movimiento de flete para envío {}, nada que liberar", envio.getId());
            return false;
        }

        long montoFlete = fleteOpt.get().getMontoCentavos();
        long montoComisionFlete = almacen.buscarMovimientoPorClave("COMISION_FLETE:" + envio.getId())
                .map(MovimientoBilletera::getMontoCentavos)
                .orElse(0L); // negativo

        long montoNeto = montoFlete + montoComisionFlete;
        if (montoNeto <= 0) {
            return false;
        }

        Billetera billetera = almacen.obtenerOCrear(envio.getTransportistaId(), Billetera.Rol.TRANSPORTISTA);
        String envioRef = envio.getCompraId() != null ? envio.getCompraId() : envio.getId();

        MovimientoBilletera movRet = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.LIBERACION,
                Billetera.EstadoSaldo.EN_RETENCION,
                -montoNeto,
                null,
                envio.getCompraId(),
                "Liberación de flete a disponible por envío #" + envioRef,
                claveRet
        );
        if (almacen.insertarMovimiento(movRet)) {
            almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.EN_RETENCION, -montoNeto);
        }

        MovimientoBilletera movDisp = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.LIBERACION,
                Billetera.EstadoSaldo.DISPONIBLE,
                montoNeto,
                null,
                envio.getCompraId(),
                "Liberación de flete a disponible por envío #" + envioRef,
                claveDisp
        );
        if (almacen.insertarMovimiento(movDisp)) {
            almacen.incrementarSaldo(billetera.getId(), Billetera.EstadoSaldo.DISPONIBLE, montoNeto);
        }

        log.info("Flete liberado a DISPONIBLE para transportista {} por {} centavos (envío {})",
                envio.getTransportistaId(), montoNeto, envio.getId());
        return true;
    }

    /**
     * Revisa pedidos y envíos entregados con ventana de reclamos cumplida
     * y libera su saldo a DISPONIBLE.
     */
    public int procesarLiberacionesPendientes() {
        int ventana = ventanaReclamosMinutos();
        java.time.LocalDateTime limite = java.time.LocalDateTime.now().minusMinutes(ventana);
        int totalLiberados = 0;

        if (pedidoRepository != null) {
            try {
                List<Pedido> pedidos = pedidoRepository.findByEstado(Pedido.ENTREGADO);
                for (Pedido p : pedidos) {
                    if (p.getFechaEntrega() != null && !p.getFechaEntrega().isAfter(limite)) {
                        if (liberarSaldoPedido(p)) {
                            totalLiberados++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error al procesar liberación de pedidos: {}", e.getMessage(), e);
            }
        }

        if (envioRepository != null) {
            try {
                List<com.example.demo.Model.Envio> envios = envioRepository.findByEstado(com.example.demo.Model.Envio.ENTREGADO);
                for (com.example.demo.Model.Envio e : envios) {
                    if (e.getFechaEntrega() != null && !e.getFechaEntrega().isAfter(limite)) {
                        if (liberarSaldoEnvio(e)) {
                            totalLiberados++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error al procesar liberación de envíos: {}", e.getMessage(), e);
            }
        }

        return totalLiberados;
    }
}
