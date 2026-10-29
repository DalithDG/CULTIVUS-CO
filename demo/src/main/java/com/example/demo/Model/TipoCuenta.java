package com.example.demo.Model;

/**
 * US-24 / Fase 1: Tipos de cuenta bancaria admitidos para retiros.
 */
public enum TipoCuenta {
    AHORROS("Cuenta de ahorros"),
    CORRIENTE("Cuenta corriente"),
    DEPOSITO_ELECTRONICO("Depósito electrónico");

    private final String etiqueta;

    TipoCuenta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}

