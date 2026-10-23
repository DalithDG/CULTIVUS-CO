package com.example.demo.Model;

import com.example.demo.Model.embebidos.DireccionPedido;
import com.example.demo.Model.embebidos.ParadaEnvio;
import com.example.demo.Model.embebidos.SnapshotTransportista;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Envio: agrupa la logística de una Compra pagada (uno por compra).
 * Colección: envios
 */
@Document(collection = "envios")
public class Envio {

    public static final String PENDIENTE_ASIGNACION = "PENDIENTE_ASIGNACION";
    public static final String ASIGNADO = "ASIGNADO";
    public static final String EN_RECOGIDA = "EN_RECOGIDA";
    public static final String EN_ENTREGA = "EN_ENTREGA";
    public static final String ENTREGADO = "ENTREGADO";
    public static final String CANCELADO = "CANCELADO";
    public static final String SIN_TRANSPORTISTA = "SIN_TRANSPORTISTA";

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("compra_id")
    private String compraId;

    @Field("estado")
    private String estado = PENDIENTE_ASIGNACION;

    @Indexed
    @Field("transportista_id")
    private String transportistaId;

    @Field("transportista")
    private SnapshotTransportista transportista;

    @Field("paradas")
    private List<ParadaEnvio> paradas = new ArrayList<>();

    @Field("punto_entrega")
    private DireccionPedido puntoEntrega;

    @Field("costo_total_centavos")
    private Long costoTotalCentavos = 0L;

    @Field("fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Field("fecha_asignacion")
    private LocalDateTime fechaAsignacion;

    @Field("fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    public Envio() {
        this.fechaCreacion = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.estado = PENDIENTE_ASIGNACION;
        this.paradas = new ArrayList<>();
    }

    public Envio(String compraId, List<ParadaEnvio> paradas, DireccionPedido puntoEntrega, Long costoTotalCentavos) {
        this();
        this.compraId = compraId;
        this.paradas = paradas != null ? paradas : new ArrayList<>();
        this.puntoEntrega = puntoEntrega;
        this.costoTotalCentavos = costoTotalCentavos != null ? costoTotalCentavos : 0L;
    }

    public double getCostoTotalCop() {
        return costoTotalCentavos != null ? costoTotalCentavos / 100.0 : 0.0;
    }

    public String getCostoTotalFormateado() {
        long centavos = costoTotalCentavos != null ? costoTotalCentavos : 0L;
        return String.format(java.util.Locale.GERMANY, "$%,d COP", centavos / 100);
    }

    public String getEstadoEtiqueta() {
        if (estado == null) return "Pendiente";
        switch (estado) {
            case PENDIENTE_ASIGNACION: return "Buscando transportista";
            case ASIGNADO: return "Asignado";
            case EN_RECOGIDA: return "En recogida";
            case EN_ENTREGA: return "En entrega";
            case ENTREGADO: return "Entregado";
            case CANCELADO: return "Cancelado";
            case SIN_TRANSPORTISTA: return "Sin transportista";
            default: return estado;
        }
    }

    public String getEstadoCss() {
        if (estado == null) return "pendiente";
        switch (estado) {
            case PENDIENTE_ASIGNACION: return "pendiente";
            case ASIGNADO: return "confirmado";
            case EN_RECOGIDA:
            case EN_ENTREGA: return "proceso";
            case ENTREGADO: return "completado";
            case CANCELADO:
            case SIN_TRANSPORTISTA: return "cancelado";
            default: return "pendiente";
        }
    }

    // Getters y Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompraId() { return compraId; }
    public void setCompraId(String compraId) { this.compraId = compraId; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getTransportistaId() { return transportistaId; }
    public void setTransportistaId(String transportistaId) { this.transportistaId = transportistaId; }

    public SnapshotTransportista getTransportista() { return transportista; }
    public void setTransportista(SnapshotTransportista transportista) { this.transportista = transportista; }

    public List<ParadaEnvio> getParadas() {
        if (paradas == null) paradas = new ArrayList<>();
        return paradas;
    }
    public void setParadas(List<ParadaEnvio> paradas) {
        this.paradas = paradas != null ? paradas : new ArrayList<>();
    }

    public DireccionPedido getPuntoEntrega() { return puntoEntrega; }
    public void setPuntoEntrega(DireccionPedido puntoEntrega) { this.puntoEntrega = puntoEntrega; }

    public Long getCostoTotalCentavos() { return costoTotalCentavos; }
    public void setCostoTotalCentavos(Long costoTotalCentavos) { this.costoTotalCentavos = costoTotalCentavos; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaAsignacion() { return fechaAsignacion; }
    public void setFechaAsignacion(LocalDateTime fechaAsignacion) { this.fechaAsignacion = fechaAsignacion; }

    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDateTime fechaEntrega) { this.fechaEntrega = fechaEntrega; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
