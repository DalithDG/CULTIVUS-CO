package com.example.demo.Model;

import com.example.demo.Model.embebidos.DatosComprador;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * US-21: una Compra agrupa todo lo que el comprador paga en una sola transacción de Wompi.
 * Tiene UNA referencia de pago y UN total firmado; de ella cuelgan los sub-pedidos
 * (uno por vendedor), que guardan el compraId.
 *
 * Todo el dinero se guarda en centavos enteros (COP x 100).
 */
@Document(collection = "compras")
public class Compra {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String PAGADA = "PAGADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    private String id;

    @Field("comprador")
    private DatosComprador comprador;

    /** Referencia enviada a Wompi (CUL-...). Es la misma que guarda cada pedido en pago.referencia. */
    @Indexed(unique = true)
    @Field("referencia_pago")
    private String referenciaPago;

    /** Suma de los subtotales de productos de todos los sub-pedidos, en centavos. */
    @Field("subtotal_centavos")
    private Long subtotalCentavos;

    /** Costo de envío cobrado en la compra, en centavos. */
    @Field("envio_centavos")
    private Long envioCentavos;

    /** Total firmado y cobrado en Wompi (productos + envío), en centavos. */
    @Field("total_centavos")
    private Long totalCentavos;

    // PENDIENTE, PAGADA, RECHAZADA
    @Field("estado")
    private String estado = PENDIENTE;

    @Field("fecha")
    private LocalDateTime fecha;

    @Field("pedido_ids")
    private List<String> pedidoIds = new ArrayList<>();

    @Field("transaccion_id")
    private String transaccionId;

    @Field("fecha_pago")
    private LocalDateTime fechaPago;

    /** true si al confirmar algún sub-pedido quedó en revisión del admin (stock insuficiente). */
    @Field("requiere_revision")
    private boolean requiereRevision;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    /** Sub-pedidos asociados para visualización en vistas. */
    @Transient
    private List<Pedido> subPedidos = new ArrayList<>();

    /** Envío asociado para visualización logística en el tracking del comprador. */
    @Transient
    private Envio envioLogistica;

    public Compra() {
        this.fecha = LocalDateTime.now();
        this.estado = PENDIENTE;
    }

    public Compra(DatosComprador comprador, String referenciaPago, long subtotalCentavos, long envioCentavos) {
        this();
        this.comprador = comprador;
        this.referenciaPago = referenciaPago;
        this.subtotalCentavos = subtotalCentavos;
        this.envioCentavos = envioCentavos;
        this.totalCentavos = subtotalCentavos + envioCentavos;
    }

    public boolean esFinal() {
        return PAGADA.equals(estado) || RECHAZADA.equals(estado);
    }

    // ── Helpers de vista (no se persisten: Spring Data mapea campos, no getters) ──

    public double getTotal() {
        return totalCentavos != null ? totalCentavos / 100.0 : 0.0;
    }

    public double getSubtotal() {
        return subtotalCentavos != null ? subtotalCentavos / 100.0 : 0.0;
    }

    public double getEnvio() {
        return envioCentavos != null ? envioCentavos / 100.0 : 0.0;
    }

    public String getEstadoEtiqueta() {
        if (PAGADA.equals(estado)) return requiereRevision ? "Pagada · con revisión" : "Pagada";
        if (RECHAZADA.equals(estado)) return "Pago rechazado";
        return "Pendiente de pago";
    }

    public String getEstadoCss() {
        if (PAGADA.equals(estado)) return requiereRevision ? "revision" : "confirmado";
        if (RECHAZADA.equals(estado)) return "cancelado";
        return "pendiente";
    }

    // ── Getters y setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public DatosComprador getComprador() { return comprador; }
    public void setComprador(DatosComprador comprador) { this.comprador = comprador; }

    public String getReferenciaPago() { return referenciaPago; }
    public void setReferenciaPago(String referenciaPago) { this.referenciaPago = referenciaPago; }

    public String getCodigoReferencia() { return referenciaPago; }
    public void setCodigoReferencia(String codigoReferencia) { this.referenciaPago = codigoReferencia; }

    public Long getSubtotalCentavos() { return subtotalCentavos; }
    public void setSubtotalCentavos(Long subtotalCentavos) { this.subtotalCentavos = subtotalCentavos; }

    public Long getEnvioCentavos() { return envioCentavos; }
    public void setEnvioCentavos(Long envioCentavos) { this.envioCentavos = envioCentavos; }

    public Long getTotalCentavos() { return totalCentavos; }
    public void setTotalCentavos(Long totalCentavos) { this.totalCentavos = totalCentavos; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public List<String> getPedidoIds() {
        if (pedidoIds == null) pedidoIds = new ArrayList<>();
        return pedidoIds;
    }
    public void setPedidoIds(List<String> pedidoIds) { this.pedidoIds = pedidoIds; }

    public String getTransaccionId() { return transaccionId; }
    public void setTransaccionId(String transaccionId) { this.transaccionId = transaccionId; }

    public LocalDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDateTime fechaPago) { this.fechaPago = fechaPago; }

    public boolean isRequiereRevision() { return requiereRevision; }
    public void setRequiereRevision(boolean requiereRevision) { this.requiereRevision = requiereRevision; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Pedido> getSubPedidos() {
        if (subPedidos == null) subPedidos = new ArrayList<>();
        return subPedidos;
    }
    public void setSubPedidos(List<Pedido> subPedidos) {
        this.subPedidos = subPedidos != null ? subPedidos : new ArrayList<>();
    }

    public Envio getEnvioLogistica() { return envioLogistica; }
    public void setEnvioLogistica(Envio envioLogistica) { this.envioLogistica = envioLogistica; }

    /**
     * Clave para las pestañas de "Mis Pedidos": ENTREGADO, PROCESO, PENDIENTE o RECHAZADA.
     * Una compra pagada con envío asignado/en curso queda en PROCESO; entregada, en ENTREGADO.
     */
    public String getEstadoFiltro() {
        if (RECHAZADA.equals(estado)) return RECHAZADA;
        if (!PAGADA.equals(estado)) return PENDIENTE;
        if (envioLogistica != null && Envio.ENTREGADO.equals(envioLogistica.getEstado())) return "ENTREGADO";
        if (subPedidos != null && !subPedidos.isEmpty()
                && subPedidos.stream().allMatch(p -> p.getEstado() != null
                        && ("ENTREGADO".equalsIgnoreCase(p.getEstado()) || "COMPLETADO".equalsIgnoreCase(p.getEstado())))) {
            return "ENTREGADO";
        }
        return "PROCESO";
    }

    public boolean isTieneSubPedidoListoParaRecoger() {
        if (subPedidos == null) return false;
        return subPedidos.stream()
                .anyMatch(p -> Pedido.LISTO_PARA_RECOGER.equalsIgnoreCase(p.getEstado()));
    }
}
