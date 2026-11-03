package com.example.demo.Controller;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.UnidadMedida;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.CalculoEnvioService;
import com.example.demo.services.CatalogoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestCartAndCatalogTest {

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private CalculoEnvioService calculoEnvioService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CatalogoService catalogoService;

    @Mock
    private com.example.demo.services.VendedorService vendedorService;

    @Mock
    private com.example.demo.services.AppConfigService configService;

    @Mock
    private com.example.demo.services.WompiService wompiService;

    @InjectMocks
    private CarritoController carritoController;

    @InjectMocks
    private PagoController pagoController;

    @InjectMocks
    private ProductoController productoController;

    private OfertaVendedor ofertaTest;

    @BeforeEach
    void setUp() {
        ofertaTest = new OfertaVendedor();
        ofertaTest.setId("of-1");
        ofertaTest.setNombreProducto("Tomate Chonto");
        ofertaTest.setProductoCatalogoId("prod-1");
        ofertaTest.setPrecio(3500.0);
        ofertaTest.setStock(20.0);
        ofertaTest.setCompraMinima(1.0);
        ofertaTest.setDisponible(true);
        ofertaTest.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        ofertaTest.setUnidadMedida(UnidadMedida.deAbreviatura("kg"));
        ofertaTest.setVendedor(new DatosVendedor("vend-1", "Don Pepe", true));
    }

    @Test
    void visitanteAnonimoPuedeAgregarAlCarritoAjaxSinError200() {
        MockHttpSession sessionAnonima = new MockHttpSession();
        when(ofertaRepository.findById("of-1")).thenReturn(Optional.of(ofertaTest));

        ResponseEntity<Map<String, Object>> response = carritoController.agregarAlCarritoAjax("of-1", 2.0, sessionAnonima);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(1.0, (Double) response.getBody().get("totalItems"));

        // Se verifica que se guardó en la sesión
        Carrito carritoEnSesion = (Carrito) sessionAnonima.getAttribute("carritoAnonimo");
        assertNotNull(carritoEnSesion);
        assertEquals(1, carritoEnSesion.getItems().size());
        assertEquals(2.0, carritoEnSesion.getItems().get(0).getCantidad());
        assertEquals("kg", carritoEnSesion.getItems().get(0).getUnidadAbreviatura());
    }

    @Test
    void visitanteAnonimoPuedeVerCarrito() {
        MockHttpSession sessionAnonima = new MockHttpSession();
        when(calculoEnvioService.calcularTarifaCarrito(any(), anyString()))
                .thenReturn(new CalculoEnvioService.ResultadoCalculoEnvio(0L, Map.of(), Map.of(), 1, List.of()));

        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = carritoController.mostrarCarrito(sessionAnonima, model, ra);

        assertEquals("comprador/carrito", view);
        assertNull(model.get("usuario"));
        assertNotNull(model.get("carrito"));
    }

    @Test
    void visitanteAnonimoAlPagarEsRedirigidoARegistroComprador() {
        MockHttpSession sessionAnonima = new MockHttpSession();
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = pagoController.mostrarPago(sessionAnonima, model, ra);

        assertEquals("redirect:/usuario/registro?tipo=comprador&redirect=/pago", view);
        assertTrue(ra.getFlashAttributes().containsKey("mensaje"));
    }

    @Test
    void carritoAnonimoSeFusionaCuandoUsuarioSeIdentifica() {
        MockHttpSession session = new MockHttpSession();
        Carrito anonimo = new Carrito("anonimo_123");
        anonimo.getItems().add(new com.example.demo.Model.embebidos.ProductoCarrito(
                "prod-1", "of-1", "vend-1", "Don Pepe", "Tomate Chonto", "/img.jpg", 3500.0, 3.0, "kg", 1.0));
        anonimo.recalcularTotal();
        session.setAttribute("carritoAnonimo", anonimo);

        Usuario comprador = new Usuario();
        comprador.setId("comp-1");
        comprador.setRoles(java.util.Set.of(com.example.demo.Model.Role.COMPRADOR));
        session.setAttribute("usuarioLogueado", comprador);

        Carrito carritoUsuario = new Carrito("comp-1");
        when(carritoRepository.findByUsuarioId("comp-1")).thenReturn(Optional.of(carritoUsuario));
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(inv -> inv.getArgument(0));

        pagoController.mostrarPago(session, new ExtendedModelMap(), new RedirectAttributesModelMap());

        // El carrito anónimo debe haberse fusionado en la BD y eliminado de la sesión
        assertNull(session.getAttribute("carritoAnonimo"));
        assertEquals(1, carritoUsuario.getItems().size());
        assertEquals(3.0, carritoUsuario.getItems().get(0).getCantidad());
    }

    @Test
    void productosPorCategoriaDeduplicaNombresRepetidos() {
        MockHttpSession sessionVendedor = new MockHttpSession();
        Usuario vend = new Usuario();
        vend.setId("v1");
        vend.setRoles(java.util.Set.of(com.example.demo.Model.Role.VENDEDOR));
        sessionVendedor.setAttribute("usuarioLogueado", vend);

        ProductoCatalogo p1 = new ProductoCatalogo();
        p1.setId("p1");
        p1.setNombre("Cebolla Cabezona");
        p1.setNombreNormalizado("cebolla cabezona");
        p1.setActivo(true);
        p1.setAprobado(true);
        p1.definirUnidades(UnidadMedida.deAbreviatura("kg"), List.of(UnidadMedida.deAbreviatura("kg"), UnidadMedida.deAbreviatura("lb")));

        ProductoCatalogo p2 = new ProductoCatalogo();
        p2.setId("p2");
        p2.setNombre("Cebolla Cabezona"); // Mismo nombre normalizado (duplicado)
        p2.setNombreNormalizado("cebolla cabezona");
        p2.setActivo(true);
        p2.setAprobado(true);
        p2.definirUnidades(UnidadMedida.deAbreviatura("kg"), List.of(UnidadMedida.deAbreviatura("kg")));

        when(vendedorService.esVendedor("v1")).thenReturn(true);
        when(catalogoService.listarPorCategoria("cat-1")).thenReturn(List.of(p1, p2));

        ResponseEntity<?> response = productoController.productosPorCategoria("cat-1", sessionVendedor);
        assertEquals(200, response.getStatusCode().value());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) response.getBody();
        assertNotNull(lista);
        // Debe haberse deduplicado a 1 solo elemento
        assertEquals(1, lista.size());
        assertEquals("Cebolla Cabezona", lista.get(0).get("nombre"));
    }
}
