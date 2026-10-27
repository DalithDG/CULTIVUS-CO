package com.example.demo.Model;

import java.util.Optional;

/**
 * Estados del ciclo de vida de un sub-pedido. Es la única fuente de verdad: el resto del código
 * no debe comparar el estado contra strings literales sino contra este enum.
 *
 * Los valores legados (PROCESANDO, COMPLETADO, PENDIENTE_PAGO) se normalizan al leer y al escribir
 * y nunca se vuelven a persistir.
 */
public enum EstadoPedido {

    PENDIENTE("Pendiente de pago", "pendiente"),
    CONFIRMADO("Confirmado", "confirmado"),
    EN_PROCESO("En preparación", "proceso"),
    LISTO_PARA_RECOGER("Listo para recoger", "listo"),
    ENVIADO("Enviado", "enviado"),
    ENTREGADO("Entregado", "completado"),
    CANCELADO("Cancelado", "cancelado"),
    /** Pagado pero sin stock al confirmar: el admin gestiona el reembolso manual. */
    EN_REVISION("En revisión", "revision");

    private final String etiqueta;
    private final String css;

    EstadoPedido(String etiqueta, String css) {
        this.etiqueta = etiqueta;
        this.css = css;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Sufijo CSS del estado (pendiente, confirmado, proceso, listo, enviado, completado, cancelado, revision). */
    public String css() {
        return css;
    }

    public boolean esTerminal() {
        return this == ENTREGADO || this == CANCELADO;
    }

    /** Estados que representan una venta confirmada y con stock descontado (CONFIRMADO..ENTREGADO). */
    public boolean esVentaConfirmada() {
        return this == CONFIRMADO || this == EN_PROCESO || this == LISTO_PARA_RECOGER
                || this == ENVIADO || this == ENTREGADO;
    }

    /** Ventas activas en curso que aún están pendientes de entrega (CONFIRMADO..ENVIADO). */
    public boolean esVentaEnCurso() {
        return this == CONFIRMADO || this == EN_PROCESO || this == LISTO_PARA_RECOGER
                || this == ENVIADO;
    }

    /** Interpreta un texto (incluidos los valores legados). Vacío -> PENDIENTE; desconocido -> empty. */
    public static Optional<EstadoPedido> interpretar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.of(PENDIENTE);
        }
        switch (texto.trim().toUpperCase()) {
            case "PENDIENTE":
            case "PENDIENTE_PAGO":
                return Optional.of(PENDIENTE);
            case "CONFIRMADO":
                return Optional.of(CONFIRMADO);
            case "EN_PROCESO":
            case "PROCESANDO":
                return Optional.of(EN_PROCESO);
            case "LISTO_PARA_RECOGER":
                return Optional.of(LISTO_PARA_RECOGER);
            case "ENVIADO":
                return Optional.of(ENVIADO);
            case "ENTREGADO":
            case "COMPLETADO":
                return Optional.of(ENTREGADO);
            case "CANCELADO":
                return Optional.of(CANCELADO);
            case "EN_REVISION":
                return Optional.of(EN_REVISION);
            default:
                return Optional.empty();
        }
    }

    /** Igual que {@link #interpretar(String)} pero lanza IllegalArgumentException si no es válido. */
    public static EstadoPedido desde(String texto) {
        return interpretar(texto)
                .orElseThrow(() -> new IllegalArgumentException("Estado no válido: " + texto));
    }

    /** Nombre canónico del estado (sin legados); si el texto no es reconocible, se devuelve tal cual. */
    public static String normalizar(String texto) {
        return interpretar(texto).map(Enum::name).orElse(texto);
    }
}
