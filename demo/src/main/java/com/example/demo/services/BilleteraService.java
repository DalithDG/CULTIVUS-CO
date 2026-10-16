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

    private final BilleteraAlmacen almacen;
    private final AppConfigService configService;

    @Autowired
    public BilleteraService(BilleteraAlmacen almacen, AppConfigService configService) {
        this.almacen = almacen;
        this.configService = configService;
    }

    /** Comisión en puntos básicos (8 % = 800), acotada a [0, 10000]. */
    public int comisionPuntosBasicos() {
        double pct = configService != null
                ? configService.obtenerValorDouble(CLAVE_COMISION, COMISION_POR_DEFECTO)
                : COMISION_POR_DEFECTO;
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
}
