package com.example.demo.services;

import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.embebidos.CategoriaProducto;
import com.example.demo.repository.ProductoCatalogoRepository;
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
    private ProductoCatalogoRepository catalogoRepository;

    @InjectMocks
    private BusquedaService busquedaService;

    private ProductoCatalogo pConVendedor;
    private ProductoCatalogo pSinVendedor;
    private ProductoCatalogo pConAcento;

    @BeforeEach
    void setUp() {
        CategoriaProducto cat1 = new CategoriaProducto("cat-1", "Frutas");
        CategoriaProducto cat2 = new CategoriaProducto("cat-2", "Verduras");

        pConVendedor = new ProductoCatalogo();
        pConVendedor.setId("p1");
        pConVendedor.setNombre("Tomate Cherry");
        pConVendedor.setTotalVendedores(2);
        pConVendedor.setPrecioMinimo(3000.0);
        pConVendedor.setCategoria(cat2);

        pSinVendedor = new ProductoCatalogo();
        pSinVendedor.setId("p2");
        pSinVendedor.setNombre("Yuca");
        pSinVendedor.setTotalVendedores(0);
        pSinVendedor.setPrecioMinimo(2000.0);
        pSinVendedor.setCategoria(cat2);

        pConAcento = new ProductoCatalogo();
        pConAcento.setId("p3");
        pConAcento.setNombre("Plátano Hartón");
        pConAcento.setTotalVendedores(1);
        pConAcento.setPrecioMinimo(1500.0);
        pConAcento.setCategoria(cat1);
    }

    @Test
    @DisplayName("buscarProductos solo retorna productos con totalVendedores > 0")
    void buscarProductosSoloRetornaConVendedores() {
        when(catalogoRepository.findByAprobadoTrueAndActivoTrue())
                .thenReturn(List.of(pConVendedor, pSinVendedor, pConAcento));

        List<ProductoCatalogo> resVacio = busquedaService.buscarProductos("");
        assertEquals(2, resVacio.size());
        assertFalse(resVacio.contains(pSinVendedor));

        List<ProductoCatalogo> resTomate = busquedaService.buscarProductos("Tomate");
        assertEquals(1, resTomate.size());
        assertEquals("p1", resTomate.get(0).getId());
    }

    @Test
    @DisplayName("buscarProductos soporta búsquedas sin tilde (platano encuentra Plátano)")
    void buscarProductosSoportaSinTilde() {
        when(catalogoRepository.findByAprobadoTrueAndActivoTrue())
                .thenReturn(List.of(pConVendedor, pSinVendedor, pConAcento));

        List<ProductoCatalogo> res = busquedaService.buscarProductos("platano");
        assertEquals(1, res.size());
        assertEquals("p3", res.get(0).getId());
    }

    @Test
    @DisplayName("buscarPorCategoria solo retorna productos con totalVendedores > 0")
    void buscarPorCategoriaSoloRetornaConVendedores() {
        when(catalogoRepository.findByCategoriaIdAndAprobadoTrueAndActivoTrue("cat-2"))
                .thenReturn(List.of(pConVendedor, pSinVendedor));

        List<ProductoCatalogo> res = busquedaService.buscarPorCategoria("cat-2");
        assertEquals(1, res.size());
        assertEquals("p1", res.get(0).getId());
    }

    @Test
    @DisplayName("busquedaAvanzada filtra por categoria y precio respetando totalVendedores > 0")
    void busquedaAvanzadaFiltros() {
        when(catalogoRepository.findByAprobadoTrueAndActivoTrue())
                .thenReturn(List.of(pConVendedor, pSinVendedor, pConAcento));

        List<ProductoCatalogo> res = busquedaService.busquedaAvanzada("tomate", "cat-2", 2000.0, 4000.0);
        assertEquals(1, res.size());
        assertEquals("p1", res.get(0).getId());

        List<ProductoCatalogo> resFueraPrecio = busquedaService.busquedaAvanzada("tomate", "cat-2", 3500.0, 5000.0);
        assertTrue(resFueraPrecio.isEmpty());
    }
}

