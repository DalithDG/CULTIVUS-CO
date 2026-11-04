package com.example.demo.services;

import com.example.demo.Model.Categoria;
import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.embebidos.UnidadMedida;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogoInicializadorTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ProductoCatalogoRepository catalogoRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @InjectMocks
    private CatalogoInicializador inicializador;

    @Test
    @DisplayName("asegurarCategorias crea solo las categorías que faltan sin fallar")
    void testAsegurarCategorias() {
        when(categoriaRepository.findByNombreIgnoreCase(anyString()))
                .thenAnswer(invocation -> {
                    String nombre = invocation.getArgument(0);
                    if ("Frutas".equalsIgnoreCase(nombre)) {
                        return Optional.of(new Categoria("Frutas", "Frutas frescas"));
                    }
                    return Optional.empty();
                });

        int creadas = inicializador.asegurarCategorias();

        // 11 base - 1 existente = 10 creadas
        assertEquals(10, creadas);
        verify(categoriaRepository, times(10)).save(any(Categoria.class));
    }

    @Test
    @DisplayName("purgarProductosSinteticos elimina clones numéricos sin ofertas y conserva los con ofertas")
    void testPurgarProductosSinteticos() {
        ProductoCatalogo clon1 = new ProductoCatalogo();
        clon1.setId("p-clon-1");
        clon1.setNombre("Cebolla Cabezona Familiar 1");

        ProductoCatalogo clon2 = new ProductoCatalogo();
        clon2.setId("p-clon-2");
        clon2.setNombre("Tomate Especial 8");

        ProductoCatalogo productoReal = new ProductoCatalogo();
        productoReal.setId("p-real");
        productoReal.setNombre("Tomate");

        when(catalogoRepository.findAll()).thenReturn(List.of(clon1, clon2, productoReal));
        // clon1 no tiene ofertas -> se borra
        when(ofertaRepository.countByProductoCatalogoId("p-clon-1")).thenReturn(0L);
        // clon2 sí tiene ofertas -> se conserva
        when(ofertaRepository.countByProductoCatalogoId("p-clon-2")).thenReturn(1L);

        int eliminados = inicializador.purgarProductosSinteticos();

        assertEquals(1, eliminados);
        verify(catalogoRepository).delete(clon1);
        verify(catalogoRepository, never()).delete(clon2);
        verify(catalogoRepository, never()).delete(productoReal);
    }

    @Test
    @DisplayName("migrarProductosExistentes no lanza NPE si unidadesPermitidas es null")
    void testMigrarProductosExistentesNullSafety() {
        ProductoCatalogo legacyKg = new ProductoCatalogo();
        legacyKg.setId("prod-legacy-1");
        legacyKg.setNombre("Yuca");
        legacyKg.setUnidadMedida("kg");
        legacyKg.setUnidadesPermitidas(null); // Documento de Mongo sin el campo

        when(catalogoRepository.findAll()).thenReturn(List.of(legacyKg));

        int migrados = inicializador.migrarProductosExistentes();

        assertEquals(1, migrados);
        assertNotNull(legacyKg.getUnidadPorDefecto());
        assertEquals("kg", legacyKg.getUnidadPorDefecto().getAbreviatura());
        assertNotNull(legacyKg.getUnidadesPermitidas());
        assertFalse(legacyKg.getUnidadesPermitidas().isEmpty());
        verify(catalogoRepository).save(legacyKg);
    }

    @Test
    @DisplayName("migrarOfertasExistentes asigna unidad del producto a la oferta")
    void testMigrarOfertasExistentes() {
        OfertaVendedor oferta = new OfertaVendedor();
        oferta.setId("of-1");
        oferta.setProductoCatalogoId("prod-1");
        oferta.setUnidadMedida(null);

        ProductoCatalogo producto = new ProductoCatalogo();
        producto.setId("prod-1");
        producto.setNombre("Plátano hartón");
        producto.setUnidadMedida("un");
        producto.definirUnidades(UnidadMedida.UN, List.of(UnidadMedida.UN, UnidadMedida.DOC));

        when(ofertaRepository.findAll()).thenReturn(List.of(oferta));
        when(catalogoRepository.findById("prod-1")).thenReturn(Optional.of(producto));

        int migradas = inicializador.migrarOfertasExistentes();

        assertEquals(1, migradas);
        assertNotNull(oferta.getUnidadMedida());
        assertEquals("un", oferta.getUnidadMedida().getAbreviatura());
        assertEquals(EstadoPublicacion.PUBLICADO, oferta.getEstadoPublicacion());
        verify(ofertaRepository).save(oferta);
    }

    @Test
    @DisplayName("recalcularMetricasProductos calcula precioMinimo, totalVendedores y stockTotal correctamente")
    void testRecalcularMetricasProductos() {
        ProductoCatalogo prodConOfertas = new ProductoCatalogo();
        prodConOfertas.setId("prod-1");
        prodConOfertas.setNombre("Tomate");

        ProductoCatalogo prodSinOfertas = new ProductoCatalogo();
        prodSinOfertas.setId("prod-2");
        prodSinOfertas.setNombre("Aguacate");
        prodSinOfertas.setTotalVendedores(5);
        prodSinOfertas.setPrecioMinimo(4000.0);
        prodSinOfertas.setStockTotal(10.0);

        OfertaVendedor o1 = new OfertaVendedor();
        o1.setId("of-1");
        o1.setProductoCatalogoId("prod-1");
        o1.setDisponible(true);
        o1.setPrecio(3000.0);
        o1.setStock(15.0);
        o1.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);

        OfertaVendedor o2 = new OfertaVendedor();
        o2.setId("of-2");
        o2.setProductoCatalogoId("prod-1");
        o2.setDisponible(true);
        o2.setPrecio(2500.0);
        o2.setStock(10.0);
        o2.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);

        when(catalogoRepository.findAll()).thenReturn(List.of(prodConOfertas, prodSinOfertas));
        when(ofertaRepository.findByProductoCatalogoIdAndDisponibleTrue("prod-1")).thenReturn(List.of(o1, o2));
        when(ofertaRepository.findByProductoCatalogoIdAndDisponibleTrue("prod-2")).thenReturn(List.of());

        int actualizados = inicializador.recalcularMetricasProductos();

        assertEquals(2, actualizados);
        assertEquals(2500.0, prodConOfertas.getPrecioMinimo());
        assertEquals(2, prodConOfertas.getTotalVendedores());
        assertEquals(25.0, prodConOfertas.getStockTotal());

        assertNull(prodSinOfertas.getPrecioMinimo());
        assertEquals(0, prodSinOfertas.getTotalVendedores());
        assertEquals(0.0, prodSinOfertas.getStockTotal());

        verify(catalogoRepository).save(prodConOfertas);
        verify(catalogoRepository).save(prodSinOfertas);
    }

    @Test
    @DisplayName("ejecutar corre todas las fases sin lanzar excepciones")
    void testEjecutarEndToEnd() {
        when(categoriaRepository.findByNombreIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(catalogoRepository.findAll()).thenReturn(List.of());
        when(ofertaRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> inicializador.ejecutar());
    }
}
