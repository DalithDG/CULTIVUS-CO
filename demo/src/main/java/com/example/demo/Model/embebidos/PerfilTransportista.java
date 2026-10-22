package com.example.demo.Model.embebidos;

public class PerfilTransportista {

    private String tipoVehiculo;          // Camión, Camioneta, Furgón, Turbo, etc.
    private String placa;                 // Placa del vehículo
    private Double capacidadCargaKg;      // Capacidad en Kilogramos
    private String licenciaConduccion;    // Número o categoría de licencia (ej. C1, C2)
    private String telefonoContacto;
    private String ciudadBase;
    private String departamentoBase;
    private String zonasCobertura;        // Municipios o departamentos donde opera
    private boolean refrigerado = false;  // Cuenta con furgón refrigerado / cadena de frío
    private boolean disponible = true;    // Listo para aceptar envíos
    private boolean verificado = false;   // Validado por administración
    private String empresa;               // Nombre de empresa de transporte o "Independiente"
    private String banco;
    private String cuentaBancaria;
    private Double calificacion = 5.0;
    private Integer totalEntregas = 0;

    public PerfilTransportista() {
        this.disponible = true;
        this.verificado = false;
        this.calificacion = 5.0;
        this.totalEntregas = 0;
    }

    public PerfilTransportista(String tipoVehiculo, String placa, Double capacidadCargaKg,
                               String licenciaConduccion, String telefonoContacto, String ciudadBase,
                               String departamentoBase, String zonasCobertura, boolean refrigerado,
                               String empresa, String banco, String cuentaBancaria) {
        this.tipoVehiculo = tipoVehiculo;
        this.placa = placa;
        this.capacidadCargaKg = capacidadCargaKg;
        this.licenciaConduccion = licenciaConduccion;
        this.telefonoContacto = telefonoContacto;
        this.ciudadBase = ciudadBase;
        this.departamentoBase = departamentoBase;
        this.zonasCobertura = zonasCobertura;
        this.refrigerado = refrigerado;
        this.empresa = empresa;
        this.banco = banco;
        this.cuentaBancaria = cuentaBancaria;
        this.disponible = true;
        this.verificado = false;
        this.calificacion = 5.0;
        this.totalEntregas = 0;
    }

    // Getters y Setters

    public String getTipoVehiculo() { return tipoVehiculo; }
    public void setTipoVehiculo(String tipoVehiculo) { this.tipoVehiculo = tipoVehiculo; }

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public Double getCapacidadCargaKg() { return capacidadCargaKg; }
    public void setCapacidadCargaKg(Double capacidadCargaKg) { this.capacidadCargaKg = capacidadCargaKg; }

    public String getLicenciaConduccion() { return licenciaConduccion; }
    public void setLicenciaConduccion(String licenciaConduccion) { this.licenciaConduccion = licenciaConduccion; }

    public String getTelefonoContacto() { return telefonoContacto; }
    public void setTelefonoContacto(String telefonoContacto) { this.telefonoContacto = telefonoContacto; }

    public String getCiudadBase() { return ciudadBase; }
    public void setCiudadBase(String ciudadBase) { this.ciudadBase = ciudadBase; }

    public String getDepartamentoBase() { return departamentoBase; }
    public void setDepartamentoBase(String departamentoBase) { this.departamentoBase = departamentoBase; }

    public String getZonasCobertura() { return zonasCobertura; }
    public void setZonasCobertura(String zonasCobertura) { this.zonasCobertura = zonasCobertura; }

    public boolean isRefrigerado() { return refrigerado; }
    public void setRefrigerado(boolean refrigerado) { this.refrigerado = refrigerado; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }

    public boolean isVerificado() { return verificado; }
    public void setVerificado(boolean verificado) { this.verificado = verificado; }

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }

    public String getCuentaBancaria() { return cuentaBancaria; }
    public void setCuentaBancaria(String cuentaBancaria) { this.cuentaBancaria = cuentaBancaria; }

    public Double getCalificacion() { return calificacion; }
    public void setCalificacion(Double calificacion) { this.calificacion = calificacion; }

    public Integer getTotalEntregas() { return totalEntregas; }
    public void setTotalEntregas(Integer totalEntregas) { this.totalEntregas = totalEntregas; }
}
