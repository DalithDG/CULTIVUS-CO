package com.example.demo.services;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sin Mongo ni internet. Verifica que Spring puede crear los beans de Wompi
 * (error real: "No default constructor found") y el comportamiento sin respuesta.
 */
class WompiApiClientTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WompiApiClient.class, WompiWebhookVerifier.class)
            .withPropertyValues(
                    "wompi.api-base-url=http://localhost:1/v1", // puerto cerrado: simula Wompi caido
                    "wompi.private-key=prv_test_falsa",
                    "wompi.events-secret=test_events_secret_123",
                    "wompi.api-timeout-ms=500");

    @Test
    void springCreaLosBeansDeWompi() {
        runner.run(ctx -> {
            assertEquals(null, ctx.getStartupFailure());
            assertTrue(ctx.containsBean("wompiApiClient"));
            assertTrue(ctx.getBean(WompiWebhookVerifier.class).secretoConfigurado());
        });
    }

    @Test
    void springArmaLaCadenaCompletaDelWebhook() {
        runner.withBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .withBean(com.example.demo.repository.PedidoRepository.class,
                        () -> org.mockito.Mockito.mock(com.example.demo.repository.PedidoRepository.class))
                .withBean(com.example.demo.repository.EventoWompiRepository.class,
                        () -> org.mockito.Mockito.mock(com.example.demo.repository.EventoWompiRepository.class))
                .withBean(PagoConfirmacionService.class)
                .withBean(WompiWebhookService.class)
                .withBean(com.example.demo.Controller.WompiWebhookController.class)
                .run(ctx -> {
                    assertEquals(null, ctx.getStartupFailure());
                    assertTrue(ctx.containsBean("wompiWebhookController"));
                });
    }

    @Test
    void siWompiNoRespondeDevuelveVacioSinLanzarExcepcion() {
        runner.run(ctx -> assertFalse(ctx.getBean(WompiApiClient.class)
                .consultarTransaccion("1292-1602113476-10985").isPresent()));
    }

    @Test
    void idInvalidoNoSeConsulta() {
        assertFalse(WompiApiClient.esIdValido("../etc/passwd"));
        assertFalse(WompiApiClient.esIdValido(""));
        assertFalse(WompiApiClient.esIdValido(null));
        assertTrue(WompiApiClient.esIdValido("1292-1602113476-10985"));
    }
}
