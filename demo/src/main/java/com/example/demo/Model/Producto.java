package com.example.demo.Model;

import com.example.demo.Model.embebidos.CategoriaProducto;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.UnidadMedida;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "productos")
public class Producto {

    @Id
    private String id;

    @Field("nombre")
    private String nombre;

    @Field("precio")
    private Double precio;

    @Field("stock")
    private int stock;

    @Field("descripcion")
    private String descripcion;

    @Field("imagen_url")
    private String imagenUrl;

    @Field("peso")
    private Double peso;

    @Field("disponible")
    private boolean disponible = true;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    // Embebidos
    @Field("categoria")
    private CategoriaProducto categoria;

    @Field("unidad_medida")
    private UnidadMedida unidadMedida;

    @Field("vendedor")
    private DatosVendedor vendedor;

    // Constructores
    public Producto() {
        this.createdAt = LocalDateTime.now();
        this.disponible = true;
    }

    public Producto(String nombre, Double precio, int stock, String descripcion,
                    String imagenUrl, Double peso, CategoriaProducto categoria,
                    UnidadMedida unidadMedida, DatosVendedor vendedor) {
        this.nombre = nombre;
        this.precio = precio;
        this.stock = stock;
        this.descripcion = descripcion;
        this.imagenUrl = imagenUrl;
        this.peso = peso;
        this.categoria = categoria;
        this.unidadMedida = unidadMedida;
        this.vendedor = vendedor;
        this.disponible = true;
        this.createdAt = LocalDateTime.now();
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public Double getPeso() { return peso; }
    public void setPeso(Double peso) { this.peso = peso; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public CategoriaProducto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProducto categoria) { this.categoria = categoria; }

    public UnidadMedida getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(UnidadMedida unidadMedida) { this.unidadMedida = unidadMedida; }

    public DatosVendedor getVendedor() { return vendedor; }
    public void setVendedor(DatosVendedor vendedor) { this.vendedor = vendedor; }
}