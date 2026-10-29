package com.example.demo.Model.embebidos;

import java.util.ArrayList;
import java.util.List;

public class PerfilTransportista {

    public static final String TIPO_INDEPENDIENTE = "INDEPENDIENTE";
    public static final String TIPO_EMPRESA = "EMPRESA";

    private String tipo = TIPO_INDEPENDIENTE; // INDEPENDIENTE | EMPRESA
    private String nit;
    private String razonSocial;

    private String tipoVehiculo;          // Camión, Camioneta, Furgón, Turbo, etc.
    private String placa;                 // Placa del vehículo
    private Double capacidadCargaKg;      // Capacidad en Kilogramos
    private String licenciaConduccion;    // Número o categoría de licencia (ej. C1, C2)
    private String telefonoContacto;
    private String ciudadBase;
    private String departamentoBase;

    // Zonas de cobertura estructuradas
    private List<Integer> zonasCoberturaIds = new ArrayList<>();
    private boolean todasLasZonas = false;
    private String zonasCobertura;        // Texto legible para mostrar únicamente

    private boolean refrigerado = false;  // Cuenta con furgón refrigerado / cadena de frío
    private boolean disponible = true;    // Listo para aceptar envíos
    private boolean verificado = false;   // Validado por administración
    private String empresa;               // Nombre de empresa de transporte o "Independiente"
    private String banco;
    private String cuentaBancaria;
    private CuentaBancaria cuentaRetiro;
    private Double calificacion = 5.0;
    private Integer totalEntregas = 0;

    public PerfilTransportista() {
        this.tipo = TIPO_INDEPENDIENTE;
        this.disponible = true;
        this.verificado = false;
        this.calificacion = 5.0;
        this.totalEntregas = 0;
        this.zonasCoberturaIds = new ArrayList<>();
    }

    public PerfilTransportista(String tipoVehiculo, String placa, Double capacidadCargaKg,
                               String licenciaConduccion, String telefonoContacto, String ciudadBase,
                               String departamentoBase, String zonasCobertura, boolean refrigerado,
                               String empresa, String banco, String cuentaBancaria) {
        this();
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
    }

    public boolean operaEnZona(Integer zonaId) {
        if (todasLasZonas) return true;
        if (zonaId == null || zonasCoberturaIds == null) return false;
        return zonasCoberturaIds.contains(zonaId);
    }

    // Getters y Setters

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public List<Integer> getZonasCoberturaIds() {
        if (zonasCoberturaIds == null) zonasCoberturaIds = new ArrayList<>();
        return zonasCoberturaIds;
    }
    public void setZonasCoberturaIds(List<Integer> zonasCoberturaIds) {
        this.zonasCoberturaIds = zonasCoberturaIds != null ? zonasCoberturaIds : new ArrayList<>();
    }

    public boolean isTodasLasZonas() { return todasLasZonas; }
    public void setTodasLasZonas(boolean todasLasZonas) { this.todasLasZonas = todasLasZonas; }

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

    public CuentaBancaria getCuentaRetiro() { return cuentaRetiro; }
    public void setCuentaRetiro(CuentaBancaria cuentaRetiro) {
        this.cuentaRetiro = cuentaRetiro;
        if (cuentaRetiro != null) {
            this.banco = cuentaRetiro.getNombreEntidad();
            this.cuentaBancaria = cuentaRetiro.getNumero();
        }
    }

    public boolean tieneCuentaRetiroCompleta() {
        return cuentaRetiro != null && cuentaRetiro.esCompleta();
    }

    public String getCuentaBancariaEnmascarada() {
        if (cuentaRetiro != null && cuentaRetiro.getNumero() != null && !cuentaRetiro.getNumero().isBlank()) {
            return cuentaRetiro.getNumeroEnmascarado();
        }
        if (cuentaBancaria != null && !cuentaBancaria.isBlank()) {
            return CuentaBancaria.enmascarar(cuentaBancaria);
        }
        return null;
    }

    public Double getCalificacion() { return calificacion; }
    public void setCalificacion(Double calificacion) { this.calificacion = calificacion; }

    public Integer getTotalEntregas() { return totalEntregas; }
    public void setTotalEntregas(Integer totalEntregas) { this.totalEntregas = totalEntregas; }

    public boolean isEmpresa() {
        return TIPO_EMPRESA.equalsIgnoreCase(this.tipo);
    }

    public String getPlacaVehiculo() {
        return placa;
    }

    public String getLicenciaConducir() {
        return licenciaConduccion;
    }
}
