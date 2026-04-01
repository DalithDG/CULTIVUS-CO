package com.example.demo.Model.embebidos;

import java.time.LocalDateTime;

public class DatosPago {

    // EFECTIVO, TRANSFERENCIA, TARJETA_CREDITO, TARJETA_DEBITO
    private String metodo;
    private Double monto;
    // PENDIENTE, COMPLETADO, FALLIDO, REEMBOLSADO
    private String estado = "COMPLETADO";
    private LocalDateTime fechaPago;

    public DatosPago() {
        this.fechaPago = LocalDateTime.now();
        this.estado = "COMPLETADO";
    }

    public DatosPago(String metodo, Double monto) {
        this.metodo = metodo;
        this.monto = monto;
        this.estado = "COMPLETADO";
        this.fechaPago = LocalDateTime.now();
    }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDateTime fechaPago) { this.fechaPago = fechaPago; }
}