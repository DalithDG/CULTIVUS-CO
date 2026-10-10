package com.example.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Valida la autenticidad de los eventos (webhook) enviados por Wompi.
 *
 * Checksum = SHA-256( valores de signature.properties tomados de "data" (en orden)
 *                     + timestamp + secreto de eventos ).
 *
 * Usa el secreto de EVENTOS (WOMPI_EVENTS_SECRET), distinto al de integridad.
 * Nunca registra el secreto ni el checksum calculado.
 */
@Component
public class WompiWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(WompiWebhookVerifier.class);

    public enum Resultado {
        VALIDA,
        INVALIDA,
        SECRETO_NO_CONFIGURADO
    }

    private final String eventsSecret;

    public WompiWebhookVerifier(@Value("${wompi.events-secret:}") String eventsSecret) {
        this.eventsSecret = eventsSecret == null ? "" : eventsSecret.trim();
    }

    public boolean secretoConfigurado() {
        return !eventsSecret.isEmpty();
    }

    /**
     * @param evento         JSON completo del evento
     * @param checksumHeader valor del header X-Event-Checksum (respaldo si el cuerpo no trae checksum)
     */
    public Resultado verificar(JsonNode evento, String checksumHeader) {
        if (!secretoConfigurado()) {
            return Resultado.SECRETO_NO_CONFIGURADO;
        }
        if (evento == null || !evento.isObject()) {
            return Resultado.INVALIDA;
        }

        String recibido = evento.path("signature").path("checksum").asText("");
        if (recibido.isBlank() && checksumHeader != null) {
            recibido = checksumHeader;
        }
        recibido = recibido.trim();
        if (recibido.isEmpty()) {
            return Resultado.INVALIDA;
        }

        String esperado;
        try {
            esperado = calcularChecksum(evento);
        } catch (IllegalArgumentException e) {
            log.warn("Evento Wompi con firma mal formada: {}", e.getMessage());
            return Resultado.INVALIDA;
        }

        // Comparación en tiempo constante (el hex se normaliza a minúsculas: Wompi lo envía en mayúsculas)
        boolean iguales = MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                recibido.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return iguales ? Resultado.VALIDA : Resultado.INVALIDA;
    }

    /**
     * Calcula el checksum esperado (hex en minúsculas).
     *
     * @throws IllegalArgumentException si falta una propiedad, la lista de propiedades
     *                                  o el timestamp
     */
    String calcularChecksum(JsonNode evento) {
        JsonNode properties = evento.path("signature").path("properties");
        if (!properties.isArray() || properties.isEmpty()) {
            throw new IllegalArgumentException("signature.properties ausente o vacío");
        }

        StringBuilder cadena = new StringBuilder();
        for (JsonNode propiedad : properties) {
            if (!propiedad.isTextual() || propiedad.asText().isBlank()) {
                throw new IllegalArgumentException("signature.properties contiene un elemento inválido");
            }
            String ruta = propiedad.asText();
            JsonNode valor = evento.path("data");
            for (String parte : ruta.split("\\.")) {
                valor = valor.path(parte);
            }
            if (valor.isMissingNode() || valor.isNull() || valor.isContainerNode()) {
                throw new IllegalArgumentException("Propiedad de firma no encontrada en data: " + ruta);
            }
            cadena.append(valor.asText());
        }

        JsonNode timestamp = evento.path("timestamp");
        if (timestamp.isMissingNode() || timestamp.isNull() || timestamp.isContainerNode()
                || timestamp.asText().isBlank()) {
            throw new IllegalArgumentException("timestamp ausente");
        }
        cadena.append(timestamp.asText());
        cadena.append(eventsSecret);

        return sha256Hex(cadena.toString());
    }

    private static String sha256Hex(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
