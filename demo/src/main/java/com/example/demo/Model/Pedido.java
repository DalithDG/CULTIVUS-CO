package com.example.demo.Model;

import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DireccionPedido;
import com.example.demo.Model.embebidos.ProductoPedido;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    @Field("vendedor_id")
    private String vendedorId;

    // PENDIENTE, CONFIRMADO, ENVIADO, ENTREGADO, CANCELADO
    @Field("estado")
    private String estado = "PENDIENTE";

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

    public Pedido(DatosComprador comprador, String vendedorId, DireccionPedido direccionEntrega,
                  List<ProductoPedido> items, DatosPago pago) {
        this.comprador = comprador;
        this.vendedorId = vendedorId;
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

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVendedorId() { return vendedorId; }
    public void setVendedorId(String vendedorId) { this.vendedorId = vendedorId; }

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

    public List<ProductoPedido> getItems() { return items; }
    public void setItems(List<ProductoPedido> items) {
        this.items = items;
        this.total = calcularTotal();
    }

    public DatosPago getPago() { return pago; }
    public void setPago(DatosPago pago) { this.pago = pago; }
}