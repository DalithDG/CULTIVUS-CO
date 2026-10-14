package com.example.demo.Controller;

import com.example.demo.services.WompiWebhookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook de eventos de Wompi.
 *
 * Vive bajo /api/** a propósito: /pago/** exige sesión y CSRF, que Wompi no envía.
 * La autenticidad se garantiza con el checksum del evento (ver WompiWebhookVerifier).
 *
 * Respuestas: 200 procesado / duplicado / ignorado, 401 firma inválida,
 * 400 JSON ilegible, 503 WOMPI_EVENTS_SECRET sin configurar.
 */
@RestController
@RequestMapping("/api/wompi")
public class WompiWebhookController {

    @Autowired
    private WompiWebhookService webhookService;

    public WompiWebhookController() {
    }

    public WompiWebhookController(WompiWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> recibirEvento(
            @RequestBody(required = false) String cuerpo,
            @RequestHeader(value = "X-Event-Checksum", required = false) String checksumHeader) {

        WompiWebhookService.Resultado resultado = webhookService.procesar(cuerpo, checksumHeader);

        switch (resultado) {
            case PROCESADO:
                return responder(200, "procesado");
            case DUPLICADO:
                return responder(200, "duplicado");
            case IGNORADO:
                return responder(200, "ignorado");
            case FIRMA_INVALIDA:
                return responder(401, "firma invalida");
            case JSON_INVALIDO:
                return responder(400, "json invalido");
            case SECRETO_NO_CONFIGURADO:
            default:
                return responder(503, "webhook no configurado");
        }
    }

    private ResponseEntity<Map<String, String>> responder(int status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("resultado", mensaje));
    }
}
