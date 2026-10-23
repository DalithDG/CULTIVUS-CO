package com.example.demo.Model;

import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.DireccionPedido;
import com.example.demo.Model.embebidos.ProductoPedido;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "pedidos")
public class Pedido {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONFIRMADO = "CONFIRMADO";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String LISTO_PARA_RECOGER = "LISTO_PARA_RECOGER";
    public static final String ENVIADO = "ENVIADO";
    public static final String ENTREGADO = "ENTREGADO";
    public static final String CANCELADO = "CANCELADO";
    public static final String EN_REVISION = "EN_REVISION";

    @Id
    private String id;

    // US-21: compra (pago único) a la que pertenece este sub-pedido. Null en pedidos antiguos.
    @Indexed
    @Field("compra_id")
    private String compraId;

    @Field("vendedor")
    private DatosVendedor vendedor;

    // PENDIENTE, CONFIRMADO, ENVIADO, ENTREGADO, CANCELADO,
    // EN_REVISION (pagado pero sin stock al confirmar: el admin gestiona el reembolso manual)
    @Field("estado")
    private String estado = "PENDIENTE";

    // Subtotal de productos de este sub-pedido en centavos (base para abonar al vendedor en US-22)
    @Field("subtotal_centavos")
    private Long subtotalCentavos;

    // Desglose de costo de envío asignado a este sub-pedido en centavos
    @Field("envio_centavos")
    private Long envioCentavos;

    // Snapshot del tramo de envío (US: costo por zona)
    @Field("ciudad_origen_envio")
    private String ciudadOrigenEnvio;

    @Field("ciudad_destino_envio")
    private String ciudadDestinoEnvio;

    @Field("zona_origen_envio")
    private Integer zonaOrigenEnvio;

    @Field("zona_destino_envio")
    private Integer zonaDestinoEnvio;

    @Field("tarifa_envio_centavos")
    private Long tarifaEnvioCentavos;

    @Field("requiere_revision")
    private boolean requiereRevision;

    @Field("motivo_revision")
    private String motivoRevision;

    @Field("fecha_pedido")
    private LocalDateTime fechaPedido;

    @Field("total")
    private Double total;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    // Comprador embebido — snapshot del usuario al momento del pedido
    @Field("comprador")
    private DatosComprador comprador;

    // Dirección de entrega — snapshot al momento del pedido (corrige ERR-03)
    @Field("direccion_entrega")
    private DireccionPedido direccionEntrega;

    // Items embebidos — snapshots históricos de precio y nombre
    @Field("items")
    private List<ProductoPedido> items = new ArrayList<>();

    // Pago embebido — corrige ERR-02 (ya no es 1:1 con UNIQUE)
    @Field("pago")
    private DatosPago pago;

    // Constructores
    public Pedido() {
        this.fechaPedido = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.estado = "PENDIENTE";
        this.items = new ArrayList<>();
    }

    public Pedido(DatosComprador comprador, DatosVendedor vendedor, DireccionPedido direccionEntrega,
                   List<ProductoPedido> items, DatosPago pago) {
        this.comprador = comprador;
        this.vendedor = vendedor;
        this.direccionEntrega = direccionEntrega;
        this.items = items;
        this.pago = pago;
        this.estado = "PENDIENTE";
        this.fechaPedido = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.total = calcularTotal();
    }

    // Método auxiliar para calcular el total
    public Double calcularTotal() {
        return items.stream()
                .mapToDouble(ProductoPedido::getSubtotal)
                .sum();
    }

    /**
     * Retorna un número de factura legible basado en el ID.
     * Ejemplo: CLT-1A2B3C
     */
    public String getNumeroFactura() {
        if (id == null) return "PENDIENTE";
        String suffix = id.length() > 6 ? id.substring(id.length() - 6) : id;
        return "CLT-" + suffix.toUpperCase();
    }

    /** Etiqueta legible del estado para las vistas del comprador. */
    public String getEstadoEtiqueta() {
        String e = estado == null ? "" : estado.toUpperCase();
        switch (e) {
            case "PENDIENTE": return "Pendiente de pago";
            case "CONFIRMADO": return "Confirmado";
            case "EN_PROCESO":
            case "PROCESANDO": return "En preparación";
            case LISTO_PARA_RECOGER: return "Listo para recoger";
            case "ENVIADO": return "Enviado";
            case "ENTREGADO":
            case "COMPLETADO": return "Entregado";
            case "CANCELADO": return "Cancelado";
            case EN_REVISION: return "En revisión";
            default: return estado != null ? estado : "Pendiente";
        }
    }

    /** Sufijo CSS del estado: pendiente, confirmado, proceso, listo, enviado, completado, cancelado, revision. */
    public String getEstadoCss() {
        String e = estado == null ? "" : estado.toUpperCase();
        switch (e) {
            case "CONFIRMADO": return "confirmado";
            case "EN_PROCESO":
            case "PROCESANDO": return "proceso";
            case LISTO_PARA_RECOGER: return "listo";
            case "ENVIADO": return "enviado";
            case "ENTREGADO":
            case "COMPLETADO": return "completado";
            case "CANCELADO": return "cancelado";
            case EN_REVISION: return "revision";
            default: return "pendiente";
        }
    }

    /** Retorna el índice de progreso (1 a 6) para el stepper de tracking del comprador. */
    public int getPasoTracking() {
        if (estado == null) return 1;
        switch (estado.toUpperCase()) {
            case "PENDIENTE": return 1;
            case "CONFIRMADO": return 2;
            case "EN_PROCESO":
            case "PROCESANDO": return 3;
            case LISTO_PARA_RECOGER: return 4;
            case "ENVIADO": return 5;
            case "ENTREGADO":
            case "COMPLETADO": return 6;
            default: return 1;
        }
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompraId() { return compraId; }
    public void setCompraId(String compraId) { this.compraId = compraId; }

    public Long getSubtotalCentavos() { return subtotalCentavos; }
    public void setSubtotalCentavos(Long subtotalCentavos) { this.subtotalCentavos = subtotalCentavos; }

    public Long getEnvioCentavos() { return envioCentavos; }
    public void setEnvioCentavos(Long envioCentavos) { this.envioCentavos = envioCentavos; }

    public double getEnvio() {
        return envioCentavos != null ? envioCentavos / 100.0 : 0.0;
    }

    public String getCiudadOrigenEnvio() { return ciudadOrigenEnvio; }
    public void setCiudadOrigenEnvio(String ciudadOrigenEnvio) { this.ciudadOrigenEnvio = ciudadOrigenEnvio; }

    public String getCiudadDestinoEnvio() { return ciudadDestinoEnvio; }
    public void setCiudadDestinoEnvio(String ciudadDestinoEnvio) { this.ciudadDestinoEnvio = ciudadDestinoEnvio; }

    public Integer getZonaOrigenEnvio() { return zonaOrigenEnvio; }
    public void setZonaOrigenEnvio(Integer zonaOrigenEnvio) { this.zonaOrigenEnvio = zonaOrigenEnvio; }

    public Integer getZonaDestinoEnvio() { return zonaDestinoEnvio; }
    public void setZonaDestinoEnvio(Integer zonaDestinoEnvio) { this.zonaDestinoEnvio = zonaDestinoEnvio; }

    public Long getTarifaEnvioCentavos() { return tarifaEnvioCentavos; }
    public void setTarifaEnvioCentavos(Long tarifaEnvioCentavos) { this.tarifaEnvioCentavos = tarifaEnvioCentavos; }

    public boolean isRequiereRevision() { return requiereRevision; }
    public void setRequiereRevision(boolean requiereRevision) { this.requiereRevision = requiereRevision; }

    public String getMotivoRevision() { return motivoRevision; }
    public void setMotivoRevision(String motivoRevision) { this.motivoRevision = motivoRevision; }

    public DatosVendedor getVendedor() { return vendedor; }
    public void setVendedor(DatosVendedor vendedor) { this.vendedor = vendedor; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaPedido() { return fechaPedido; }
    public void setFechaPedido(LocalDateTime fechaPedido) { this.fechaPedido = fechaPedido; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public DatosComprador getComprador() { return comprador; }
    public void setComprador(DatosComprador comprador) { this.comprador = comprador; }

    public DireccionPedido getDireccionEntrega() { return direccionEntrega; }
    public void setDireccionEntrega(DireccionPedido direccionEntrega) { this.direccionEntrega = direccionEntrega; }

    public List<ProductoPedido> getItems() {
        if (items == null) items = new ArrayList<>();
        return items;
    }
    public void setItems(List<ProductoPedido> items) {
        this.items = items != null ? items : new ArrayList<>();
        this.total = calcularTotal();
    }

    public DatosPago getPago() { return pago; }
    public void setPago(DatosPago pago) { this.pago = pago; }
}