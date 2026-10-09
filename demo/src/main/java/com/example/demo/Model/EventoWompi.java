package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Registro de idempotencia de eventos de Wompi.
 * El _id es "transaction.id:transaction.status", de modo que un mismo cambio de
 * estado de una transacción solo se aplica una vez, aunque Wompi reintente.
 * No guarda el cuerpo del evento ni ningún secreto.
 */
@Document(collection = "eventos_wompi")
public class EventoWompi {

    @Id
    private String id;

    // false mientras se procesa (o si el proceso quedó a medias); true cuando terminó
    private boolean procesado;

    private String transaccionId;
    private String estado;
    private String referencia;
    private LocalDateTime recibidoEn;
    private LocalDateTime procesadoEn;

    public EventoWompi() {
    }

    public EventoWompi(String transaccionId, String estado, String referencia) {
        this.id = construirId(transaccionId, estado);
        this.transaccionId = transaccionId;
        this.estado = estado;
        this.referencia = referencia;
        this.procesado = false;
        this.recibidoEn = LocalDateTime.now();
    }

    public static String construirId(String transaccionId, String estado) {
        return transaccionId + ":" + estado;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public boolean isProcesado() { return procesado; }
    public void setProcesado(boolean procesado) { this.procesado = procesado; }

    public String getTransaccionId() { return transaccionId; }
    public void setTransaccionId(String transaccionId) { this.transaccionId = transaccionId; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public LocalDateTime getRecibidoEn() { return recibidoEn; }
    public void setRecibidoEn(LocalDateTime recibidoEn) { this.recibidoEn = recibidoEn; }

    public LocalDateTime getProcesadoEn() { return procesadoEn; }
    public void setProcesadoEn(LocalDateTime procesadoEn) { this.procesadoEn = procesadoEn; }
}
