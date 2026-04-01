package com.example.demo.Model.embebidos;

public class UbicacionUsuario {

    private String ciudad;
    private String departamento;

    public UbicacionUsuario() {
    }

    public UbicacionUsuario(String ciudad, String departamento) {
        this.ciudad = ciudad;
        this.departamento = departamento;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public String getDepartamento() {
        return departamento;
    }

    public void setDepartamento(String departamento) {
        this.departamento = departamento;
    }
}