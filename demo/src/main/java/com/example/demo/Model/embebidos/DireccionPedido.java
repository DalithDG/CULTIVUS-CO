package com.example.demo.Model.embebidos;

public class DireccionPedido {

    private String direccion;
    private String ciudad;
    private String departamento;

    public DireccionPedido() {
    }

    public DireccionPedido(String direccion, String ciudad, String departamento) {
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.departamento = departamento;
    }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }
}