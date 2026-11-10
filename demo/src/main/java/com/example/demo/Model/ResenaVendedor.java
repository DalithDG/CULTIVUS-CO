package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;

@Document(collection = "resenas_vendedor")
public class ResenaVendedor {

    @Id
    private String id;

    @Field("vendedor_id")
    private String vendedorId;

    @Field("usuario_id")
    private String usuarioId;

    @Field("pedido_id")
    private String pedidoId;

    @Field("calificacion")
    private int calificacion;

    @Field("comentario")
    private String comentario;

    @Field("fecha")
    private LocalDate fecha;

    public ResenaVendedor() {}

    public ResenaVendedor(String vendedorId, String usuarioId, String pedidoId,
                          int calificacion, String comentario, LocalDate fecha) {
        this.vendedorId = vendedorId;
        this.usuarioId = usuarioId;
        this.pedidoId = pedidoId;
        this.calificacion = calificacion;
        this.comentario = comentario;
        this.fecha = fecha;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVendedorId() { return vendedorId; }
    public void setVendedorId(String vendedorId) { this.vendedorId = vendedorId; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getPedidoId() { return pedidoId; }
    public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }
    public int getCalificacion() { return calificacion; }
    public void setCalificacion(int calificacion) { this.calificacion = calificacion; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
}
