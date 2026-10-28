package com.example.demo.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Parámetros del flujo de recuperación de contraseña.
 * Se pueden ajustar por variables de entorno sin recompilar.
 */
@Component
@ConfigurationProperties(prefix = "cultivus.password-reset")
public class PasswordResetProperties {

    /** Vigencia del enlace. Por defecto 15 minutos. */
    private Duration tokenTtl = Duration.ofMinutes(15);

    /** Ventana móvil usada por los límites de frecuencia. */
    private Duration ventana = Duration.ofMinutes(15);

    private int maxSolicitudesPorEmail = 3;

    private int maxSolicitudesPorIp = 10;

    private int maxIntentosPorIp = 20;

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public Duration getVentana() {
        return ventana;
    }

    public void setVentana(Duration ventana) {
        this.ventana = ventana;
    }

    public int getMaxSolicitudesPorEmail() {
        return maxSolicitudesPorEmail;
    }

    public void setMaxSolicitudesPorEmail(int maxSolicitudesPorEmail) {
        this.maxSolicitudesPorEmail = maxSolicitudesPorEmail;
    }

    public int getMaxSolicitudesPorIp() {
        return maxSolicitudesPorIp;
    }

    public void setMaxSolicitudesPorIp(int maxSolicitudesPorIp) {
        this.maxSolicitudesPorIp = maxSolicitudesPorIp;
    }

    public int getMaxIntentosPorIp() {
        return maxIntentosPorIp;
    }

    public void setMaxIntentosPorIp(int maxIntentosPorIp) {
        this.maxIntentosPorIp = maxIntentosPorIp;
    }
}
