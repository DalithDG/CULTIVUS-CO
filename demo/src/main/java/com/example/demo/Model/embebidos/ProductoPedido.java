package com.example.demo.Model.embebidos;

public class ProductoPedido {

    private String productoId;
    private String nombreSnapshot;
    private String imagenSnapshot;
    private Double precioUnitario;
    private Double cantidad;
    private String unidadAbreviatura;
    private Double subtotal;

    public ProductoPedido() {
    }

    public ProductoPedido(String productoId, String nombreSnapshot, String imagenSnapshot,
                          Double precioUnitario, Double cantidad, String unidadAbreviatura) {
        this.productoId = productoId;
        this.nombreSnapshot = nombreSnapshot;
        this.imagenSnapshot = imagenSnapshot;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
        this.unidadAbreviatura = unidadAbreviatura;
        this.subtotal = precioUnitario * cantidad;
    }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getNombreSnapshot() { return nombreSnapshot; }
    public void setNombreSnapshot(String nombreSnapshot) { this.nombreSnapshot = nombreSnapshot; }

    public String getImagenSnapshot() { return imagenSnapshot; }
    public void setImagenSnapshot(String imagenSnapshot) { this.imagenSnapshot = imagenSnapshot; }

    public Double getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(Double precioUnitario) { this.precioUnitario = precioUnitario; }

    public Double getCantidad() { return cantidad; }
    public void setCantidad(Double cantidad) { this.cantidad = cantidad; }

    public String getUnidadAbreviatura() { return unidadAbreviatura; }
    public void setUnidadAbreviatura(String unidadAbreviatura) { this.unidadAbreviatura = unidadAbreviatura; }

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }
}