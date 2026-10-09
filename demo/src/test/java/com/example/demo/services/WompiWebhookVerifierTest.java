package com.example.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas puras del checksum de eventos de Wompi (sin Spring, Mongo ni internet).
 * Todos los secretos son FALSOS y solo existen en estas pruebas.
 */
class WompiWebhookVerifierTest {

    private static final String SECRETO = "test_events_secret_123";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Vector fijo calculado FUERA de Java (PowerShell/.NET SHA256 y certutil -hashfile SHA256)
     * sobre la cadena: tx-001 + APPROVED + 2350000 + 1700000000 + test_events_secret_123
     */
    private static final String CHECKSUM_VECTOR =
            "e867d208ffa9d52f94229f5f5eaf3ed50e1539690d439a91ec94a77e4e0aa500";

    private static String eventoJson(String checksum) {
        return "{"
                + "\"event\":\"transaction.updated\","
                + "\"data\":{\"transaction\":{\"id\":\"tx-001\",\"status\":\"APPROVED\","
                + "\"amount_in_cents\":2350000,\"reference\":\"CUL-1\",\"currency\":\"COP\"}},"
                + "\"environment\":\"test\","
                + "\"signature\":{\"properties\":[\"transaction.id\",\"transaction.status\","
                + "\"transaction.amount_in_cents\"],\"checksum\":\"" + checksum + "\"},"
                + "\"timestamp\":1700000000,"
                + "\"sent_at\":\"2023-11-14T22:13:20.000Z\"}";
    }

    private static JsonNode leer(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void eventoValidoCoincideConVectorExterno() throws Exception {
        WompiWebhookVerifier verifier = new WompiWebhookVerifier(SECRETO);
        assertEquals(CHECKSUM_VECTOR, verifier.calcularChecksum(leer(eventoJson(CHECKSUM_VECTOR))));
        assertEquals(WompiWebhookVerifier.Resultado.VALIDA,
                verifier.verificar(leer(eventoJson(CHECKSUM_VECTOR)), null));
    }

    @Test
    void checksumEnMayusculasTambienEsValido() throws Exception {
        // Wompi documenta el checksum en hex mayusculas
        WompiWebhookVerifier verifier = new WompiWebhookVerifier(SECRETO);
        assertEquals(WompiWebhookVerifier.Resultado.VALIDA,
                verifier.verificar(leer(eventoJson(CHECKSUM_VECTOR.toUpperCase())), null));
    }

    @Test
    void checksumDelHeaderSirveDeRespaldoSiElCuerpoNoLoTrae() throws Exception {
        WompiWebhookVerifier verifier = new WompiWebhookVerifier(SECRETO);
        ObjectNode evento = (ObjectNode) leer(eventoJson("x"));
        ((ObjectNode) evento.get("signature")).remove("checksum");
        assertEquals(WompiWebhookVerifier.Resultado.VALIDA, verifier.verificar(evento, CHECKSUM_VECTOR));
    }

    @Test
    void eventoAlteradoEsInvalido() throws Exception {
        WompiWebhookVerifier verifier = new WompiWebhookVerifier(SECRETO);
        ObjectNode evento = (ObjectNode) leer(eventoJson(CHECKSUM_VECTOR));
        // Un atacante cambia el monto manteniendo el checksum original
        ((ObjectNode) evento.get("data").get("transaction")).put("amount_in_cents", 100);
        assertEquals(WompiWebhookVerifier.Resultado.INVALIDA, verifier.verificar(evento, null));

        // ... o el estado
        ObjectNode otro = (ObjectNode) leer(eventoJson(CHECKSUM_VECTOR));
        ((ObjectNode) otro.get("data").get("transaction")).put("status", "DECLINED");
        assertEquals(WompiWebhookVerifier.Resultado.INVALIDA, verifier.verificar(otro, null));

        // ... o el timestamp
        ObjectNode otroTs = (ObjectNode) leer(eventoJson(CHECKSUM_VECTOR));
        otroTs.put("timestamp", 1700000001L);
        assertEquals(WompiWebhookVerifier.Resultado.INVALIDA, verifier.verificar(otroTs, null));
    }

    @Test
    void secretoDistintoEsInvalido() throws Exception {
        WompiWebhookVerifier otroSecreto = new WompiWebhookVerifier("otro_secreto_de_eventos");
        assertEquals(WompiWebhookVerifier.Resultado.INVALIDA,
                otroSecreto.verificar(leer(eventoJson(CHECKSUM_VECTOR)), null));
    }

    @Test
    void propiedadFaltanteEsInvalida() throws Exception {
        WompiWebhookVerifier verifier = new WompiWebhookVerifier(SECRETO);
        ObjectNode evento = (ObjectNode) leer(eventoJson(CHECKSUM_VECTOR));
        // signature.properties apunta a un campo que no existe en data
        ((ObjectNode) evento.get("data").get("transaction")).remove("amount_in_cents");
        assertEquals(WompiWebhookVerifier.Resultado.INVALIDA, verifier.verificar(evento, null));
    }

    @Test
    void sinTimestampEsInvalido() throws Exception {
        WompiWebhookVerifier verifier = new WompiWebhookVerifier(SECRETO);
        ObjectNode evento = (ObjectNode) leer(eventoJson(CHECKSUM_VECTOR));
        evento.remove("timestamp");
        assertEquals(WompiWebhookVerifier.Resultado.INVALIDA, verifier.verificar(evento, null));
    }

    @Test
    void sinListaDePropiedadesEsInvalido() throws Exception {
        WompiWebhookVerifier verifier = new WompiWebhookVerifier(SECRETO);
        ObjectNode evento = (ObjectNode) leer(eventoJson(CHECKSUM_VECTOR));
        ((ObjectNode) evento.get("signature")).remove("properties");
        assertEquals(WompiWebhookVerifier.Resultado.INVALIDA, verifier.verificar(evento, null));
    }

    @Test
    void sinChecksumEsInvalido() throws Exception {
        WompiWebhookVerifier verifier = new WompiWebhookVerifier(SECRETO);
        ObjectNode evento = (ObjectNode) leer(eventoJson("x"));
        ((ObjectNode) evento.get("signature")).remove("checksum");
        assertEquals(WompiWebhookVerifier.Resultado.INVALIDA, verifier.verificar(evento, null));
    }

    @Test
    void sinSecretoConfiguradoNuncaAcepta() throws Exception {
        assertEquals(WompiWebhookVerifier.Resultado.SECRETO_NO_CONFIGURADO,
                new WompiWebhookVerifier("").verificar(leer(eventoJson(CHECKSUM_VECTOR)), null));
        assertEquals(WompiWebhookVerifier.Resultado.SECRETO_NO_CONFIGURADO,
                new WompiWebhookVerifier("   ").verificar(leer(eventoJson(CHECKSUM_VECTOR)), null));
        assertEquals(WompiWebhookVerifier.Resultado.SECRETO_NO_CONFIGURADO,
                new WompiWebhookVerifier(null).verificar(leer(eventoJson(CHECKSUM_VECTOR)), null));
    }
}
