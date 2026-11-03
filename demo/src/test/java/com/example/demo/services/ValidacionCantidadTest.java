package com.example.demo.services;

import com.example.demo.Model.embebidos.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-06: Pruebas unitarias de ValidacionCantidad")
class ValidacionCantidadTest {

    private final UnidadMedida kg = UnidadMedida.deAbreviatura("kg");
    private final UnidadMedida g = UnidadMedida.deAbreviatura("g");
    private final UnidadMedida lb = UnidadMedida.deAbreviatura("lb");
    private final UnidadMedida litros = UnidadMedida.deAbreviatura("L");
    private final UnidadMedida ml = UnidadMedida.deAbreviatura("ml");

    private final UnidadMedida un = UnidadMedida.deAbreviatura("un");
    private final UnidadMedida doc = UnidadMedida.deAbreviatura("doc");
    private final UnidadMedida manojo = UnidadMedida.deAbreviatura("manojo");
    private final UnidadMedida bandeja = UnidadMedida.deAbreviatura("bandeja");

    // ── AC1: Unidades enteras no admiten fracciones y explican el motivo ──

    @Test
    @DisplayName("AC1: Docena rechaza fracciones con mensaje explicativo claro")
    void docenaRechazaFracciones() {
        String error = ValidacionCantidad.validarCantidad(doc, 1.5, 1.0, 10.0);
        assertNotNull(error);
        assertEquals("Las docenas se venden completas; ingresa un número entero.", error);
    }

    @Test
    @DisplayName("AC1: Manojo rechaza fracciones con mensaje explicativo claro")
    void manojoRechazaFracciones() {
        String error = ValidacionCantidad.validarCantidad(manojo, 0.5, 1.0, 10.0);
        assertNotNull(error);
        assertEquals("Los manojos se venden completos; ingresa un número entero.", error);
    }

    @Test
    @DisplayName("AC1: Bandeja rechaza fracciones con mensaje explicativo claro")
    void bandejaRechazaFracciones() {
        String error = ValidacionCantidad.validarCantidad(bandeja, 2.25, 1.0, 10.0);
        assertNotNull(error);
        assertEquals("Las bandejas se venden completas; ingresa un número entero.", error);
    }

    @Test
    @DisplayName("AC1: Unidad (un) rechaza fracciones con mensaje explicativo claro")
    void unidadRechazaFracciones() {
        String error = ValidacionCantidad.validarCantidad(un, 3.14, 1.0, 10.0);
        assertNotNull(error);
        assertEquals("Las unidades se venden completas; ingresa un número entero.", error);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 2.0, 5.0, 100.0})
    @DisplayName("AC1: Unidades enteras aceptan números enteros")
    void unidadesEnterasAceptanEnteros(double cantidad) {
        assertNull(ValidacionCantidad.validarCantidad(doc, cantidad, 1.0, 200.0));
        assertNull(ValidacionCantidad.validarCantidad(manojo, cantidad, 1.0, 200.0));
        assertNull(ValidacionCantidad.validarCantidad(bandeja, cantidad, 1.0, 200.0));
        assertNull(ValidacionCantidad.validarCantidad(un, cantidad, 1.0, 200.0));
    }

    // ── AC2: Peso y Volumen admiten decimales (máximo 2) ──

    @Test
    @DisplayName("AC2: kg, g, lb, L y ml aceptan decimales")
    void unidadesPesoYVolumenAceptanDecimales() {
        assertNull(ValidacionCantidad.validarCantidad(kg, 1.5, 0.5, 10.0));
        assertNull(ValidacionCantidad.validarCantidad(kg, 0.25, 0.1, 5.0));
        assertNull(ValidacionCantidad.validarCantidad(g, 250.5, 50.0, 1000.0));
        assertNull(ValidacionCantidad.validarCantidad(lb, 2.75, 1.0, 20.0));
        assertNull(ValidacionCantidad.validarCantidad(litros, 1.5, 0.5, 50.0));
        assertNull(ValidacionCantidad.validarCantidad(ml, 250.75, 100.0, 1000.0));
    }

    @Test
    @DisplayName("AC2: Peso y Volumen también aceptan enteros")
    void unidadesPesoYVolumenAceptanEnteros() {
        assertNull(ValidacionCantidad.validarCantidad(kg, 2.0, 1.0, 10.0));
        assertNull(ValidacionCantidad.validarCantidad(litros, 5.0, 1.0, 10.0));
    }

    @Test
    @DisplayName("AC2: Más de 2 decimales son rechazados con aviso")
    void rechazaMasDeDosDecimales() {
        String error = ValidacionCantidad.validarCantidad(kg, 1.234, 0.5, 10.0);
        assertNotNull(error);
        assertTrue(error.contains("máximo 2 decimales"));
    }

    // ── Validación de Compra Mínima ──

    @Test
    @DisplayName("Cantidad menor a compra mínima es rechazada")
    void cantidadMenorACompraMinimaRechazada() {
        String error = ValidacionCantidad.validarCantidad(kg, 1.5, 2.0, 10.0);
        assertNotNull(error);
        assertEquals("La compra mínima es 2 kg.", error);

        String errorDoc = ValidacionCantidad.validarCantidad(doc, 1.0, 3.0, 10.0);
        assertNotNull(errorDoc);
        assertEquals("La compra mínima es 3 doc.", errorDoc);
    }

    @Test
    @DisplayName("Cantidad igual a compra mínima es válida")
    void cantidadIgualACompraMinimaValida() {
        assertNull(ValidacionCantidad.validarCantidad(kg, 2.5, 2.5, 10.0));
        assertNull(ValidacionCantidad.validarCantidad(un, 3.0, 3.0, 10.0));
    }

    // ── Validación de Stock ──

    @Test
    @DisplayName("Cantidad mayor a stock disponible es rechazada")
    void cantidadMayorAStockRechazada() {
        String error = ValidacionCantidad.validarCantidad(kg, 12.5, 1.0, 10.0);
        assertNotNull(error);
        assertEquals("Solo hay 10 kg disponibles.", error);
    }

    @Test
    @DisplayName("Cantidad igual a stock disponible es válida")
    void cantidadIgualAStockValida() {
        assertNull(ValidacionCantidad.validarCantidad(kg, 10.0, 1.0, 10.0));
        assertNull(ValidacionCantidad.validarCantidad(doc, 5.0, 1.0, 5.0));
    }

    // ── Cantidad 0 o negativa ──

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, -0.25, -100.0})
    @DisplayName("Cantidad 0 o negativa siempre es rechazada")
    void cantidadCeroONegativaRechazada(double cantidad) {
        String error = ValidacionCantidad.validarCantidad(kg, cantidad, 0.5, 10.0);
        assertNotNull(error);
        assertEquals("La cantidad debe ser mayor a 0.", error);
    }

    @Test
    @DisplayName("Cantidad null, NaN o infinita es rechazada")
    void cantidadInvalidaRechazada() {
        assertEquals("La cantidad debe ser mayor a 0.", ValidacionCantidad.validarCantidad(kg, null, 1.0, 10.0));
        assertEquals("La cantidad debe ser mayor a 0.", ValidacionCantidad.validarCantidad(kg, Double.NaN, 1.0, 10.0));
        assertEquals("La cantidad debe ser mayor a 0.", ValidacionCantidad.validarCantidad(kg, Double.POSITIVE_INFINITY, 1.0, 10.0));
    }

    // ── Opción 'Otra' (Unidad personalizada) ──

    @Test
    @DisplayName("Otra unidad por defecto es tipo Unidad (solo enteros)")
    void otraUnidadPorDefectoEsEntera() {
        UnidadMedida otra = UnidadMedida.personalizada("Canastilla", false);
        assertFalse(otra.permiteDecimales());
        assertNull(ValidacionCantidad.validarCantidad(otra, 2.0, 1.0, 10.0));
        String error = ValidacionCantidad.validarCantidad(otra, 2.5, 1.0, 10.0);
        assertNotNull(error);
        assertTrue(error.contains("entero"));
    }

    @Test
    @DisplayName("Otra unidad marcada como fraccionable admite decimales")
    void otraUnidadFraccionableAdmiteDecimales() {
        UnidadMedida otraFracc = UnidadMedida.personalizada("Atado", true);
        assertTrue(otraFracc.permiteDecimales());
        assertNull(ValidacionCantidad.validarCantidad(otraFracc, 1.75, 0.5, 10.0));
    }

    // ── Coherencia de unidad en OfertaService (publicar stock/mínimo) ──

    @Test
    @DisplayName("validarCoherenciaUnidad rechaza 2.5 docenas en stock")
    void coherenciaRechazaDecimalesEnEnteros() {
        String errStock = ValidacionCantidad.validarCoherenciaUnidad(doc, 2.5, "El stock");
        assertNotNull(errStock);
        assertTrue(errStock.contains("entero"));

        String errMin = ValidacionCantidad.validarCoherenciaUnidad(bandeja, 1.25, "La compra mínima");
        assertNotNull(errMin);
        assertTrue(errMin.contains("entero"));
    }

    @Test
    @DisplayName("validarCoherenciaUnidad acepta 2.5 kg en stock y compra mínima")
    void coherenciaAceptaDecimalesEnPeso() {
        assertNull(ValidacionCantidad.validarCoherenciaUnidad(kg, 2.5, "El stock"));
        assertNull(ValidacionCantidad.validarCoherenciaUnidad(kg, 0.5, "La compra mínima"));
    }

    @Test
    @DisplayName("exigirCantidad lanza IllegalArgumentException si hay error")
    void exigirCantidadLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
                ValidacionCantidad.exigirCantidad(doc, 1.5, 1.0, 10.0));
        assertDoesNotThrow(() ->
                ValidacionCantidad.exigirCantidad(kg, 1.5, 1.0, 10.0));
    }
}

