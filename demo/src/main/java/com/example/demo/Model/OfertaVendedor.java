package com.example.demo.Model;

import com.example.demo.Model.embebidos.DatosVendedor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Representa la oferta de UN vendedor para un producto del catálogo.
 * Ejemplo: "Juan vende Yuca a $3000/kg, stock 50kg"
 *
 * Un vendedor puede tener máximo 1 oferta por producto del catálogo.
 */
@Document(collection = "ofertas")
@CompoundIndex(name = "idx_producto_vendedor", def = "{'producto_catalogo_id': 1, 'vendedor.id': 1}", unique = true)
public class OfertaVendedor {

    @Id
    private String id;

    /** Referencia al ProductoCatalogo */
    @Indexed
    @Field("producto_catalogo_id")
    private String productoCatalogoId;

    /** Snapshot del vendedor al momento de crear la oferta */
    @Field("vendedor")
    private DatosVendedor vendedor;

    @Field("precio")
    private Double precio;

    @Field("stock")
    private Double stock;

    @Field("peso")
    private Double peso;

    @Field("peso_promedio_unidad")
    private Double pesoPromedioUnidad;

    @Field("descripcion_paquete")
    private String descripcionPaquete;

    @Field("compra_minima")
    private Double compraMinima = 1.0;

    @Field("disponible")
    private boolean disponible = true;

    /** Imagen propia del vendedor (puede diferir de la imagen estándar del catálogo) */
    @Field("imagen_url")
    private String imagenUrl;

    /** Descripción específica del vendedor (ej: "cultivada orgánicamente en Boyacá") */
    @Field("descripcion_vendedor")
    private String descripcionVendedor;

    // ── Moderación ──

    // Las ofertas se aprueban automáticamente según la nueva regla de negocio.
    // Se eliminaron estadoAprobacion y motivoRechazo.

    // ── Nombre del producto (desnormalizado para facilitar vistas) ──

    @Field("nombre_producto")
    private String nombreProducto;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    // ── Constructores ──

    public OfertaVendedor() {
        this.createdAt = LocalDateTime.now();
        this.disponible = true;
    }

    public OfertaVendedor(String productoCatalogoId, DatosVendedor vendedor,
                          Double precio, Double stock, Double peso,
                          Double pesoPromedioUnidad, String descripcionPaquete,
                          Double compraMinima, String imagenUrl,
                          String descripcionVendedor, String nombreProducto) {
        this.productoCatalogoId = productoCatalogoId;
        this.vendedor = vendedor;
        this.precio = precio;
        this.stock = stock;
        this.peso = peso;
        this.pesoPromedioUnidad = pesoPromedioUnidad;
        this.descripcionPaquete = descripcionPaquete;
        this.compraMinima = compraMinima != null ? compraMinima : 1.0;
        this.imagenUrl = imagenUrl;
        this.descripcionVendedor = descripcionVendedor;
        this.nombreProducto = nombreProducto;
        this.disponible = true;
        this.createdAt = LocalDateTime.now();
    }

    // ── Getters y Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductoCatalogoId() { return productoCatalogoId; }
    public void setProductoCatalogoId(String productoCatalogoId) { this.productoCatalogoId = productoCatalogoId; }

    public DatosVendedor getVendedor() { return vendedor; }
    public void setVendedor(DatosVendedor vendedor) { this.vendedor = vendedor; }

    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }

    public Double getStock() { return stock; }
    public void setStock(Double stock) { this.stock = stock; }

    public Double getPeso() { return peso; }
    public void setPeso(Double peso) { this.peso = peso; }

    public Double getPesoPromedioUnidad() { return pesoPromedioUnidad; }
    public void setPesoPromedioUnidad(Double pesoPromedioUnidad) { this.pesoPromedioUnidad = pesoPromedioUnidad; }

    public String getDescripcionPaquete() { return descripcionPaquete; }
    public void setDescripcionPaquete(String descripcionPaquete) { this.descripcionPaquete = descripcionPaquete; }

    public Double getCompraMinima() { return compraMinima != null ? compraMinima : 1.0; }
    public void setCompraMinima(Double compraMinima) { this.compraMinima = compraMinima; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public String getDescripcionVendedor() { return descripcionVendedor; }
    public void setDescripcionVendedor(String descripcionVendedor) { this.descripcionVendedor = descripcionVendedor; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
