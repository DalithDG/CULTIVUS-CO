package com.example.demo.Model;

/** Modalidad de entrega elegida UNA sola vez por compra; todos sus sub-pedidos la heredan. */
public enum ModalidadEntrega {

    DOMICILIO("Envío a domicilio"),
    @Deprecated
    RECOGE_EN_TIENDA("Recoger en tienda");

    private final String etiqueta;

    ModalidadEntrega(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Texto -> modalidad. Siempre normaliza a DOMICILIO. */
    public static ModalidadEntrega desde(String texto) {
        return DOMICILIO;
    }
}
