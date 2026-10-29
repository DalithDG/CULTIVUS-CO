package com.example.demo.Model;

/**
 * US-24 / Fase 1: Tipos de documento de identidad del titular de la cuenta bancaria.
 */
public enum TipoDocumento {
    CC("Cédula de ciudadanía (CC)"),
    CE("Cédula de extranjería (CE)"),
    NIT("NIT"),
    PASAPORTE("Pasaporte");

    private final String etiqueta;

    TipoDocumento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}

