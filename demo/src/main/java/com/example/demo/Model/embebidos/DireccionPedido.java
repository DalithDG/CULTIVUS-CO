package com.example.demo.Model.embebidos;

public class DireccionPedido {

    private String direccion;
    private String ciudad;
    private String departamento;
    private String destinatarioNombre;
    private String destinatarioTelefono;

    public DireccionPedido() {
    }

    public DireccionPedido(String direccion, String ciudad, String departamento) {
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.departamento = departamento;
    }

    public String getDireccionCompleta() {
        StringBuilder sb = new StringBuilder();
        if (direccion != null && !direccion.isBlank()) sb.append(direccion);
        if (ciudad != null && !ciudad.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ciudad);
        }
        if (departamento != null && !departamento.isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(departamento);
        }
        return sb.toString();
    }

    public String getDestinatarioNombre() {
        return destinatarioNombre != null && !destinatarioNombre.isBlank() ? destinatarioNombre : "Comprador";
    }

    public void setDestinatarioNombre(String destinatarioNombre) {
        this.destinatarioNombre = destinatarioNombre;
    }

    public String getDestinatarioTelefono() {
        return destinatarioTelefono;
    }

    public void setDestinatarioTelefono(String destinatarioTelefono) {
        this.destinatarioTelefono = destinatarioTelefono;
    }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }
}