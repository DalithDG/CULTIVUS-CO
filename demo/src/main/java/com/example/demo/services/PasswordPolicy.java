package com.example.demo.services;

import java.nio.charset.StandardCharsets;

/**
 * Política única de contraseñas de Cultivus.
 *
 * Se reutiliza en el registro/perfil y en el restablecimiento para que ambas
 * rutas exijan exactamente las mismas reglas. No se recortan espacios: una
 * contraseña puede contenerlos, pero debe cumplir el resto de requisitos.
 */
public final class PasswordPolicy {

    public static final int LONGITUD_MINIMA = 8;

    /**
     * BCrypt solo considera los primeros 72 bytes. Limitamos por bytes UTF-8
     * para no aceptar contraseñas que en la práctica se truncarían.
     */
    public static final int LONGITUD_MAXIMA_BYTES = 72;

    private static final String CARACTERES_ESPECIALES = "!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?";

    private PasswordPolicy() {
    }

    /**
     * @return null si la contraseña cumple la política; un mensaje seguro para
     *         mostrar al usuario en caso contrario.
     */
    public static String validar(String contrasena) {
        if (contrasena == null || contrasena.isEmpty()) {
            return "La contraseña es obligatoria";
        }
        if (contrasena.length() < LONGITUD_MINIMA) {
            return "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres";
        }
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > LONGITUD_MAXIMA_BYTES) {
            return "La contraseña no puede superar los " + LONGITUD_MAXIMA_BYTES + " bytes";
        }
        if (!contrasena.matches(".*[A-Z].*")) {
            return "La contraseña debe contener al menos una mayúscula";
        }
        if (!contrasena.matches(".*[a-z].*")) {
            return "La contraseña debe contener al menos una minúscula";
        }
        if (!contrasena.matches(".*[0-9].*")) {
            return "La contraseña debe contener al menos un número";
        }
        if (!contrasena.matches(".*[" + CARACTERES_ESPECIALES + "].*")) {
            return "La contraseña debe contener al menos un carácter especial";
        }
        return null;
    }
}
