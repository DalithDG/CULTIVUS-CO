package com.example.demo.Model.embebidos;

public class DatosVendedor {

    private String id;
    private String nombre;
    private boolean verificado;

    // Datos adicionales del perfil del vendedor (snapshot al momento de crear el producto)
    private String razonSocial;
    private String telefonoContacto;
    private String descripcionNegocio;

    public DatosVendedor() {
    }

    public DatosVendedor(String id, String nombre, boolean verificado) {
        this.id = id;
        this.nombre = nombre;
        this.verificado = verificado;
    }

    public DatosVendedor(String id, String nombre, boolean verificado,
                         String razonSocial, String telefonoContacto, String descripcionNegocio) {
        this.id = id;
        this.nombre = nombre;
        this.verificado = verificado;
        this.razonSocial = razonSocial;
        this.telefonoContacto = telefonoContacto;
        this.descripcionNegocio = descripcionNegocio;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public boolean isVerificado() { return verificado; }
    public void setVerificado(boolean verificado) { this.verificado = verificado; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getTelefonoContacto() { return telefonoContacto; }
    public void setTelefonoContacto(String telefonoContacto) { this.telefonoContacto = telefonoContacto; }

    public String getDescripcionNegocio() { return descripcionNegocio; }
    public void setDescripcionNegocio(String descripcionNegocio) { this.descripcionNegocio = descripcionNegocio; }
}