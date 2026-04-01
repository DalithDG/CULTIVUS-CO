package com.example.demo.Model.embebidos;

public class PerfilVendedor {

    private String razonSocial;
    private String telefonoContacto;
    private String direccionNegocio;
    private String descripcionNegocio;
    private String banco;
    private String cuentaBancaria;
    private boolean verificado = false;

    public PerfilVendedor() {
    }

    public PerfilVendedor(String razonSocial, String telefonoContacto,
                          String direccionNegocio, String descripcionNegocio,
                          String banco, String cuentaBancaria) {
        this.razonSocial = razonSocial;
        this.telefonoContacto = telefonoContacto;
        this.direccionNegocio = direccionNegocio;
        this.descripcionNegocio = descripcionNegocio;
        this.banco = banco;
        this.cuentaBancaria = cuentaBancaria;
        this.verificado = false;
    }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getTelefonoContacto() { return telefonoContacto; }
    public void setTelefonoContacto(String telefonoContacto) { this.telefonoContacto = telefonoContacto; }

    public String getDireccionNegocio() { return direccionNegocio; }
    public void setDireccionNegocio(String direccionNegocio) { this.direccionNegocio = direccionNegocio; }

    public String getDescripcionNegocio() { return descripcionNegocio; }
    public void setDescripcionNegocio(String descripcionNegocio) { this.descripcionNegocio = descripcionNegocio; }

    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }

    public String getCuentaBancaria() { return cuentaBancaria; }
    public void setCuentaBancaria(String cuentaBancaria) { this.cuentaBancaria = cuentaBancaria; }

    public boolean isVerificado() { return verificado; }
    public void setVerificado(boolean verificado) { this.verificado = verificado; }
}