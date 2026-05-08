package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "mensajes")
public class Mensaje {
    @Id
    private String id;
    private String emisorId;
    private String emisorNombre;
    private String receptorId; // Si es nulo, es para el soporte técnico (admin)
    private String asunto;
    private String contenido;
    private LocalDateTime fecha;
    private boolean leido;

    public Mensaje() {
        this.fecha = LocalDateTime.now();
        this.leido = false;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmisorId() {
        return emisorId;
    }

    public void setEmisorId(String emisorId) {
        this.emisorId = emisorId;
    }

    public String getEmisorNombre() {
        return emisorNombre;
    }

    public void setEmisorNombre(String emisorNombre) {
        this.emisorNombre = emisorNombre;
    }

    public String getReceptorId() {
        return receptorId;
    }

    public void setReceptorId(String receptorId) {
        this.receptorId = receptorId;
    }

    public String getAsunto() {
        return asunto;
    }

    public void setAsunto(String asunto) {
        this.asunto = asunto;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public boolean isLeido() {
        return leido;
    }

    public void setLeido(boolean leido) {
        this.leido = leido;
    }
}
