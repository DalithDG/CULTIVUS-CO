package com.example.demo.services;

import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.repository.OfertaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusquedaServiceTest {

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private CatalogoService catalogoService;

    @InjectMocks
    private BusquedaService busquedaService;

    private OfertaVendedor ofertaPublicada;
    private OfertaVendedor ofertaNoDisponible;
    private OfertaVendedor ofertaConAcento;
    private OfertaVendedor ofertaPendiente;

    @BeforeEach
    void setUp() {
        ofertaPublicada = new OfertaVendedor();
        ofertaPublicada.setId("of-1");
        ofertaPublicada.setNombreProducto("Tomate Cherry");
        ofertaPublicada.setDisponible(true);
        ofertaPublicada.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        ofertaPublicada.setPrecio(3000.0);
        ofertaPublicada.setCategoriaId("cat-2");
        ofertaPublicada.setCategoriaNombre("Verduras");

        ofertaNoDisponible = new OfertaVendedor();
        ofertaNoDisponible.setId("of-2");
        ofertaNoDisponible.setNombreProducto("Yuca");
        ofertaNoDisponible.setDisponible(false);
        ofertaNoDisponible.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        ofertaNoDisponible.setPrecio(2000.0);
        ofertaNoDisponible.setCategoriaId("cat-2");
        ofertaNoDisponible.setCategoriaNombre("Verduras");

        ofertaConAcento = new OfertaVendedor();
        ofertaConAcento.setId("of-3");
        ofertaConAcento.setNombreProducto("Plátano Hartón");
        ofertaConAcento.setDisponible(true);
        ofertaConAcento.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        ofertaConAcento.setPrecio(1500.0);
        ofertaConAcento.setCategoriaId("cat-1");
        ofertaConAcento.setCategoriaNombre("Frutas");

        ofertaPendiente = new OfertaVendedor();
        ofertaPendiente.setId("of-4");
        ofertaPendiente.setNombreProducto("Tomate de Árbol");
        ofertaPendiente.setDisponible(true);
        ofertaPendiente.setEstadoPublicacion(EstadoPublicacion.PENDIENTE);
        ofertaPendiente.setPrecio(2800.0);
        ofertaPendiente.setCategoriaId("cat-1");
        ofertaPendiente.setCategoriaNombre("Frutas");
    }

    @Test
    @DisplayName("buscarProductos solo retorna ofertas disponibles, PUBLICADAS y con precio")
    void buscarProductosSoloRetornaPublicadasDisponibles() {
        when(ofertaRepository.findAll())
                .thenReturn(List.of(ofertaPublicada, ofertaNoDisponible, ofertaConAcento, ofertaPendiente));

        List<OfertaVendedor> resVacio = busquedaService.buscarProductos("");
        assertEquals(2, resVacio.size());
        assertFalse(resVacio.contains(ofertaNoDisponible));
        assertFalse(resVacio.contains(ofertaPendiente));

        List<OfertaVendedor> resTomate = busquedaService.buscarProductos("Tomate");
        assertEquals(1, resTomate.size());
        assertEquals("of-1", resTomate.get(0).getId());
    }

    @Test
    @DisplayName("buscarProductos soporta búsquedas sin tilde (platano encuentra Plátano)")
    void buscarProductosSoportaSinTilde() {
        when(ofertaRepository.findAll())
                .thenReturn(List.of(ofertaPublicada, ofertaNoDisponible, ofertaConAcento));

        List<OfertaVendedor> res = busquedaService.buscarProductos("platano");
        assertEquals(1, res.size());
        assertEquals("of-3", res.get(0).getId());
    }

    @Test
    @DisplayName("buscarPorCategoria solo retorna ofertas públicas de esa categoría")
    void buscarPorCategoriaSoloRetornaPublicas() {
        when(ofertaRepository.findAll())
                .thenReturn(List.of(ofertaPublicada, ofertaNoDisponible, ofertaConAcento));

        List<OfertaVendedor> res = busquedaService.buscarPorCategoria("cat-2");
        assertEquals(1, res.size());
        assertEquals("of-1", res.get(0).getId());
    }

    @Test
    @DisplayName("busquedaAvanzada filtra por categoría y precio sobre ofertas públicas")
    void busquedaAvanzadaFiltros() {
        when(ofertaRepository.findAll())
                .thenReturn(List.of(ofertaPublicada, ofertaNoDisponible, ofertaConAcento));

        List<OfertaVendedor> res = busquedaService.busquedaAvanzada("tomate", "cat-2", 2000.0, 4000.0);
        assertEquals(1, res.size());
        assertEquals("of-1", res.get(0).getId());

        List<OfertaVendedor> resFueraPrecio = busquedaService.busquedaAvanzada("tomate", "cat-2", 3500.0, 5000.0);
        assertTrue(resFueraPrecio.isEmpty());
    }
}
