package com.example.demo.Model;

import com.example.demo.Model.embebidos.CuentaBancaria;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * US-24 / Fase 4: Solicitud de retiro simulado de billetera (vendedor o transportista).
 * Guarda un snapshot inmutable de los datos del usuario y de su cuenta bancaria al momento de solicitar.
 */
@Document(collection = "solicitudes_retiro")
public class SolicitudRetiro {

    public enum Estado {
        PENDIENTE,
        APROBADO,
        RECHAZADO
    }

    @Id
    private String id;

    @Field("usuario_id")
    private String usuarioId;

    @Field("rol")
    private Billetera.Rol rol;

    @Field("usuario_nombre")
    private String usuarioNombre;

    @Field("usuario_email")
    private String usuarioEmail;

    @Field("monto_centavos")
    private long montoCentavos;

    @Field("estado")
    private Estado estado = Estado.PENDIENTE;

    @Field("cuenta_destino")
    private CuentaBancaria cuentaDestino;

    @Field("fecha_solicitud")
    private LocalDateTime fechaSolicitud;

    @Field("fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Field("admin_id")
    private String adminId;

    @Field("admin_nombre")
    private String adminNombre;

    @Field("motivo_rechazo")
    private String motivoRechazo;

    @Field("referencia_bancaria")
    private String referenciaBancaria;

    @Field("numero_comprobante")
    private String numeroComprobante;

    public SolicitudRetiro() {
        this.fechaSolicitud = LocalDateTime.now();
        this.estado = Estado.PENDIENTE;
    }

    public SolicitudRetiro(String usuarioId, Billetera.Rol rol, String usuarioNombre,
                           String usuarioEmail, long montoCentavos, CuentaBancaria cuentaDestino) {
        this();
        this.usuarioId = usuarioId;
        this.rol = rol;
        this.usuarioNombre = usuarioNombre;
        this.usuarioEmail = usuarioEmail;
        this.montoCentavos = montoCentavos;
        this.cuentaDestino = cuentaDestino != null ? cuentaDestino.copiar() : null;
    }

    // Helpers de vista
    public double getMonto() {
        return montoCentavos / 100.0;
    }

    public long getMontoPesos() {
        return montoCentavos / 100L;
    }

    public boolean isPendiente() {
        return Estado.PENDIENTE == estado;
    }

    public boolean isAprobado() {
        return Estado.APROBADO == estado;
    }

    public boolean isRechazado() {
        return Estado.RECHAZADO == estado;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public Billetera.Rol getRol() { return rol; }
    public void setRol(Billetera.Rol rol) { this.rol = rol; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public String getUsuarioEmail() { return usuarioEmail; }
    public void setUsuarioEmail(String usuarioEmail) { this.usuarioEmail = usuarioEmail; }

    public long getMontoCentavos() { return montoCentavos; }
    public void setMontoCentavos(long montoCentavos) { this.montoCentavos = montoCentavos; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public CuentaBancaria getCuentaDestino() { return cuentaDestino; }
    public void setCuentaDestino(CuentaBancaria cuentaDestino) {
        this.cuentaDestino = cuentaDestino != null ? cuentaDestino.copiar() : null;
    }

    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(LocalDateTime fechaResolucion) { this.fechaResolucion = fechaResolucion; }

    public LocalDateTime getFechaProcesamiento() { return fechaResolucion; }

    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }

    public String getAdminNombre() { return adminNombre; }
    public void setAdminNombre(String adminNombre) { this.adminNombre = adminNombre; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public String getReferenciaBancaria() { return referenciaBancaria; }
    public void setReferenciaBancaria(String referenciaBancaria) { this.referenciaBancaria = referenciaBancaria; }

    public String getReferencia() { return referenciaBancaria; }

    public String getNumeroComprobante() { return numeroComprobante; }
    public void setNumeroComprobante(String numeroComprobante) { this.numeroComprobante = numeroComprobante; }
}

