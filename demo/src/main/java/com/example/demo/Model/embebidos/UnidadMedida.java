package com.example.demo.Model.embebidos;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Unidad de venta de una oferta (copia embebida, sin colecci\u00f3n propia).
 *
 * <p>Tipos: {@code Peso} y {@code Volumen} admiten decimales; {@code Unidad} solo enteros;
 * {@code Fraccionable} es el tipo de una unidad personalizada ("Otra") que el vendedor
 * marc\u00f3 como fraccionable. {@link #permiteDecimales()} se deriva siempre del tipo.</p>
 */
public class UnidadMedida implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public static final String TIPO_PESO = "Peso";
    public static final String TIPO_VOLUMEN = "Volumen";
    public static final String TIPO_UNIDAD = "Unidad";
    public static final String TIPO_FRACCIONABLE = "Fraccionable";

    /** Abreviatura reservada para unidades personalizadas elegidas con "Otra". */
    public static final String ABREVIATURA_OTRA = "otra";

    /** Lista fija de unidades de medida (antes viv\u00eda en ProductoController). */
    public static final List<UnidadMedida> UNIDADES = Collections.unmodifiableList(Arrays.asList(
            new UnidadMedida("Kilogramos", "kg", TIPO_PESO),
            new UnidadMedida("Gramos", "g", TIPO_PESO),
            new UnidadMedida("Libras", "lb", TIPO_PESO),
            new UnidadMedida("Litros", "L", TIPO_VOLUMEN),
            new UnidadMedida("Mililitros", "ml", TIPO_VOLUMEN),
            new UnidadMedida("Unidades", "un", TIPO_UNIDAD),
            new UnidadMedida("Docena", "doc", TIPO_UNIDAD),
            new UnidadMedida("Manojo", "manojo", TIPO_UNIDAD),
            new UnidadMedida("Bandeja", "bandeja", TIPO_UNIDAD)
    ));

    private String nombre;
    private String abreviatura;
    private String tipo;

    public UnidadMedida() {
    }

    public UnidadMedida(String nombre, String abreviatura, String tipo) {
        this.nombre = nombre;
        this.abreviatura = abreviatura;
        this.tipo = tipo;
    }

    /** true si la unidad admite cantidades decimales (Peso, Volumen o personalizada fraccionable). */
    public boolean permiteDecimales() {
        return TIPO_PESO.equalsIgnoreCase(tipo)
                || TIPO_VOLUMEN.equalsIgnoreCase(tipo)
                || TIPO_FRACCIONABLE.equalsIgnoreCase(tipo);
    }

    /** Copia independiente (las ofertas guardan una copia, nunca la referencia compartida). */
    public UnidadMedida copia() {
        return new UnidadMedida(nombre, abreviatura, tipo);
    }

    /** Etiqueta para rotular precios: "Precio por kg". */
    public String etiquetaPrecio() {
        return "Precio por " + (abreviatura != null ? abreviatura : "un");
    }

    /** Atributo HTML {@code step} de los inputs de cantidad. */
    public String paso() {
        return permiteDecimales() ? "0.01" : "1";
    }

    /** Unidad "un" por defecto (si no hay ninguna otra informaci\u00f3n). */
    public static UnidadMedida porDefecto() {
        return deAbreviatura("un");
    }

    /**
     * Busca en la lista fija por abreviatura (sin distinguir may\u00fasculas) o por nombre/alias.
     * Devuelve {@code null} si no hay mapeo.
     */
    public static UnidadMedida buscarEnLista(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.trim();
        for (UnidadMedida u : UNIDADES) {
            if (u.abreviatura.equals(t)) {
                return u.copia();
            }
        }
        String k = Normalizer.normalize(t, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (UnidadMedida u : UNIDADES) {
            String nombreNorm = Normalizer.normalize(u.nombre, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            if (u.abreviatura.toLowerCase(Locale.ROOT).equals(k) || nombreNorm.equals(k)) {
                return u.copia();
            }
        }
        switch (k) {
            case "kilo": case "kilos": case "kilogramo": return porAbreviaturaExacta("kg");
            case "gramo": return porAbreviaturaExacta("g");
            case "libra": return porAbreviaturaExacta("lb");
            case "litro": return porAbreviaturaExacta("L");
            case "mililitro": return porAbreviaturaExacta("ml");
            case "unid": case "unidad": case "u": case "und": return porAbreviaturaExacta("un");
            case "docenas": return porAbreviaturaExacta("doc");
            case "manojos": return porAbreviaturaExacta("manojo");
            case "bandejas": return porAbreviaturaExacta("bandeja");
            default: return null;
        }
    }

    private static UnidadMedida porAbreviaturaExacta(String abreviatura) {
        for (UnidadMedida u : UNIDADES) {
            if (u.abreviatura.equals(abreviatura)) {
                return u.copia();
            }
        }
        return null;
    }

    /** Igual que {@link #buscarEnLista(String)} pero sin mapeo devuelve "un". */
    public static UnidadMedida deAbreviatura(String abreviatura) {
        UnidadMedida u = buscarEnLista(abreviatura);
        return u != null ? u : porAbreviaturaExacta("un");
    }

    /** Crea una unidad personalizada ("Otra"): tipo Unidad por defecto, Fraccionable si se marca. */
    public static UnidadMedida personalizada(String nombre, boolean fraccionable) {
        String limpio = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("Escribe el nombre de la unidad de venta.");
        }
        if (limpio.length() > 20) {
            throw new IllegalArgumentException("El nombre de la unidad no puede superar 20 caracteres.");
        }
        return new UnidadMedida(limpio, limpio, fraccionable ? TIPO_FRACCIONABLE : TIPO_UNIDAD);
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getAbreviatura() { return abreviatura; }
    public void setAbreviatura(String abreviatura) { this.abreviatura = abreviatura; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}
