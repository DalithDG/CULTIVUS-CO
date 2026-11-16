package com.example.demo.services;

import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.embebidos.CategoriaProducto;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import com.example.demo.repository.ProductoRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceCatalogoTest {

    @Mock
    private ProductoCatalogoRepository catalogoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AdminService adminService;

    private List<ProductoCatalogo> catalogoMock;

    @BeforeEach
    void setUp() {
        catalogoMock = new ArrayList<>();

        ProductoCatalogo p1 = new ProductoCatalogo();
        p1.setId("cat-1");
        p1.setNombre("Papa Criolla");
        p1.setCategoria(new CategoriaProducto("cat_tuberculos", "Tubérculos"));
        p1.setActivo(true);
        p1.setAprobado(true);
        catalogoMock.add(p1);

        ProductoCatalogo p2 = new ProductoCatalogo();
        p2.setId("cat-2");
        p2.setNombre("Aguacate Hass");
        p2.setCategoria(new CategoriaProducto("cat_frutas", "Frutas"));
        p2.setActivo(true);
        p2.setAprobado(true);
        catalogoMock.add(p2);
    }

    @Test
    @DisplayName("1. Siempre consulta catalogoRepository incluso si productoRepository tiene miles de registros")
    void testObtenerProductosPaginadosIgnoraColeccionLegacy() {
        // Simular que productoRepository tiene 15.000 registros
        lenient().when(productoRepository.count()).thenReturn(15000L);

        Page<ProductoCatalogo> pageMock = new PageImpl<>(catalogoMock);
        when(catalogoRepository.findAll(any(Pageable.class))).thenReturn(pageMock);

        Page<ProductoCatalogo> result = adminService.obtenerProductosPaginados(0, 50, null, null);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals("Papa Criolla", result.getContent().get(0).getNombre());

        // Verificar que NUNCA se consultó la colección legacy
        verify(productoRepository, never()).findAll(any(Pageable.class));
        verify(catalogoRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("2. Aplica búsqueda por texto y filtro por categoría sobre ProductoCatalogo")
    void testFiltrosBusquedaYCategoria() {
        Page<ProductoCatalogo> pageMock = new PageImpl<>(List.of(catalogoMock.get(0)));

        when(catalogoRepository.findBySearchAndCategoria(eq("papa"), eq("cat_tuberculos"), any(Pageable.class)))
                .thenReturn(pageMock);

        Page<ProductoCatalogo> result = adminService.obtenerProductosPaginados(0, 50, "papa", "cat_tuberculos");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(catalogoRepository).findBySearchAndCategoria(eq("papa"), eq("cat_tuberculos"), any(Pageable.class));
    }

    @Test
    @DisplayName("3. Agrega vendedores, precio mínimo y stock sumado mediante una sola consulta de agregación")
    @SuppressWarnings("unchecked")
    void testEnriquecerConMetricasDeOfertas() {
        Page<ProductoCatalogo> pageMock = new PageImpl<>(catalogoMock);
        when(catalogoRepository.findAll(any(Pageable.class))).thenReturn(pageMock);

        // Simular resultado de agregación en MongoDB para cat-1
        Document docCat1 = new Document("_id", "cat-1")
                .append("totalVendedores", 5)
                .append("precioMinimo", 4500.0)
                .append("stockTotal", 120.0);

        AggregationResults<Document> aggResults = new AggregationResults<>(List.of(docCat1), new Document());

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("ofertas"), eq(Document.class)))
                .thenReturn(aggResults);

        Page<ProductoCatalogo> result = adminService.obtenerProductosPaginados(0, 50, null, null);

        ProductoCatalogo papa = result.getContent().get(0);
        assertEquals(5, papa.getTotalVendedores());
        assertEquals(4500.0, papa.getPrecioMinimo());
        assertEquals(120.0, papa.getStockTotal());
        assertEquals("5 vendedores", papa.getVendedor().getNombre());

        // cat-2 no tiene ofertas: debe quedar con 0 vendedores y stock 0
        ProductoCatalogo aguacate = result.getContent().get(1);
        assertEquals(0, aguacate.getTotalVendedores());
        assertNull(aguacate.getPrecioMinimo());
        assertEquals(0.0, aguacate.getStockTotal());

        // Se verifica que la agregación se ejecutó exactamente 1 vez para toda la página
        verify(mongoTemplate, times(1)).aggregate(any(Aggregation.class), eq("ofertas"), eq(Document.class));
    }

    @Test
    @DisplayName("4. La agregación solo cuenta ofertas disponibles, publicadas y con precio mayor a cero")
    @SuppressWarnings("unchecked")
    void testAgregacionSoloCuentaOfertasPublicadas() {
        when(catalogoRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(catalogoMock));

        AggregationResults<Document> vacio = new AggregationResults<>(List.of(), new Document());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("ofertas"), eq(Document.class)))
                .thenReturn(vacio);

        org.mockito.ArgumentCaptor<Aggregation> captor =
                org.mockito.ArgumentCaptor.forClass(Aggregation.class);

        adminService.obtenerProductosPaginados(0, 50, null, null);

        verify(mongoTemplate).aggregate(captor.capture(), eq("ofertas"), eq(Document.class));
        List<Document> pipeline = captor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT);
        String match = pipeline.get(0).toJson();
        assertTrue(match.contains("estado_publicacion"), "Debe filtrar por estado de publicación");
        assertTrue(match.contains("PUBLICADO"), "Debe contar solo ofertas PUBLICADO");
        assertTrue(match.contains("disponible"), "Debe contar solo ofertas disponibles");
        assertTrue(match.contains("precio"), "Debe ignorar ofertas sin precio válido");
        assertTrue(pipeline.get(1).toJson().contains("producto_catalogo_id"),
                "Debe agrupar por producto_catalogo_id");
    }

    @Test
    @DisplayName("5. La búsqueda del admin se escapa para no romper el regex de MongoDB")
    void testBusquedaEscapaMetacaracteres() {
        when(catalogoRepository.findByNombreContainingIgnoreCaseOrDescripcionContainingIgnoreCase(
                anyString(), anyString(), any(Pageable.class)))
                .thenReturn(Page.empty());

        adminService.obtenerProductosPaginados(0, 50, "(papa", null);

        verify(catalogoRepository).findByNombreContainingIgnoreCaseOrDescripcionContainingIgnoreCase(
                eq("\\(papa"), eq("\\(papa"), any(Pageable.class));
    }
}
