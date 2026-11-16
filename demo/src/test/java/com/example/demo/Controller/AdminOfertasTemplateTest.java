package com.example.demo.Controller;

import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.embebidos.DatosVendedor;
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

public class AdminOfertasTemplateTest {

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
    @DisplayName("Renderiza ofertas con estados PUBLICADO, PENDIENTE y sin vendedor")
    void testRenderizarOfertasVariadas() {
        List<OfertaVendedor> lista = new ArrayList<>();

        // 1. Oferta PUBLICADO con todos los datos
        OfertaVendedor of1 = new OfertaVendedor();
        of1.setId("of-1");
        of1.setNombreProducto("Papa R-12");
        DatosVendedor vend1 = new DatosVendedor();
        vend1.setRazonSocial("AgroBoyacá");
        of1.setVendedor(vend1);
        of1.setImagenUrl("https://ejemplo.com/papa.jpg");
        of1.setPrecio(15000.0);
        of1.setStock(50.0);
        of1.setCompraMinima(2.0);
        of1.setDisponible(true);
        of1.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        lista.add(of1);

        // 2. Oferta PENDIENTE con vendedor por nombre
        OfertaVendedor of2 = new OfertaVendedor();
        of2.setId("of-2");
        of2.setNombreProducto("Zanahoria");
        DatosVendedor vend2 = new DatosVendedor();
        vend2.setNombre("Pedro Pérez");
        of2.setVendedor(vend2);
        of2.setImagenUrl("https://ejemplo.com/zanahoria.jpg");
        of2.setPrecio(8000.0);
        of2.setStock(20.0);
        of2.setCompraMinima(1.0);
        of2.setDisponible(false);
        of2.setEstadoPublicacion(EstadoPublicacion.PENDIENTE);
        lista.add(of2);

        // 3. Oferta sin vendedor y con campos nulos (tolerancia a datos manuales de Mongo)
        OfertaVendedor of3 = new OfertaVendedor();
        of3.setId("of-3");
        of3.setNombreProducto("Cebolla Cabezona");
        of3.setVendedor(null);
        of3.setImagenUrl(null);
        of3.setPrecio(null);
        of3.setStock(null);
        of3.setCompraMinima(null);
        of3.setDisponible(true);
        of3.setEstadoPublicacion(EstadoPublicacion.RECHAZADO);
        lista.add(of3);

        Page<OfertaVendedor> pagina = new PageImpl<>(lista, PageRequest.of(0, 50), 3);

        Context context = new Context();
        context.setVariable("ofertas", pagina);
        context.setVariable("activePage", "ofertas");
        context.setVariable("session", sesionAdmin());

        String html = templateEngine.process("admin/ofertas", context);
        assertNotNull(html);

        // Comprobar que se pintan todas las filas (3 productos)
        assertTrue(html.contains("Papa R-12"), "Debe contener Papa R-12");
        assertTrue(html.contains("Zanahoria"), "Debe contener Zanahoria");
        assertTrue(html.contains("Cebolla Cabezona"), "Debe contener Cebolla Cabezona");

        // Comprobar vendedor
        assertTrue(html.contains("AgroBoyacá"), "Debe mostrar la razón social del vendedor");
        assertTrue(html.contains("Pedro Pérez"), "Debe mostrar el nombre del vendedor cuando no hay razón social");
        assertTrue(html.contains("Sin vendedor"), "Debe tolerar ofertas con vendedor null y mostrar 'Sin vendedor'");

        // Comprobar badges de estado y estilos
        // 1. PUBLICADO -> badge-active
        assertTrue(html.contains("badge-active"), "El badge PUBLICADO debe tener badge-active");
        assertTrue(html.contains("PUBLICADO"), "Debe mostrar el texto PUBLICADO");

        // 2. PENDIENTE -> badge--warning
        assertTrue(html.contains("badge--warning"), "El badge PENDIENTE debe tener badge--warning");
        assertTrue(html.contains("PENDIENTE"), "Debe mostrar el texto PENDIENTE");

        // 3. RECHAZADO -> badge-inactive
        assertTrue(html.contains("badge-inactive"), "El badge RECHAZADO debe tener badge-inactive");
        assertTrue(html.contains("RECHAZADO"), "Debe mostrar el texto RECHAZADO");

        // Comprobar que no existe el valor viejo 'APROBADO'
        assertFalse(html.contains("APROBADO"), "No debe existir el valor 'APROBADO'");

        // Comprobar totalElements
        assertTrue(html.contains("Ofertas de Vendedores (<span class=\"adm-tabular\">3</span>)"),
                "Debe mostrar el conteo total de 3 ofertas");
    }

    @Test
    @DisplayName("Paginación con 279 elementos calcula 6 páginas y navegación correcta")
    void testPaginacion279Elementos() {
        List<OfertaVendedor> lista = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            OfertaVendedor of = new OfertaVendedor();
            of.setId("of-" + i);
            of.setNombreProducto("Producto " + i);
            of.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
            lista.add(of);
        }

        // Página 0 de 6 (279 elementos en total, 50 por página)
        Page<OfertaVendedor> pagina0 = new PageImpl<>(lista, PageRequest.of(0, 50), 279);

        Context ctx0 = new Context();
        ctx0.setVariable("ofertas", pagina0);
        ctx0.setVariable("activePage", "ofertas");
        ctx0.setVariable("session", sesionAdmin());

        String html0 = templateEngine.process("admin/ofertas", ctx0);

        assertTrue(html0.contains("279"), "Debe mostrar 279 ofertas en total");
        assertTrue(html0.contains("Mostrando página <strong>1</strong> de <strong>6</strong>"),
                "Debe indicar página 1 de 6");
        assertTrue(html0.contains("page=1"), "Enlace Siguiente debe apuntar a la página 1");
        assertFalse(html0.contains("page=-1"), "No debe haber enlace Anterior en la primera página");

        // Página 1 de 6
        Page<OfertaVendedor> pagina1 = new PageImpl<>(lista, PageRequest.of(1, 50), 279);
        Context ctx1 = new Context();
        ctx1.setVariable("ofertas", pagina1);
        ctx1.setVariable("activePage", "ofertas");
        ctx1.setVariable("session", sesionAdmin());

        String html1 = templateEngine.process("admin/ofertas", ctx1);
        assertTrue(html1.contains("Mostrando página <strong>2</strong> de <strong>6</strong>"));
        assertTrue(html1.contains("page=0"), "Enlace Anterior debe apuntar a la página 0");
        assertTrue(html1.contains("page=2"), "Enlace Siguiente debe apuntar a la página 2");
    }

    /**
     * El sidebar del admin (versión actual) lee session.usuarioLogueado,
     * por eso los tests de plantilla necesitan una sesión simulada.
     */
    private java.util.Map<String, Object> sesionAdmin() {
        com.example.demo.Model.Usuario admin = new com.example.demo.Model.Usuario();
        admin.setNombre("Admin Prueba");
        java.util.Map<String, Object> sesion = new java.util.HashMap<>();
        sesion.put("usuarioLogueado", admin);
        return sesion;
    }
}
