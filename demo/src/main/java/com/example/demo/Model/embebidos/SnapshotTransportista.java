package com.example.demo.Model.embebidos;

/**
 * Snapshot inmutable del transportista asignado al envío.
 */
public class SnapshotTransportista {

    private String id;
    private String nombre;
    private String telefono;
    private String tipo;           // INDEPENDIENTE | EMPRESA
    private String placa;
    private String razonSocial;
    private String tipoVehiculo;

    public SnapshotTransportista() {
    }

    public SnapshotTransportista(String id, String nombre, String telefono, String tipo,
                                 String placa, String razonSocial, String tipoVehiculo) {
        this.id = id;
        this.nombre = nombre;
        this.telefono = telefono;
        this.tipo = tipo;
        this.placa = placa;
        this.razonSocial = razonSocial;
        this.tipoVehiculo = tipoVehiculo;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getTipoVehiculo() { return tipoVehiculo; }
    public void setTipoVehiculo(String tipoVehiculo) { this.tipoVehiculo = tipoVehiculo; }
}
