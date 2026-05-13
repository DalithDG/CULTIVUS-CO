package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "configuracion")
public class AppConfig {

    @Id
    private String id;

    @Field("clave")
    private String clave;

    @Field("valor")
    private String valor;

    @Field("descripcion")
    private String descripcion;

    @Field("tipo")
    private String tipo; // "NUMBER", "STRING", "BOOLEAN"

    public AppConfig() {
    }

    public AppConfig(String clave, String valor, String descripcion, String tipo) {
        this.clave = clave;
        this.valor = valor;
        this.descripcion = descripcion;
        this.tipo = tipo;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}
