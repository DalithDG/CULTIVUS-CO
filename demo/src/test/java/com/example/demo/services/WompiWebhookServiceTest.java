package com.example.demo.services;

import com.example.demo.Model.EventoWompi;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.repository.EventoWompiRepository;
import com.example.demo.repository.PedidoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba del webhook completo (verificador + idempotencia + aplicacion del resultado)
 * con repositorios simulados: sin Mongo, sin Spring y sin internet.
 * Los secretos son FALSOS.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WompiWebhookServiceTest {

    private static final String SECRETO = "test_events_secret_123";
    private static final String REFERENCIA = "CUL-1700000000000-A1B2C3";
    private static final long MONTO = 2_350_000L;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private EventoWompiRepository eventoRepository;

    private final Map<String, EventoWompi> eventosGuardados = new HashMap<>();
    private Pedido pedido;
    private WompiWebhookService servicio;

    @BeforeEach
    void setUp() {
        // Repositorio de eventos con estado en memoria
        when(eventoRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(eventosGuardados.get(inv.<String>getArgument(0))));
        when(eventoRepository.save(any(EventoWompi.class))).thenAnswer(inv -> {
            EventoWompi e = inv.getArgument(0);
            eventosGuardados.put(e.getId(), e);
            return e;
        });

        // Un pedido PENDIENTE con el monto firmado al iniciar el pago
        pedido = nuevoPedido("PENDIENTE", MONTO);
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        servicio = new WompiWebhookService(
                new ObjectMapper(),
                new WompiWebhookVerifier(SECRETO),
                eventoRepository,
                new PagoConfirmacionService(pedidoRepository));
    }

    private static Pedido nuevoPedido(String estadoPago, Long montoFirmado) {
        DatosPago pago = new DatosPago("WOMPI", 100000.0);
        pago.setEstado(estadoPago);
        pago.setReferencia(REFERENCIA);
        pago.setMontoCentavosFirmado(montoFirmado);
        Pedido p = new Pedido();
        p.setPago(pago);
        return p;
    }

    // ---------- helpers de eventos firmados ----------

    private static String sha256Hex(String texto) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String eventoFirmado(String tipo, String txId, String estado, long monto, String secreto)
            throws Exception {
        long timestamp = 1700000000L;
        String checksum = sha256Hex(txId + estado + monto + timestamp + secreto);
        return "{\"event\":\"" + tipo + "\","
                + "\"data\":{\"transaction\":{\"id\":\"" + txId + "\",\"status\":\"" + estado + "\","
                + "\"amount_in_cents\":" + monto + ",\"reference\":\"" + REFERENCIA + "\"}},"
                + "\"environment\":\"test\","
                + "\"signature\":{\"properties\":[\"transaction.id\",\"transaction.status\","
                + "\"transaction.amount_in_cents\"],\"checksum\":\"" + checksum.toUpperCase() + "\"},"
                + "\"timestamp\":" + timestamp + ",\"sent_at\":\"2023-11-14T22:13:20.000Z\"}";
    }

    private String aprobado(String txId) throws Exception {
        return eventoFirmado("transaction.updated", txId, "APPROVED", MONTO, SECRETO);
    }

    // ---------- Firma invalida ----------

    @Test
    void firmaInvalidaDevuelve401SinTocarNada() throws Exception {
        String cuerpo = eventoFirmado("transaction.updated", "tx-001", "APPROVED", MONTO, "secreto_ajeno");

        assertEquals(WompiWebhookService.Resultado.FIRMA_INVALIDA, servicio.procesar(cuerpo, null));

        verifyNoInteractions(eventoRepository);
        verifyNoInteractions(pedidoRepository);
        assertEquals("PENDIENTE", pedido.getPago().getEstado());
    }

    @Test
    void eventoManipuladoDevuelveFirmaInvalidaSinTocarNada() throws Exception {
        // Firmado como DECLINED por 1 centavo, pero el atacante lo cambia a APPROVED
        String cuerpo = eventoFirmado("transaction.updated", "tx-001", "DECLINED", 1L, SECRETO)
                .replace("\"DECLINED\"", "\"APPROVED\"");

        assertEquals(WompiWebhookService.Resultado.FIRMA_INVALIDA, servicio.procesar(cuerpo, null));

        verifyNoInteractions(eventoRepository);
        verifyNoInteractions(pedidoRepository);
    }

    // ---------- Idempotencia ----------

    @Test
    void mismoEventoDosVecesSeAplicaUnaSolaVez() throws Exception {
        String cuerpo = aprobado("tx-001");

        assertEquals(WompiWebhookService.Resultado.PROCESADO, servicio.procesar(cuerpo, null));
        assertEquals(WompiWebhookService.Resultado.DUPLICADO, servicio.procesar(cuerpo, null));

        assertEquals("COMPLETADO", pedido.getPago().getEstado());
        assertEquals("tx-001", pedido.getPago().getTransaccionId());
        // El pedido se guardo una sola vez
        verify(pedidoRepository, times(1)).save(any(Pedido.class));
        // El evento quedo registrado como procesado, con _id = transaction.id:status
        EventoWompi registro = eventosGuardados.get("tx-001:APPROVED");
        assertTrue(registro.isProcesado());
        assertEquals(1, eventosGuardados.size());
    }

    @Test
    void eventoQueQuedoAMediasSeReprocesa() throws Exception {
        EventoWompi aMedias = new EventoWompi("tx-001", "APPROVED", REFERENCIA);
        aMedias.setProcesado(false);
        eventosGuardados.put(aMedias.getId(), aMedias);

        assertEquals(WompiWebhookService.Resultado.PROCESADO, servicio.procesar(aprobado("tx-001"), null));

        assertEquals("COMPLETADO", pedido.getPago().getEstado());
        assertTrue(eventosGuardados.get("tx-001:APPROVED").isProcesado());
    }

    @Test
    void pagoEnEstadoFinalNoSeDegrada() throws Exception {
        assertEquals(WompiWebhookService.Resultado.PROCESADO, servicio.procesar(aprobado("tx-001"), null));

        // Llega despues un evento DECLINED distinto (otro id:status): no debe degradar el pago
        String declinado = eventoFirmado("transaction.updated", "tx-002", "DECLINED", MONTO, SECRETO);
        assertEquals(WompiWebhookService.Resultado.IGNORADO, servicio.procesar(declinado, null));

        assertEquals("COMPLETADO", pedido.getPago().getEstado());
        assertEquals("tx-001", pedido.getPago().getTransaccionId());
        verify(pedidoRepository, times(1)).save(any(Pedido.class));
    }

    // ---------- Reglas de estado ----------

    @Test
    void declinedErrorYVoidedMarcanFallido() throws Exception {
        for (String estado : List.of("DECLINED", "ERROR", "VOIDED")) {
            pedido = nuevoPedido("PENDIENTE", MONTO);
            when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(pedido));

            String cuerpo = eventoFirmado("transaction.updated", "tx-" + estado, estado, MONTO, SECRETO);
            assertEquals(WompiWebhookService.Resultado.PROCESADO, servicio.procesar(cuerpo, null));
            assertEquals("FALLIDO", pedido.getPago().getEstado(), estado);
        }
    }

    @Test
    void pendingNoCambiaNada() throws Exception {
        String cuerpo = eventoFirmado("transaction.updated", "tx-001", "PENDING", MONTO, SECRETO);

        assertEquals(WompiWebhookService.Resultado.IGNORADO, servicio.procesar(cuerpo, null));

        assertEquals("PENDIENTE", pedido.getPago().getEstado());
        assertNull(pedido.getPago().getTransaccionId());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void montoDistintoAlFirmadoNoMarcaCompletado() throws Exception {
        String cuerpo = eventoFirmado("transaction.updated", "tx-001", "APPROVED", MONTO - 100, SECRETO);

        assertEquals(WompiWebhookService.Resultado.IGNORADO, servicio.procesar(cuerpo, null));

        assertEquals("PENDIENTE", pedido.getPago().getEstado());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    void pagoSinMontoFirmadoNoSeMarcaCompletado() throws Exception {
        pedido = nuevoPedido("PENDIENTE", null);
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(pedido));

        servicio.procesar(aprobado("tx-001"), null);

        assertEquals("PENDIENTE", pedido.getPago().getEstado());
    }

    // ---------- Eventos no soportados / JSON invalido / sin secreto ----------

    @Test
    void eventoNoSoportadoSeIgnoraSinTocarNada() throws Exception {
        String cuerpo = eventoFirmado("nequi_token.updated", "tx-001", "APPROVED", MONTO, SECRETO);

        assertEquals(WompiWebhookService.Resultado.IGNORADO, servicio.procesar(cuerpo, null));

        verifyNoInteractions(eventoRepository);
        verifyNoInteractions(pedidoRepository);
        assertEquals("PENDIENTE", pedido.getPago().getEstado());
    }

    @Test
    void jsonInvalidoDevuelveJsonInvalidoSinTocarNada() {
        assertEquals(WompiWebhookService.Resultado.JSON_INVALIDO, servicio.procesar("{esto no es json", null));
        assertEquals(WompiWebhookService.Resultado.JSON_INVALIDO, servicio.procesar("", null));
        assertEquals(WompiWebhookService.Resultado.JSON_INVALIDO, servicio.procesar(null, null));
        assertEquals(WompiWebhookService.Resultado.JSON_INVALIDO, servicio.procesar("[1,2,3]", null));

        verifyNoInteractions(eventoRepository);
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void sinSecretoConfiguradoNuncaProcesa() throws Exception {
        WompiWebhookService sinSecreto = new WompiWebhookService(
                new ObjectMapper(),
                new WompiWebhookVerifier(""),
                eventoRepository,
                new PagoConfirmacionService(pedidoRepository));

        assertEquals(WompiWebhookService.Resultado.SECRETO_NO_CONFIGURADO,
                sinSecreto.procesar(aprobado("tx-001"), null));

        verifyNoInteractions(eventoRepository);
        verifyNoInteractions(pedidoRepository);
        assertFalse(eventosGuardados.containsKey("tx-001:APPROVED"));
    }
}
