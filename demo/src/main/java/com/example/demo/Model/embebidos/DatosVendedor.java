package com.example.demo.Model.embebidos;

public class DatosVendedor {

    private String id;
    private String nombre;
    private boolean verificado;

    public DatosVendedor() {
    }

    public DatosVendedor(String id, String nombre, boolean verificado) {
        this.id = id;
        this.nombre = nombre;
        this.verificado = verificado;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public boolean isVerificado() { return verificado; }
    public void setVerificado(boolean verificado) { this.verificado = verificado; }
}