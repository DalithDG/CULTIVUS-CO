package com.example.demo.Model;

import com.example.demo.Model.embebidos.ProductoCarrito;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "carritos")
public class Carrito implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    // Un carrito por usuario — unique
    @Indexed(unique = true)
    @Field("usuario_id")
    private String usuarioId;

    @Field("items")
    private List<ProductoCarrito> items = new ArrayList<>();

    @Field("total_estimado")
    private Double totalEstimado = 0.0;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    // Constructores
    public Carrito() {
        this.updatedAt = LocalDateTime.now();
        this.items = new ArrayList<>();
        this.totalEstimado = 0.0;
    }

    public Carrito(String usuarioId) {
        this.usuarioId = usuarioId;
        this.items = new ArrayList<>();
        this.totalEstimado = 0.0;
        this.updatedAt = LocalDateTime.now();
    }

    // Método auxiliar para recalcular el total
    public void recalcularTotal() {
        this.totalEstimado = items.stream()
                .mapToDouble(ProductoCarrito::getSubtotal)
                .sum();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Número de productos distintos en el carrito (el contador del header).
     * Antes sumaba cantidades; con kg/lb/L decimales eso mostraría "2.5" en el contador.
     */
    public Double getTotalArticulos() {
        return (double) items.size();
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public List<ProductoCarrito> getItems() { return items; }
    public void setItems(List<ProductoCarrito> items) {
        this.items = items;
        recalcularTotal();
    }

    public Double getTotalEstimado() { return totalEstimado; }
    public void setTotalEstimado(Double totalEstimado) { this.totalEstimado = totalEstimado; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}