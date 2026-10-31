package com.example.demo.Controller;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.ProductoCarrito;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.CalculoEnvioService;
import com.example.demo.services.AppConfigService;
import com.example.demo.services.WompiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendedorBloqueoComprasTest {

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private ProductoCatalogoRepository catalogoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private WompiService wompiService;

    @Mock
    private AppConfigService configService;

    @Mock
    private CalculoEnvioService calculoEnvioService;

    @InjectMocks
    private CarritoController carritoController;

    @InjectMocks
    private PagoController pagoController;

    private Usuario vendedor;
    private Usuario comprador;
    private MockHttpSession sessionVendedor;
    private MockHttpSession sessionComprador;

    @BeforeEach
    void setUp() {
        vendedor = new Usuario();
        vendedor.setId("vend-1");
        vendedor.setNombre("Vendedor Don Pepe");
        vendedor.setRoles(Set.of(Role.COMPRADOR, Role.VENDEDOR));

        comprador = new Usuario();
        comprador.setId("comp-1");
        comprador.setNombre("Comprador Maria");
        comprador.setRoles(Set.of(Role.COMPRADOR));

        sessionVendedor = new MockHttpSession();
        sessionVendedor.setAttribute("usuarioLogueado", vendedor);

        sessionComprador = new MockHttpSession();
        sessionComprador.setAttribute("usuarioLogueado", comprador);
    }

    // ── CARRITO CONTROLLER TESTS ──────────────────────────────────────────

    @Test
    void vendedorNoPuedeVerCarrito() {
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        String view = carritoController.mostrarCarrito(sessionVendedor, new ExtendedModelMap(), ra);

        assertEquals("redirect:/vendedor/dashboard", view);
        assertEquals(Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR, ra.getFlashAttributes().get("error"));
    }

    @Test
    void vendedorNoPuedeAgregarAlCarrito() {
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        String view = carritoController.agregarAlCarrito("ofert-1", 2.0, sessionVendedor, ra);

        assertEquals("redirect:/vendedor/dashboard", view);
        assertEquals(Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR, ra.getFlashAttributes().get("error"));
    }

    @Test
    void vendedorNoPuedeAgregarAlCarritoAjax() {
        ResponseEntity<Map<String, Object>> resp = carritoController.agregarAlCarritoAjax("ofert-1", 1.0, sessionVendedor);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(false, resp.getBody().get("success"));
        assertEquals(Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR, resp.getBody().get("error"));
    }

    @Test
    void usuarioNoPuedeComprarPropiaOfertaAjax() {
        OfertaVendedor propia = new OfertaVendedor();
        propia.setId("ofert-propia");
        propia.setStock(10.0);
        DatosVendedor datosVendedor = new DatosVendedor();
        datosVendedor.setId(comprador.getId()); // mismo id
        propia.setVendedor(datosVendedor);

        when(ofertaRepository.findById("ofert-propia")).thenReturn(Optional.of(propia));

        ResponseEntity<Map<String, Object>> resp = carritoController.agregarAlCarritoAjax("ofert-propia", 1.0, sessionComprador);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(false, resp.getBody().get("success"));
        assertEquals("No puedes comprar tus propios productos.", resp.getBody().get("error"));
    }

    @Test
    void vendedorNoPuedeActualizarCantidad() {
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        String view = carritoController.actualizarCantidad("ofert-1", 3.0, sessionVendedor, ra);

        assertEquals("redirect:/vendedor/dashboard", view);
        assertEquals(Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR, ra.getFlashAttributes().get("error"));
    }

    @Test
    void vendedorNoPuedeEliminarDelCarrito() {
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        String view = carritoController.eliminarDelCarrito("ofert-1", sessionVendedor, ra);

        assertEquals("redirect:/vendedor/dashboard", view);
        assertEquals(Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR, ra.getFlashAttributes().get("error"));
    }

    // ── PAGO CONTROLLER TESTS ─────────────────────────────────────────────

    @Test
    void vendedorNoPuedeAccederAPago() {
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        String view = pagoController.mostrarPago(sessionVendedor, new ExtendedModelMap(), ra);

        assertEquals("redirect:/vendedor/dashboard", view);
        assertEquals(Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR, ra.getFlashAttributes().get("error"));
    }

    @Test
    void vendedorNoPuedeCalcularEnvio() {
        ResponseEntity<?> resp = pagoController.calcularEnvio("Cartagena", sessionVendedor);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertTrue(resp.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertEquals(false, body.get("ok"));
        assertEquals(Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR, body.get("error"));
    }

    @Test
    void vendedorNoPuedeIniciarPagoWompi() {
        ResponseEntity<?> resp = pagoController.iniciarPagoWompi(Map.of("ciudad", "Cartagena"), sessionVendedor);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertTrue(resp.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertEquals(false, body.get("ok"));
        assertEquals(Usuario.MENSAJE_VENDEDOR_NO_PUEDE_COMPRAR, body.get("error"));
    }

    @Test
    void compradorNoPuedePagarCarritoConProductosPropios() {
        Carrito carritoConPropio = new Carrito(comprador.getId());
        ProductoCarrito item = new ProductoCarrito();
        item.setNombre("Tomate");
        item.setVendedorId(comprador.getId()); // Mismo ID del comprador
        carritoConPropio.getItems().add(item);

        when(carritoRepository.findByUsuarioId(comprador.getId())).thenReturn(Optional.of(carritoConPropio));

        ResponseEntity<?> resp = pagoController.iniciarPagoWompi(Map.of("ciudad", "Cartagena"), sessionComprador);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertEquals(false, body.get("ok"));
        assertTrue(body.get("error").toString().contains("No puedes comprar tus propios productos"));
    }
}
