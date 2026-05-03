package com.example.demo.Model.embebidos;

public class ProductoCarrito {

    private String productoId;
    private String nombre;
    private String imagenUrl;
    private Double precioUnitario;
    private Double cantidad;
    private String unidadAbreviatura;
    private Double subtotal;

    public ProductoCarrito() {
    }

    public ProductoCarrito(String productoId, String nombre, String imagenUrl,
                           Double precioUnitario, Double cantidad, String unidadAbreviatura) {
        this.productoId = productoId;
        this.nombre = nombre;
        this.imagenUrl = imagenUrl;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
        this.unidadAbreviatura = unidadAbreviatura;
        this.subtotal = precioUnitario * cantidad;
    }

    // Getters y Setters
    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public Double getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(Double precioUnitario) {
        this.precioUnitario = precioUnitario;
        this.subtotal = precioUnitario * this.cantidad;
    }

    public Double getCantidad() { return cantidad; }
    public void setCantidad(Double cantidad) {
        this.cantidad = cantidad;
        this.subtotal = this.precioUnitario * cantidad;
    }

    public String getUnidadAbreviatura() { return unidadAbreviatura; }
    public void setUnidadAbreviatura(String unidadAbreviatura) { this.unidadAbreviatura = unidadAbreviatura; }

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }
}