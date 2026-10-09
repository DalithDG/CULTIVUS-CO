package com.example.demo.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas puras de WompiService: sin Mongo, sin Spring y sin internet.
 */
class WompiServiceTest {

    // Secreto FALSO, solo para pruebas
    private static final String SECRETO_INTEGRIDAD_PRUEBA = "test_integrity_secret_123";

    private WompiService wompiService;

    @BeforeEach
    void setUp() {
        wompiService = new WompiService();
        ReflectionTestUtils.setField(wompiService, "integritySecret", SECRETO_INTEGRIDAD_PRUEBA);
        ReflectionTestUtils.setField(wompiService, "currency", "COP");
        ReflectionTestUtils.setField(wompiService, "envioProvisionalPorDefecto", 10000.0);
    }

    // ---------- Conversión a centavos ----------

    @Test
    void convierteACentavosEnteros() {
        assertEquals(1_000_000L, wompiService.convertirACentavos(10000.0));
        assertEquals(0L, wompiService.convertirACentavos(0.0));
        assertEquals(1_999_999L, wompiService.convertirACentavos(19999.99));
    }

    @Test
    void convertirACentavosEvitaErroresDePuntoFlotante() {
        // 0.07 * 100 = 7.000000000000001 en double; debe redondear a 7
        assertEquals(7L, wompiService.convertirACentavos(0.07));
        // 1.15 * 100 = 114.99999999999999 en double; debe redondear a 115
        assertEquals(115L, wompiService.convertirACentavos(1.15));
    }

    // ---------- Total = productos + envío ----------

    @Test
    void totalCentavosSumaProductosYEnvio() {
        // 200.000 + 10.000 = 210.000 COP = 21.000.000 centavos
        assertEquals(21_000_000L, wompiService.calcularTotalCentavos(200000.0, 10000.0));
    }

    @Test
    void totalCentavosConEnvioCeroEsSoloProductos() {
        assertEquals(20_000_000L, wompiService.calcularTotalCentavos(200000.0, 0.0));
    }

    @Test
    void totalCentavosConDecimalesRedondeaBien() {
        assertEquals(1_234_567L, wompiService.calcularTotalCentavos(12000.0, 345.67));
    }

    @Test
    void envioProvisionalPorDefectoSeUsaSinConfigService() {
        assertEquals(10000.0, wompiService.obtenerCostoEnvioProvisional());
        long total = wompiService.calcularTotalCentavos(
                200000.0, wompiService.obtenerCostoEnvioProvisional());
        assertEquals(21_000_000L, total);
    }

    // ---------- Firma de integridad ----------

    /**
     * Vector fijo calculado FUERA de Java (PowerShell/.NET SHA256 y certutil -hashfile SHA256)
     * sobre la cadena:
     * CUL-1700000000000-A1B2C3 + 2350000 + COP + test_integrity_secret_123
     */
    private static final String VECTOR_FIRMA_INTEGRIDAD =
            "12359f497d8a4d0b44bffdd42f70edbf48dffc0653ad865f036fa0ddff663298";

    @Test
    void firmaDeIntegridadCoincideConVectorExterno() {
        String firma = wompiService.generarFirmaIntegridad("CUL-1700000000000-A1B2C3", 2_350_000L, "COP");
        assertEquals(VECTOR_FIRMA_INTEGRIDAD, firma);
    }

    @Test
    void firmaDeIntegridadCambiaSiCambiaElMonto() {
        String firma = wompiService.generarFirmaIntegridad("CUL-1700000000000-A1B2C3", 2_350_001L, "COP");
        assertNotEquals(VECTOR_FIRMA_INTEGRIDAD, firma);
    }

    @Test
    void firmaDeIntegridadSinSecretoLanzaExcepcion() {
        ReflectionTestUtils.setField(wompiService, "integritySecret", "  ");
        assertThrows(IllegalStateException.class,
                () -> wompiService.generarFirmaIntegridad("CUL-1", 100L, "COP"));
    }
}
