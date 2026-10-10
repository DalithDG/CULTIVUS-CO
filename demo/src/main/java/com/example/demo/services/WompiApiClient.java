package com.example.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cliente minimo de la API de Wompi para consultar una transaccion.
 *
 * GET {wompi.api-base-url}/transactions/{id} con "Authorization: Bearer <llave privada>"
 * (desde 2025 Wompi exige la llave privada para esta consulta; con la publica responde 404).
 * La URL base es sandbox por defecto; en produccion: https://production.wompi.co/v1
 *
 * Si Wompi no responde, devuelve Optional.empty() (nunca lanza): el llamador debe
 * mostrar "pago en verificacion". No registra la llave privada.
 */
@Component
public class WompiApiClient {

    private static final Logger log = LoggerFactory.getLogger(WompiApiClient.class);

    private static final Pattern ID_VALIDO = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");

    public record TransaccionWompi(String id, String reference, String status, long amountInCents) {
    }

    private final RestClient restClient;
    private final String privateKey;

    @Autowired
    public WompiApiClient(
            @Value("${wompi.api-base-url:https://sandbox.wompi.co/v1}") String baseUrl,
            @Value("${wompi.private-key:}") String privateKey,
            @Value("${wompi.api-timeout-ms:4000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(quitarSlashFinal(baseUrl))
                .requestFactory(factory)
                .build();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
    }

    /** Constructor para pruebas con un RestClient propio. */
    WompiApiClient(RestClient restClient, String privateKey) {
        this.restClient = restClient;
        this.privateKey = privateKey == null ? "" : privateKey.trim();
    }

    public static boolean esIdValido(String id) {
        return id != null && ID_VALIDO.matcher(id.trim()).matches();
    }

    public Optional<TransaccionWompi> consultarTransaccion(String transaccionId) {
        if (!esIdValido(transaccionId)) {
            return Optional.empty();
        }
        if (privateKey.isEmpty()) {
            log.warn("WOMPI_PRIVATE_KEY no configurada: no se puede consultar la transaccion a Wompi");
            return Optional.empty();
        }
        try {
            JsonNode respuesta = restClient.get()
                    .uri("/transactions/{id}", transaccionId.trim())
                    .header("Authorization", "Bearer " + privateKey)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode data = respuesta == null ? null : respuesta.path("data");
            if (data == null || !data.isObject()) {
                return Optional.empty();
            }
            String id = data.path("id").asText("");
            String reference = data.path("reference").asText("");
            String status = data.path("status").asText("");
            JsonNode monto = data.path("amount_in_cents");
            if (id.isBlank() || reference.isBlank() || status.isBlank() || !monto.isIntegralNumber()) {
                return Optional.empty();
            }
            return Optional.of(new TransaccionWompi(id, reference, status, monto.asLong()));
        } catch (RestClientResponseException e) {
            log.warn("Wompi respondio HTTP {} al consultar la transaccion", e.getStatusCode().value());
            return Optional.empty();
        } catch (RuntimeException e) {
            // Timeout, DNS, conexion rechazada, JSON invalido, etc.
            log.warn("No se pudo consultar la transaccion a Wompi: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String quitarSlashFinal(String url) {
        String limpia = (url == null || url.isBlank()) ? "https://sandbox.wompi.co/v1" : url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        return limpia;
    }
}
