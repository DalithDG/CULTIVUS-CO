package com.example.demo.services;

import com.example.demo.Model.EventoWompi;
import com.example.demo.repository.EventoWompiRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Orquesta el procesamiento de un evento (webhook) de Wompi:
 * 1) exige secreto configurado, 2) parsea el JSON, 3) valida la firma,
 * 4) ignora eventos distintos de transaction.updated, 5) aplica idempotencia
 * y 6) delega en {@link PagoConfirmacionService}.
 *
 * Nada se persiste antes de validar la firma. No registra secretos ni el cuerpo.
 */
@Service
public class WompiWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WompiWebhookService.class);

    public static final String EVENTO_SOPORTADO = "transaction.updated";

    public enum Resultado {
        /** Evento aplicado (200). */
        PROCESADO,
        /** Evento ya procesado antes (200, sin hacer nada). */
        DUPLICADO,
        /** Evento de otro tipo o sin efecto (200). */
        IGNORADO,
        /** Firma inválida (401). */
        FIRMA_INVALIDA,
        /** JSON ilegible o sin la estructura esperada (400). */
        JSON_INVALIDO,
        /** WOMPI_EVENTS_SECRET sin configurar (503). */
        SECRETO_NO_CONFIGURADO
    }

    private ObjectMapper objectMapper;
    private WompiWebhookVerifier verifier;
    private EventoWompiRepository eventoRepository;
    private PagoConfirmacionService pagoConfirmacionService;

    public WompiWebhookService() {
    }

    @Autowired
    public WompiWebhookService(ObjectMapper objectMapper, WompiWebhookVerifier verifier,
            EventoWompiRepository eventoRepository, PagoConfirmacionService pagoConfirmacionService) {
        this.objectMapper = objectMapper;
        this.verifier = verifier;
        this.eventoRepository = eventoRepository;
        this.pagoConfirmacionService = pagoConfirmacionService;
    }

    public Resultado procesar(String cuerpo, String checksumHeader) {
        // Nunca aceptar sin poder verificar
        if (!verifier.secretoConfigurado()) {
            log.error("WOMPI_EVENTS_SECRET no está configurado: se rechaza el evento de Wompi");
            return Resultado.SECRETO_NO_CONFIGURADO;
        }

        JsonNode evento;
        try {
            evento = (cuerpo == null || cuerpo.isBlank()) ? null : objectMapper.readTree(cuerpo);
        } catch (JsonProcessingException e) {
            return Resultado.JSON_INVALIDO;
        }
        if (evento == null || !evento.isObject()) {
            return Resultado.JSON_INVALIDO;
        }

        // TASK-07: validar firma antes de guardar o tocar cualquier dato
        WompiWebhookVerifier.Resultado firma = verifier.verificar(evento, checksumHeader);
        if (firma == WompiWebhookVerifier.Resultado.SECRETO_NO_CONFIGURADO) {
            return Resultado.SECRETO_NO_CONFIGURADO;
        }
        if (firma != WompiWebhookVerifier.Resultado.VALIDA) {
            log.warn("Evento de Wompi rechazado: firma inválida");
            return Resultado.FIRMA_INVALIDA;
        }

        String tipo = evento.path("event").asText("");
        if (!EVENTO_SOPORTADO.equals(tipo)) {
            log.info("Evento de Wompi ignorado (tipo no soportado): {}", tipo);
            return Resultado.IGNORADO;
        }

        JsonNode transaccion = evento.path("data").path("transaction");
        String transaccionId = transaccion.path("id").asText("");
        String estado = transaccion.path("status").asText("");
        String referencia = transaccion.path("reference").asText("");
        JsonNode monto = transaccion.path("amount_in_cents");
        if (transaccionId.isBlank() || estado.isBlank() || referencia.isBlank() || !monto.canConvertToLong()
                || !monto.isIntegralNumber()) {
            return Resultado.JSON_INVALIDO;
        }

        // TASK-08: idempotencia por transaction.id:status
        String eventoId = EventoWompi.construirId(transaccionId, estado);
        Optional<EventoWompi> previo = eventoRepository.findById(eventoId);
        if (previo.isPresent() && previo.get().isProcesado()) {
            log.info("Evento de Wompi duplicado ignorado: {}", eventoId);
            return Resultado.DUPLICADO;
        }

        // Si quedó a medias (procesado=false) se reprocesa con el mismo registro
        EventoWompi registro = previo.orElseGet(() -> new EventoWompi(transaccionId, estado, referencia));
        registro.setProcesado(false);
        eventoRepository.save(registro);

        PagoConfirmacionService.Resultado aplicado = pagoConfirmacionService.aplicarResultado(
                referencia, transaccionId, estado, monto.asLong());
        log.info("Evento de Wompi {} aplicado con resultado {}", eventoId, aplicado);

        registro.setProcesado(true);
        registro.setProcesadoEn(LocalDateTime.now());
        eventoRepository.save(registro);

        return aplicado == PagoConfirmacionService.Resultado.APLICADO
                ? Resultado.PROCESADO
                : Resultado.IGNORADO;
    }
}
