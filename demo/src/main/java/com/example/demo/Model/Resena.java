package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;

@Document(collection = "resenas")
public class Resena {

    @Id
    private String id;

    @Field("producto_id")
    private String productoId;

    @Field("usuario_id")
    private String usuarioId;

    @Field("pedido_id")
    private String pedidoId;

    @Field("calificacion")
    private int calificacion; // 1 a 5

    @Field("comentario")
    private String comentario;

    @Field("fecha")
    private LocalDate fecha;

    // Constructores
    public Resena() {
    }

    public Resena(String productoId, String usuarioId, String pedidoId,
                  int calificacion, String comentario, LocalDate fecha) {
        this.productoId = productoId;
        this.usuarioId = usuarioId;
        this.pedidoId = pedidoId;
        this.calificacion = calificacion;
        this.comentario = comentario;
        this.fecha = fecha;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductoId() {
        return productoId;
    }

    public void setProductoId(String productoId) {
        this.productoId = productoId;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(String pedidoId) {
        this.pedidoId = pedidoId;
    }

    public int getCalificacion() {
        return calificacion;
    }

    public void setCalificacion(int calificacion) {
        this.calificacion = calificacion;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }
}