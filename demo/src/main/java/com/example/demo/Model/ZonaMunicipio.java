package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Relación municipio -> zona logística. La búsqueda se hace siempre por el nombre normalizado
 * (sin tildes, en mayúsculas, espacios colapsados) para tolerar diferencias de escritura.
 */
@Document(collection = "zonas_municipio")
public class ZonaMunicipio {

    @Id
    private String id;

    /** Nombre para mostrar, alineado con la colección de ubicaciones cuando existe. */
    @Field("municipio")
    private String municipio;

    @Indexed(unique = true)
    @Field("municipio_normalizado")
    private String municipioNormalizado;

    @Field("departamento")
    private String departamento;

    @Field("zona_id")
    private Integer zonaId;

    @Field("zona_nombre")
    private String zonaNombre;

    public ZonaMunicipio() {
    }

    public ZonaMunicipio(String municipio, Integer zonaId, String zonaNombre) {
        this(municipio, com.example.demo.services.ZonasLogistica.normalizar(municipio), "Bolívar", zonaId, zonaNombre);
    }

    public ZonaMunicipio(String municipio, String municipioNormalizado, String departamento,
                         Integer zonaId, String zonaNombre) {
        this.municipio = municipio;
        this.municipioNormalizado = municipioNormalizado;
        this.departamento = departamento;
        this.zonaId = zonaId;
        this.zonaNombre = zonaNombre;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMunicipio() { return municipio; }
    public void setMunicipio(String municipio) { this.municipio = municipio; }

    public String getMunicipioNormalizado() { return municipioNormalizado; }
    public void setMunicipioNormalizado(String municipioNormalizado) { this.municipioNormalizado = municipioNormalizado; }

    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }

    public Integer getZonaId() { return zonaId; }
    public void setZonaId(Integer zonaId) { this.zonaId = zonaId; }

    public String getZonaNombre() { return zonaNombre; }
    public void setZonaNombre(String zonaNombre) { this.zonaNombre = zonaNombre; }
}
