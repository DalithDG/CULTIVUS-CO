package com.example.demo.Model;

import com.example.demo.Model.embebidos.CategoriaProducto;
import com.example.demo.Model.embebidos.UnidadMedida;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa un TIPO de producto único en la plataforma (ficha maestra del catálogo).
 * Ejemplo: "Yuca", "Tomate Cherry", "Leche Entera".
 * Múltiples vendedores pueden crear OfertaVendedor vinculadas a este catálogo.
 */
@Document(collection = "productos_catalogo")
public class ProductoCatalogo {

    @Id
    private String id;

    /**
     * Nombre normalizado en minúsculas para evitar duplicados.
     * Ej: "yuca", "tomate cherry", "leche entera"
     */
    @Indexed(unique = true)
    @Field("nombre_normalizado")
    private String nombreNormalizado;

    @Field("nombre")
    private String nombre;

    @Field("descripcion")
    private String descripcion;

    @Field("imagen_url")
    private String imagenUrl;

    // Embebidos
    @Field("categoria")
    private CategoriaProducto categoria;

    /**
     * LEGACY (anterior a US-06): ya no se usa en validaciones ni vistas. Se conserva solo para que la
     * migración del DataLoader pueda copiar la unidad antigua a las ofertas existentes.
     */
    @Field("tipo_venta")
    private String tipoVenta;

    /** LEGACY: abreviatura de la unidad antigua (ver nota de tipoVenta). */
    @Field("unidad_medida")
    private String unidadMedida;

    /** Unidad sugerida al vendedor al publicar (US-06). */
    @Field("unidad_por_defecto")
    private UnidadMedida unidadPorDefecto;

    /** Entre 1 y 3 unidades entre las que el vendedor puede elegir. Si hay una sola, el campo va bloqueado. */
    @Field("unidades_permitidas")
    private List<UnidadMedida> unidadesPermitidas = new ArrayList<>();

    // ── Campos calculados (se actualizan al crear/editar/eliminar ofertas) ──

    @Field("precio_minimo")
    private Double precioMinimo;

    @Field("total_vendedores")
    private Integer totalVendedores = 0;

    @Field("stock_total")
    private Double stockTotal = 0.0;

    // ── Estado de moderación ──

    /**
     * true = visible en el catálogo público.
     * Los productos creados por vendedores inician en false (pendiente de aprobación).
     * Los migrados desde la colección antigua inician en true.
     */
    @Field("aprobado")
    private boolean aprobado = false;

    @Field("activo")
    private boolean activo = true;

    /** ID del vendedor que sugirió este producto (null si lo creó un admin) */
    @Field("sugerido_por")
    private String sugeridoPor;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    // ── Constructores ──

    public ProductoCatalogo() {
        this.createdAt = LocalDateTime.now();
        this.activo = true;
        this.aprobado = false;
        this.totalVendedores = 0;
        this.stockTotal = 0.0;
    }

    public ProductoCatalogo(String nombre, String descripcion, String imagenUrl,
                            CategoriaProducto categoria, String tipoVenta, String unidadMedida) {
        this.nombre = nombre;
        this.nombreNormalizado = normalizarNombre(nombre);
        this.descripcion = descripcion;
        this.imagenUrl = imagenUrl;
        this.categoria = categoria;
        this.tipoVenta = tipoVenta;
        this.unidadMedida = unidadMedida;
        this.activo = true;
        this.aprobado = false;
        this.totalVendedores = 0;
        this.stockTotal = 0.0;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor US-06: producto con unidad por defecto y unidades permitidas (1 a 3).
     * Si la lista llega vacía se usa solo la unidad por defecto.
     */
    public ProductoCatalogo(String nombre, String descripcion, String imagenUrl,
                            CategoriaProducto categoria, UnidadMedida unidadPorDefecto,
                            List<UnidadMedida> unidadesPermitidas) {
        this.nombre = nombre;
        this.nombreNormalizado = normalizarNombre(nombre);
        this.descripcion = descripcion;
        this.imagenUrl = imagenUrl;
        this.categoria = categoria;
        this.activo = true;
        this.aprobado = false;
        this.totalVendedores = 0;
        this.stockTotal = 0.0;
        this.createdAt = LocalDateTime.now();
        definirUnidades(unidadPorDefecto, unidadesPermitidas);
    }

    // ── Unidades (US-06) ──

    /**
     * Define la unidad por defecto y las permitidas (1 a 3). La por defecto siempre queda incluida
     * y de primera. Lanza IllegalArgumentException si hay más de 3 o no hay ninguna.
     */
    public void definirUnidades(UnidadMedida porDefecto, List<UnidadMedida> permitidas) {
        if (porDefecto == null) {
            throw new IllegalArgumentException("El producto debe tener una unidad por defecto.");
        }
        List<UnidadMedida> lista = new ArrayList<>();
        lista.add(porDefecto.copia());
        if (permitidas != null) {
            for (UnidadMedida u : permitidas) {
                if (u == null || u.getAbreviatura() == null) continue;
                boolean repetida = lista.stream().anyMatch(x -> x.getAbreviatura().equalsIgnoreCase(u.getAbreviatura()));
                if (!repetida) {
                    lista.add(u.copia());
                }
            }
        }
        if (lista.size() > 3) {
            throw new IllegalArgumentException("Un producto admite entre 1 y 3 unidades de venta.");
        }
        this.unidadPorDefecto = lista.get(0);
        this.unidadesPermitidas = lista;
    }

    /** Busca una unidad permitida por abreviatura (sin distinguir mayúsculas). Null si no está permitida. */
    public UnidadMedida buscarUnidadPermitida(String abreviatura) {
        if (abreviatura == null || unidadesPermitidas == null) return null;
        for (UnidadMedida u : unidadesPermitidas) {
            if (u.getAbreviatura() != null && u.getAbreviatura().equalsIgnoreCase(abreviatura.trim())) {
                return u;
            }
        }
        return null;
    }

    /** Abreviatura para mostrar junto al precio en listados ("kg", "un"...). Sin unidad: "un". */
    public String getAbreviaturaUnidad() {
        return unidadPorDefecto != null && unidadPorDefecto.getAbreviatura() != null
                ? unidadPorDefecto.getAbreviatura() : "un";
    }

    // ── Utilidad ──

    /**
     * Normaliza el nombre para comparaciones de unicidad:
     * minúsculas, sin acentos, sin espacios extra.
     */
    public static String normalizarNombre(String nombre) {
        if (nombre == null) return null;
        String normalizado = nombre.trim().toLowerCase();
        normalizado = normalizado.replaceAll("[áàâä]", "a");
        normalizado = normalizado.replaceAll("[éèêë]", "e");
        normalizado = normalizado.replaceAll("[íìîï]", "i");
        normalizado = normalizado.replaceAll("[óòôö]", "o");
        normalizado = normalizado.replaceAll("[úùûü]", "u");
        normalizado = normalizado.replaceAll("[ñ]", "n");
        normalizado = normalizado.replaceAll("\\s+", " ");
        return normalizado;
    }

    // ── Getters y Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombreNormalizado() { return nombreNormalizado; }
    public void setNombreNormalizado(String nombreNormalizado) { this.nombreNormalizado = nombreNormalizado; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) {
        this.nombre = nombre;
        this.nombreNormalizado = normalizarNombre(nombre);
    }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public CategoriaProducto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProducto categoria) { this.categoria = categoria; }

    public String getTipoVenta() { return tipoVenta; }
    public void setTipoVenta(String tipoVenta) { this.tipoVenta = tipoVenta; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public UnidadMedida getUnidadPorDefecto() { return unidadPorDefecto; }
    public void setUnidadPorDefecto(UnidadMedida unidadPorDefecto) { this.unidadPorDefecto = unidadPorDefecto; }

    public List<UnidadMedida> getUnidadesPermitidas() {
        return unidadesPermitidas != null ? unidadesPermitidas : new ArrayList<>();
    }
    public void setUnidadesPermitidas(List<UnidadMedida> unidadesPermitidas) { this.unidadesPermitidas = unidadesPermitidas; }

    public Double getPrecioMinimo() { return precioMinimo; }
    public void setPrecioMinimo(Double precioMinimo) { this.precioMinimo = precioMinimo; }

    public Integer getTotalVendedores() { return totalVendedores; }
    public void setTotalVendedores(Integer totalVendedores) { this.totalVendedores = totalVendedores; }

    public Double getStockTotal() { return stockTotal; }
    public void setStockTotal(Double stockTotal) { this.stockTotal = stockTotal; }

    public boolean isAprobado() { return aprobado; }
    public void setAprobado(boolean aprobado) { this.aprobado = aprobado; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public String getSugeridoPor() { return sugeridoPor; }
    public void setSugeridoPor(String sugeridoPor) { this.sugeridoPor = sugeridoPor; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
