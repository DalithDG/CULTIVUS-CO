package com.example.demo.services;

import com.example.demo.Model.embebidos.UnidadMedida;

import java.math.BigDecimal;

/**
 * Validaci\u00f3n \u00fanica de cantidades seg\u00fan la unidad de venta de la OFERTA (US-06).
 *
 * <p>Se usa en carrito, pago y OfertaService. Nunca lee la unidad del cat\u00e1logo.</p>
 */
public final class ValidacionCantidad {

    private static final int MAX_DECIMALES = 2;

    private ValidacionCantidad() {
    }

    /**
     * Valida una cantidad de compra.
     *
     * @param unidad       unidad de la oferta (si es null se asume "un", solo enteros)
     * @param cantidad     cantidad pedida
     * @param compraMinima compra m\u00ednima de la oferta (null = sin m\u00ednimo)
     * @param stock        stock disponible de la oferta (null = sin l\u00edmite)
     * @return mensaje de error en espa\u00f1ol, o {@code null} si la cantidad es v\u00e1lida
     */
    public static String validarCantidad(UnidadMedida unidad, Double cantidad, Double compraMinima, Double stock) {
        UnidadMedida u = unidad != null ? unidad : UnidadMedida.porDefecto();

        if (!esNumeroValido(cantidad) || cantidad <= 0) {
            return "La cantidad debe ser mayor a 0.";
        }

        String errorForma = validarForma(u, cantidad);
        if (errorForma != null) {
            return errorForma;
        }

        BigDecimal c = BigDecimal.valueOf(cantidad);
        if (compraMinima != null && esNumeroValido(compraMinima) && c.compareTo(BigDecimal.valueOf(compraMinima)) < 0) {
            return "La compra m\u00ednima es " + formatear(compraMinima) + " " + abreviatura(u) + ".";
        }
        if (stock != null && esNumeroValido(stock) && c.compareTo(BigDecimal.valueOf(stock)) > 0) {
            return "Solo hay " + formatear(stock) + " " + abreviatura(u) + " disponibles.";
        }
        return null;
    }

    /** Igual que {@link #validarCantidad} pero lanza {@link IllegalArgumentException} si no es v\u00e1lida. */
    public static void exigirCantidad(UnidadMedida unidad, Double cantidad, Double compraMinima, Double stock) {
        String error = validarCantidad(unidad, cantidad, compraMinima, stock);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    /**
     * Valida que un valor de oferta (stock o compra m\u00ednima) sea coherente con la unidad:
     * entero si no admite decimales, m\u00e1ximo 2 decimales si los admite. No exige mayor a 0.
     *
     * @param etiqueta nombre del campo para el mensaje (por ejemplo "El stock")
     * @return mensaje de error o {@code null}
     */
    public static String validarCoherenciaUnidad(UnidadMedida unidad, Double valor, String etiqueta) {
        UnidadMedida u = unidad != null ? unidad : UnidadMedida.porDefecto();
        if (!esNumeroValido(valor)) {
            return etiqueta + " debe ser un n\u00famero v\u00e1lido.";
        }
        String error = validarForma(u, valor);
        if (error == null) {
            return null;
        }
        if (!u.permiteDecimales()) {
            return etiqueta + " debe ser un n\u00famero entero porque " + descripcionVentaCompleta(u) + ".";
        }
        return etiqueta + " admite m\u00e1ximo " + MAX_DECIMALES + " decimales.";
    }

    /** Suma exacta de cantidades (evita 0.1 + 0.2 = 0.30000000000000004). */
    public static double sumar(double a, double b) {
        return BigDecimal.valueOf(a).add(BigDecimal.valueOf(b)).doubleValue();
    }

    // \u2500\u2500 internos \u2500\u2500

    private static String validarForma(UnidadMedida u, double valor) {
        BigDecimal v = BigDecimal.valueOf(valor).stripTrailingZeros();
        if (!u.permiteDecimales()) {
            if (v.scale() > 0) {
                return mensajeEntero(u);
            }
            return null;
        }
        if (v.scale() > MAX_DECIMALES) {
            return "Ingresa como m\u00e1ximo " + MAX_DECIMALES + " decimales (por ejemplo, 1,25 " + abreviatura(u) + ").";
        }
        return null;
    }

    /** Mensaje que explica el motivo de la regla de enteros, por unidad. */
    static String mensajeEntero(UnidadMedida u) {
        String ab = u.getAbreviatura() != null ? u.getAbreviatura().toLowerCase() : "";
        switch (ab) {
            case "doc":
                return "Las docenas se venden completas; ingresa un n\u00famero entero.";
            case "manojo":
                return "Los manojos se venden completos; ingresa un n\u00famero entero.";
            case "bandeja":
                return "Las bandejas se venden completas; ingresa un n\u00famero entero.";
            case "un":
                return "Las unidades se venden completas; ingresa un n\u00famero entero.";
            default:
                return "Este producto se vende por " + nombreUnidad(u) + " completo(a); ingresa un n\u00famero entero.";
        }
    }

    private static String descripcionVentaCompleta(UnidadMedida u) {
        String ab = u.getAbreviatura() != null ? u.getAbreviatura().toLowerCase() : "";
        switch (ab) {
            case "doc": return "las docenas se venden completas";
            case "manojo": return "los manojos se venden completos";
            case "bandeja": return "las bandejas se venden completas";
            case "un": return "las unidades se venden completas";
            default: return "este producto se vende por " + nombreUnidad(u) + " completo(a)";
        }
    }

    private static String nombreUnidad(UnidadMedida u) {
        if (u.getNombre() != null && !u.getNombre().isBlank()) {
            return u.getNombre().toLowerCase();
        }
        return abreviatura(u);
    }

    private static String abreviatura(UnidadMedida u) {
        return u.getAbreviatura() != null ? u.getAbreviatura() : "un";
    }

    private static boolean esNumeroValido(Double d) {
        return d != null && !d.isNaN() && !d.isInfinite();
    }

    /** 2 \u2192 "2"; 2.5 \u2192 "2,5"; 0.25 \u2192 "0,25". */
    static String formatear(double d) {
        BigDecimal b = BigDecimal.valueOf(d).stripTrailingZeros();
        if (b.scale() < 0) {
            b = b.setScale(0);
        }
        return b.toPlainString().replace('.', ',');
    }
}
