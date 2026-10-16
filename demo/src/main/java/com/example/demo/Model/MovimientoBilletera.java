package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * US-22: asiento INMUTABLE del libro de una billetera.
 *
 * Inmutable por diseño: todos los campos son final y no hay setters. Nunca se actualiza ni se
 * borra: solo se inserta (ver BilleteraAlmacen.insertarMovimiento). Una corrección se hace con
 * un nuevo movimiento AJUSTE, no editando el original.
 *
 * montoCentavos va con signo: ABONO positivo, COMISION negativo, AJUSTE según corresponda.
 * El efecto sobre el saldo es la suma de los montos del movimiento en su estadoSaldo.
 */
@Document(collection = "movimientos_billetera")
@CompoundIndex(name = "idx_mov_usuario_rol_fecha", def = "{'usuario_id': 1, 'rol': 1, 'fecha': -1}")
public final class MovimientoBilletera {

    public enum Tipo {
        ABONO, COMISION, AJUSTE
    }

    @Id
    private final String id;

    @Field("billetera_id")
    private final String billeteraId;

    @Field("usuario_id")
    private final String usuarioId;

    @Field("rol")
    private final Billetera.Rol rol;

    @Field("tipo")
    private final Tipo tipo;

    /** Saldo al que afecta (US-22 solo usa EN_RETENCION). */
    @Field("estado_saldo")
    private final Billetera.EstadoSaldo estadoSaldo;

    /** Centavos enteros con signo. */
    @Field("monto_centavos")
    private final long montoCentavos;

    @Field("pedido_id")
    private final String pedidoId;

    @Field("compra_id")
    private final String compraId;

    @Field("fecha")
    private final LocalDateTime fecha;

    @Field("descripcion")
    private final String descripcion;

    /**
     * Clave de idempotencia ("ABONO:pedidoId", "COMISION:pedidoId"). Única y dispersa: un mismo
     * pedido no puede abonarse ni cobrar comisión dos veces. Null para AJUSTE.
     */
    @Indexed(unique = true, sparse = true)
    @Field("clave_idempotencia")
    private final String claveIdempotencia;

    @PersistenceCreator
    public MovimientoBilletera(String id, String billeteraId, String usuarioId, Billetera.Rol rol,
                               Tipo tipo, Billetera.EstadoSaldo estadoSaldo, long montoCentavos,
                               String pedidoId, String compraId, LocalDateTime fecha,
                               String descripcion, String claveIdempotencia) {
        this.id = id;
        this.billeteraId = billeteraId;
        this.usuarioId = usuarioId;
        this.rol = rol;
        this.tipo = tipo;
        this.estadoSaldo = estadoSaldo;
        this.montoCentavos = montoCentavos;
        this.pedidoId = pedidoId;
        this.compraId = compraId;
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.claveIdempotencia = claveIdempotencia;
    }

    /** Crea un movimiento nuevo con id propio y fecha actual. */
    public static MovimientoBilletera nuevo(Billetera billetera, Tipo tipo, Billetera.EstadoSaldo estadoSaldo,
                                            long montoCentavos, String pedidoId, String compraId,
                                            String descripcion, String claveIdempotencia) {
        return new MovimientoBilletera(new org.bson.types.ObjectId().toHexString(), billetera.getId(),
                billetera.getUsuarioId(), billetera.getRol(), tipo, estadoSaldo, montoCentavos,
                pedidoId, compraId, LocalDateTime.now(), descripcion, claveIdempotencia);
    }

    public String getId() { return id; }
    public String getBilleteraId() { return billeteraId; }
    public String getUsuarioId() { return usuarioId; }
    public Billetera.Rol getRol() { return rol; }
    public Tipo getTipo() { return tipo; }
    public Billetera.EstadoSaldo getEstadoSaldo() { return estadoSaldo; }
    public long getMontoCentavos() { return montoCentavos; }
    public String getPedidoId() { return pedidoId; }
    public String getCompraId() { return compraId; }
    public LocalDateTime getFecha() { return fecha; }
    public String getDescripcion() { return descripcion; }
    public String getClaveIdempotencia() { return claveIdempotencia; }

    // Helpers de vista
    public double getMonto() { return montoCentavos / 100.0; }
}
