package com.example.demo.Model.embebidos;

public class ProductoCarrito {

    private String productoId;          // Ahora es el productoCatalogoId
    private String ofertaId;            // ID de la OfertaVendedor específica
    private String vendedorId;          // ID del vendedor (para agrupar pedidos)
    private String vendedorNombre;      // Nombre del vendedor (display)
    private String nombre;
    private String imagenUrl;
    private Double precioUnitario;
    private Double cantidad;
    private String unidadAbreviatura;
    private Double subtotal;
    private Double compraMinima;

    public ProductoCarrito() {
    }

    /**
     * Constructor actualizado para el nuevo modelo con ofertas.
     */
    public ProductoCarrito(String productoId, String ofertaId, String vendedorId,
                           String vendedorNombre, String nombre, String imagenUrl,
                           Double precioUnitario, Double cantidad, String unidadAbreviatura,
                           Double compraMinima) {
        this.productoId = productoId;
        this.ofertaId = ofertaId;
        this.vendedorId = vendedorId;
        this.vendedorNombre = vendedorNombre;
        this.nombre = nombre;
        this.imagenUrl = imagenUrl;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
        this.unidadAbreviatura = unidadAbreviatura;
        this.compraMinima = compraMinima;
        this.subtotal = precioUnitario * cantidad;
    }

    // Getters y Setters
    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getOfertaId() { return ofertaId; }
    public void setOfertaId(String ofertaId) { this.ofertaId = ofertaId; }

    public String getVendedorId() { return vendedorId; }
    public void setVendedorId(String vendedorId) { this.vendedorId = vendedorId; }

    public String getVendedorNombre() { return vendedorNombre; }
    public void setVendedorNombre(String vendedorNombre) { this.vendedorNombre = vendedorNombre; }

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

    public Double getCompraMinima() { return compraMinima; }
    public void setCompraMinima(Double compraMinima) { this.compraMinima = compraMinima; }
}