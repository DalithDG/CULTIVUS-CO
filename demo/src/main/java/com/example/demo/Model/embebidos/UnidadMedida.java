package com.example.demo.Model.embebidos;

public class UnidadMedida {

    private String nombre;
    private String abreviatura;
    private String tipo;

    public UnidadMedida() {
    }

    public UnidadMedida(String nombre, String abreviatura, String tipo) {
        this.nombre = nombre;
        this.abreviatura = abreviatura;
        this.tipo = tipo;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getAbreviatura() { return abreviatura; }
    public void setAbreviatura(String abreviatura) { this.abreviatura = abreviatura; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}