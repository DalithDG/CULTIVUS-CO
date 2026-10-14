package com.example.demo.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class WompiService {

    private static final Logger log = LoggerFactory.getLogger(WompiService.class);

    @Value("${wompi.public-key:}")
    private String publicKey;

    @Value("${wompi.private-key:}")
    private String privateKey;

    @Value("${wompi.integrity-secret:}")
    private String integritySecret;

    @Value("${wompi.events-secret:}")
    private String eventsSecret;

    @Value("${wompi.currency:COP}")
    private String currency;

    @Value("${cultivus.envio.provisional:10000.0}")
    private double envioProvisionalPorDefecto;

    // Inyección opcional: si el proyecto tiene un servicio de configuración dinámica
    @Autowired(required = false)
    private AppConfigService configService;

    public String getPublicKey() {
        return publicKey;
    }

    public String getCurrency() {
        return currency;
    }

    /**
     * Obtiene el costo de envío provisional.
     * Usa AppConfigService si está disponible; si no, usa el valor de properties.
     */
    public double obtenerCostoEnvioProvisional() {
        if (configService != null) {
            return configService.obtenerValorDouble("VALOR_ENVIO_PROVISIONAL", envioProvisionalPorDefecto);
        }
        return envioProvisionalPorDefecto;
    }

    /**
     * Convierte un monto en COP a centavos enteros (sin decimales).
     * Usa Math.round para evitar errores de punto flotante.
     */
    public long convertirACentavos(double montoCop) {
        return Math.round(montoCop * 100.0);
    }

    /**
     * Total a cobrar en centavos: productos + envío. Es el monto que se firma con la
     * firma de integridad y el que luego se compara con lo que reporta Wompi.
     */
    public long calcularTotalCentavos(double subtotalProductos, double costoEnvio) {
        return convertirACentavos(subtotalProductos + costoEnvio);
    }

    /**
     * Genera una referencia única para cada intento de pago.
     * Formato: CUL-{timestamp}-{sufijoHex}
     */
    public String generarReferenciaUnica() {
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "CUL-" + System.currentTimeMillis() + "-" + randomSuffix;
    }

    /**
     * Genera la firma de integridad SHA-256 requerida por Wompi.
     * Fórmula: SHA256(referencia + montoEnCentavos + moneda + secretoIntegridad)
     */
    public String generarFirmaIntegridad(String referencia, long montoCentavos, String moneda) {
        if (integritySecret == null || integritySecret.trim().isEmpty()) {
            log.error("El secreto de integridad de Wompi (WOMPI_INTEGRITY_SECRET) no está configurado.");
            throw new IllegalStateException("Secreto de integridad de Wompi no configurado");
        }

        String cadenaFirma = referencia + montoCentavos + moneda + integritySecret.trim();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cadenaFirma.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error al calcular hash SHA-256 para firma de integridad Wompi", e);
            throw new RuntimeException("Error calculando firma de integridad", e);
        }
    }
}
