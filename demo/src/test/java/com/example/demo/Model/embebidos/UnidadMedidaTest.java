package com.example.demo.Model.embebidos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-06: Pruebas de UnidadMedida y mapeo de abreviaturas")
class UnidadMedidaTest {

    @Test
    @DisplayName("permiteDecimales() se deriva correctamente del tipo")
    void permiteDecimalesSeDerivaDelTipo() {
        assertTrue(UnidadMedida.deAbreviatura("kg").permiteDecimales());
        assertTrue(UnidadMedida.deAbreviatura("g").permiteDecimales());
        assertTrue(UnidadMedida.deAbreviatura("lb").permiteDecimales());
        assertTrue(UnidadMedida.deAbreviatura("L").permiteDecimales());
        assertTrue(UnidadMedida.deAbreviatura("ml").permiteDecimales());

        assertFalse(UnidadMedida.deAbreviatura("un").permiteDecimales());
        assertFalse(UnidadMedida.deAbreviatura("doc").permiteDecimales());
        assertFalse(UnidadMedida.deAbreviatura("manojo").permiteDecimales());
        assertFalse(UnidadMedida.deAbreviatura("bandeja").permiteDecimales());
    }

    @Test
    @DisplayName("Mapeo de abreviaturas y sinónimos devuelve la unidad correcta")
    void mapeoDeAbreviaturasYSinonimos() {
        assertEquals("kg", UnidadMedida.deAbreviatura("kg").getAbreviatura());
        assertEquals("kg", UnidadMedida.deAbreviatura("kilogramo").getAbreviatura());
        assertEquals("kg", UnidadMedida.deAbreviatura("KILOS").getAbreviatura());
        assertEquals("un", UnidadMedida.deAbreviatura("unid").getAbreviatura());
        assertEquals("un", UnidadMedida.deAbreviatura("unidad").getAbreviatura());
        assertEquals("doc", UnidadMedida.deAbreviatura("docena").getAbreviatura());
        assertEquals("manojo", UnidadMedida.deAbreviatura("manojos").getAbreviatura());
        assertEquals("L", UnidadMedida.deAbreviatura("litros").getAbreviatura());
    }

    @Test
    @DisplayName("Abreviatura desconocida o nula mapea a 'un'")
    void abreviaturaDesconocidaMapeaAUn() {
        assertEquals("un", UnidadMedida.deAbreviatura(null).getAbreviatura());
        assertEquals("un", UnidadMedida.deAbreviatura("").getAbreviatura());
        assertEquals("un", UnidadMedida.deAbreviatura("desconocida_xyz").getAbreviatura());
    }

    @Test
    @DisplayName("Creación de unidad personalizada con validación de nombre")
    void unidadPersonalizada() {
        UnidadMedida u = UnidadMedida.personalizada("Guacal", false);
        assertEquals("Guacal", u.getNombre());
        assertEquals("Guacal", u.getAbreviatura());
        assertEquals(UnidadMedida.TIPO_UNIDAD, u.getTipo());
        assertFalse(u.permiteDecimales());

        UnidadMedida uFracc = UnidadMedida.personalizada("Bolsa", true);
        assertEquals(UnidadMedida.TIPO_FRACCIONABLE, uFracc.getTipo());
        assertTrue(uFracc.permiteDecimales());

        assertThrows(IllegalArgumentException.class, () -> UnidadMedida.personalizada("", false));
        assertThrows(IllegalArgumentException.class, () -> UnidadMedida.personalizada("NombreDemasiadoLargoQueSuperaVeinteCaracteres", false));
    }
}

