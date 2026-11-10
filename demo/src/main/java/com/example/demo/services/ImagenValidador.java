package com.example.demo.services;

/**
 * Validación de imágenes subidas por vendedores. Comprueba los BYTES reales del archivo
 * (firma/magic bytes) y no la extensión ni el Content-Type que declara el cliente.
 */
public final class ImagenValidador {

    /** Tamaño máximo permitido: 3 MB. */
    public static final long TAMANO_MAXIMO_BYTES = 3L * 1024 * 1024;

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";

    private ImagenValidador() {
    }

    /**
     * Detecta el tipo real de la imagen por su firma.
     *
     * @return {@code image/jpeg}, {@code image/png}, {@code image/webp} o {@code null} si no es un formato permitido
     */
    public static String detectarTipo(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return PNG;
        }
        // WebP: "RIFF" ???? "WEBP"
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return WEBP;
        }
        return null;
    }

    /**
     * Valida tamaño y tipo real.
     *
     * @throws IllegalArgumentException con un mensaje claro para mostrar al vendedor
     */
    public static String validar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("No se recibió ninguna imagen.");
        }
        if (bytes.length > TAMANO_MAXIMO_BYTES) {
            throw new IllegalArgumentException("La imagen supera el máximo de 3 MB.");
        }
        String tipo = detectarTipo(bytes);
        if (tipo == null) {
            throw new IllegalArgumentException("Formato no permitido. Usa una imagen JPEG, PNG o WebP.");
        }
        return tipo;
    }
}

