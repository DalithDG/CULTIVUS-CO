package com.example.demo.Controller;

import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.embebidos.CategoriaProducto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminProductosTemplateTest {

    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        org.thymeleaf.linkbuilder.StandardLinkBuilder linkBuilder = new org.thymeleaf.linkbuilder.StandardLinkBuilder() {
            @Override
            protected String computeContextPath(
                    final org.thymeleaf.context.IExpressionContext context,
                    final String base,
                    final java.util.Map<String, Object> parameters) {
                return "";
            }
        };
        templateEngine.setLinkBuilder(linkBuilder);
    }

    @Test
    @DisplayName("Renderiza tabla del catálogo con 57 productos base y métricas agregadas")
    void testRenderizarCatalogoConMetricas() {
        List<ProductoCatalogo> lista = new ArrayList<>();

        // 1. Producto con múltiples ofertas
        ProductoCatalogo p1 = new ProductoCatalogo();
        p1.setId("cat-1");
        p1.setNombre("Papa Criolla");
        p1.setDescripcion("Papa amarilla seleccionada");
        p1.setCategoria(new CategoriaProducto("cat_tub", "Tubérculos"));
        p1.setImagenUrl("https://ejemplo.com/papa.jpg");
        p1.setTotalVendedores(5);
        p1.setPrecioMinimo(4500.0);
        p1.setStockTotal(120.0);
        p1.setActivo(true);
        p1.setAprobado(true);
        lista.add(p1);

        // 2. Producto con 1 oferta
        ProductoCatalogo p2 = new ProductoCatalogo();
        p2.setId("cat-2");
        p2.setNombre("Aguacate Hass");
        p2.setDescripcion("Aguacate fresco de exportación");
        p2.setCategoria(new CategoriaProducto("cat_fru", "Frutas"));
        p2.setTotalVendedores(1);
        p2.setPrecioMinimo(8000.0);
        p2.setStockTotal(25.0);
        p2.setActivo(true);
        p2.setAprobado(true);
        lista.add(p2);

        // 3. Producto sin ofertas aún
        ProductoCatalogo p3 = new ProductoCatalogo();
        p3.setId("cat-3");
        p3.setNombre("Yuca Llanera");
        p3.setCategoria(new CategoriaProducto("cat_tub", "Tubérculos"));
        p3.setTotalVendedores(0);
        p3.setPrecioMinimo(null);
        p3.setStockTotal(0.0);
        p3.setActivo(true);
        p3.setAprobado(false); // No disponible
        lista.add(p3);

        Page<ProductoCatalogo> pagina = new PageImpl<>(lista, PageRequest.of(0, 50), 57);

        Context context = new Context();
        context.setVariable("productos", pagina);
        context.setVariable("activePage", "catalogo");

        String html = templateEngine.process("admin/productos", context);
        assertNotNull(html);

        // 1. Título con total de 57 productos
        assertTrue(html.contains("Catálogo de Productos (<span class=\"adm-tabular\">57</span>)"),
                "Debe mostrar exactamente 57 productos en el título");

        // 2. Nombres y descripciones
        assertTrue(html.contains("Papa Criolla"));
        assertTrue(html.contains("Papa amarilla seleccionada"));
        assertTrue(html.contains("Aguacate Hass"));
        assertTrue(html.contains("Yuca Llanera"));

        // 3. Conteo de vendedores
        assertTrue(html.contains("5 vendedores"), "Debe indicar 5 vendedores para cat-1");
        assertTrue(html.contains("1 vendedor"), "Debe indicar 1 vendedor para cat-2");
        assertTrue(html.contains("Sin vendedores"), "Debe indicar Sin vendedores para cat-3");

        // 4. Precios y stocks
        assertTrue(html.contains("$4.500"), "Debe mostrar el precio mínimo formateado");
        assertTrue(html.contains("$8.000"), "Debe mostrar el precio de cat-2");
        assertTrue(html.contains("120"), "Debe mostrar el stock de cat-1");
        assertTrue(html.contains("25"), "Debe mostrar el stock de cat-2");

        // 5. Estados
        assertTrue(html.contains("Disponible"), "cat-1 y cat-2 deben estar disponibles");
        assertTrue(html.contains("No disponible"), "cat-3 no aprobado debe estar no disponible");

        // 6. Paginación con 57 elementos (2 páginas con tamaño 50)
        assertTrue(html.contains("Mostrando página <strong>1</strong> de <strong>2</strong>"),
                "Debe indicar página 1 de 2");
        assertTrue(html.contains("page=1"), "Enlace siguiente debe apuntar a la página 1");
    }
}
