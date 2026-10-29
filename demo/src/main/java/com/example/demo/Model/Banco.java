package com.example.demo.Model;

import java.text.Normalizer;
import java.util.Locale;

/**
 * US-24 / Fase 1: Catálogo cerrado de entidades financieras soportadas para retiros simulados.
 */
public enum Banco {
    BANCOLOMBIA("BANCOLOMBIA", "Bancolombia", TipoEntidad.BANCO),
    NEQUI("NEQUI", "Nequi", TipoEntidad.BILLETERA_DIGITAL),
    DAVIVIENDA("DAVIVIENDA", "Davivienda", TipoEntidad.BANCO),
    DAVIPLATA("DAVIPLATA", "Daviplata", TipoEntidad.BILLETERA_DIGITAL),
    BANCO_DE_BOGOTA("BANCO_DE_BOGOTA", "Banco de Bogotá", TipoEntidad.BANCO),
    BBVA_COLOMBIA("BBVA_COLOMBIA", "BBVA Colombia", TipoEntidad.BANCO),
    NU_COLOMBIA("NU_COLOMBIA", "Nu Colombia", TipoEntidad.BANCO),
    BANCO_CAJA_SOCIAL("BANCO_CAJA_SOCIAL", "Banco Caja Social", TipoEntidad.BANCO),
    BANCO_AGRARIO("BANCO_AGRARIO", "Banco Agrario de Colombia", TipoEntidad.BANCO),
    DALE("DALE", "Dale!", TipoEntidad.BILLETERA_DIGITAL),
    OTRO("OTRO", "Otro", TipoEntidad.BANCO);

    public enum TipoEntidad {
        BANCO,
        BILLETERA_DIGITAL
    }

    public static final class ResultadoMapeo {
        private final Banco banco;
        private final String bancoOtroNombre;

        public ResultadoMapeo(Banco banco, String bancoOtroNombre) {
            this.banco = banco;
            this.bancoOtroNombre = bancoOtroNombre;
        }

        public Banco getBanco() { return banco; }
        public String getBancoOtroNombre() { return bancoOtroNombre; }
    }

    private final String codigo;
    private final String nombreVisible;
    private final TipoEntidad tipo;

    Banco(String codigo, String nombreVisible, TipoEntidad tipo) {
        this.codigo = codigo;
        this.nombreVisible = nombreVisible;
        this.tipo = tipo;
    }

    public String getCodigo() { return codigo; }
    public String getNombreVisible() { return nombreVisible; }
    public TipoEntidad getTipo() { return tipo; }

    public boolean esBilleteraDigital() {
        return tipo == TipoEntidad.BILLETERA_DIGITAL;
    }

    /**
     * Normaliza una cadena quitando tildes/diacríticos, signos de puntuación y pasando a mayúsculas.
     */
    public static String normalizar(String input) {
        if (input == null) return "";
        String sinTildes = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.replaceAll("[^a-zA-Z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Mapea un texto libre legado al catálogo ignorando tildes y mayúsculas.
     * Si no coincide con ninguna entidad conocida, devuelve OTRO con el texto original como nombre de entidad.
     * Si el texto es nulo o vacío, devuelve null.
     */
    public static ResultadoMapeo mapearTextoLibre(String textoLibre) {
        if (textoLibre == null || textoLibre.trim().isEmpty()) {
            return null;
        }
        String limpio = textoLibre.trim();
        String norm = normalizar(limpio);
        if (norm.isEmpty()) {
            return null;
        }

        for (Banco b : values()) {
            if (b == OTRO) continue;
            if (norm.equals(normalizar(b.codigo)) || norm.equals(normalizar(b.nombreVisible)) || norm.equals(normalizar(b.name()))) {
                return new ResultadoMapeo(b, null);
            }
        }

        // Alias comunes sin tildes ni mayúsculas
        switch (norm) {
            case "BANCO BOGOTA":
            case "BOGOTA":
                return new ResultadoMapeo(BANCO_DE_BOGOTA, null);
            case "BBVA":
                return new ResultadoMapeo(BBVA_COLOMBIA, null);
            case "NU":
            case "NUBANK":
                return new ResultadoMapeo(NU_COLOMBIA, null);
            case "CAJA SOCIAL":
            case "BCSC":
                return new ResultadoMapeo(BANCO_CAJA_SOCIAL, null);
            case "BANCO AGRARIO":
            case "AGRARIO":
                return new ResultadoMapeo(BANCO_AGRARIO, null);
            case "OTRO":
                return new ResultadoMapeo(OTRO, null);
            default:
                return new ResultadoMapeo(OTRO, limpio);
        }
    }

    /**
     * Devuelve el enum Banco para el texto libre dado (o OTRO si no coincide). Null si texto vacío.
     */
    public static Banco desdeTextoLibre(String textoLibre) {
        ResultadoMapeo res = mapearTextoLibre(textoLibre);
        return res != null ? res.getBanco() : null;
    }
}

