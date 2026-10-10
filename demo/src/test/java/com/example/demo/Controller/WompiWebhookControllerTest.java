package com.example.demo.Controller;

import com.example.demo.services.WompiWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el mapeo resultado -> codigo HTTP del webhook, sin contexto de Spring ni Mongo.
 */
@ExtendWith(MockitoExtension.class)
class WompiWebhookControllerTest {

    @Mock
    private WompiWebhookService webhookService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WompiWebhookController(webhookService)).build();
    }

    private void postear(WompiWebhookService.Resultado resultado, int statusEsperado) throws Exception {
        when(webhookService.procesar(any(), any())).thenReturn(resultado);
        mockMvc.perform(post("/api/wompi/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is(statusEsperado));
    }

    @Test
    void procesadoDuplicadoEIgnoradoResponden200() throws Exception {
        postear(WompiWebhookService.Resultado.PROCESADO, 200);
        postear(WompiWebhookService.Resultado.DUPLICADO, 200);
        postear(WompiWebhookService.Resultado.IGNORADO, 200);
    }

    @Test
    void firmaInvalidaResponde401() throws Exception {
        postear(WompiWebhookService.Resultado.FIRMA_INVALIDA, 401);
    }

    @Test
    void jsonInvalidoResponde400() throws Exception {
        postear(WompiWebhookService.Resultado.JSON_INVALIDO, 400);
    }

    @Test
    void sinSecretoResponde503() throws Exception {
        postear(WompiWebhookService.Resultado.SECRETO_NO_CONFIGURADO, 503);
    }

    @Test
    void pasaCuerpoYHeaderDeChecksumAlServicio() throws Exception {
        when(webhookService.procesar(any(), any())).thenReturn(WompiWebhookService.Resultado.IGNORADO);

        mockMvc.perform(post("/api/wompi/webhook")
                .header("X-Event-Checksum", "ABC123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"x\"}"))
                .andExpect(status().isOk());

        verify(webhookService).procesar(eq("{\"event\":\"x\"}"), eq("ABC123"));
    }
}
