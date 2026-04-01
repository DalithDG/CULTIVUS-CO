package com.example.demo.Model;

import com.example.demo.Model.embebidos.CiudadUbicacion;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "ubicaciones")
public class Ubicacion {

    @Id
    private String id;

    // Nombre del departamento
    @Field("nombre")
    private String nombre;

    // Ciudades embebidas dentro del departamento
    @Field("ciudades")
    private List<CiudadUbicacion> ciudades = new ArrayList<>();

    // Constructores
    public Ubicacion() {
        this.ciudades = new ArrayList<>();
    }

    public Ubicacion(String nombre) {
        this.nombre = nombre;
        this.ciudades = new ArrayList<>();
    }

    public Ubicacion(String nombre, List<CiudadUbicacion> ciudades) {
        this.nombre = nombre;
        this.ciudades = ciudades;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public List<CiudadUbicacion> getCiudades() { return ciudades; }
    public void setCiudades(List<CiudadUbicacion> ciudades) { this.ciudades = ciudades; }
}