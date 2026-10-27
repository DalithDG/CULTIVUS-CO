package com.example.demo.services;

import com.example.demo.Model.*;
import com.example.demo.Model.embebidos.*;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para verificación de modalidad de entrega única: DOMICILIO exclusivo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModalidadEntregaBloque7Test {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private AppConfigService appConfigService;

    private PedidoEstadoService pedidoEstadoService;

    @BeforeEach
    void setUp() {
        pedidoEstadoService = new PedidoEstadoService(
                pedidoRepository,
                compraRepository,
                null,
                notificacionService,
                appConfigService
        );

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(compraRepository.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("7.1 Modalidad siempre normaliza a DOMICILIO y flags son coherentes")
    void testModalidadFijadaADomicilio() {
        assertEquals(ModalidadEntrega.DOMICILIO, ModalidadEntrega.desde("RECOGE_EN_TIENDA"));
        assertEquals(ModalidadEntrega.DOMICILIO, ModalidadEntrega.desde("TIENDA"));
        assertEquals(ModalidadEntrega.DOMICILIO, ModalidadEntrega.desde(null));

        Pedido pedido = new Pedido();
        pedido.setModalidadEntrega(ModalidadEntrega.DOMICILIO);
        assertTrue(pedido.isDomicilio());
        assertFalse(pedido.isRecogeEnTienda());

        Compra compra = new Compra();
        compra.setModalidadEntrega(ModalidadEntrega.DOMICILIO);
        assertEquals(ModalidadEntrega.DOMICILIO, compra.getModalidadEntrega());
        assertFalse(compra.isRecogeEnTienda());
    }

    @Test
    @DisplayName("7.2 Flujo en DOMICILIO: Vendedor solo avanza a EN_PROCESO y LISTO_PARA_RECOGER, no a ENTREGADO")
    void testFlujoEstadosDomicilioExclusivo() {
        Pedido pedido = new Pedido();
        pedido.setId("ped_dom_1");
        pedido.setModalidadEntrega(ModalidadEntrega.DOMICILIO);
        pedido.setEstadoEnum(EstadoPedido.CONFIRMADO);

        // Vendedor pasa a EN_PROCESO
        pedidoEstadoService.transicionar(pedido, EstadoPedido.EN_PROCESO, ActorTransicion.VENDEDOR, "Preparando");
        assertEquals(EstadoPedido.EN_PROCESO, pedido.getEstadoEnum());

        // Vendedor pasa a LISTO_PARA_RECOGER
        pedidoEstadoService.transicionar(pedido, EstadoPedido.LISTO_PARA_RECOGER, ActorTransicion.VENDEDOR, "Listo");
        assertEquals(EstadoPedido.LISTO_PARA_RECOGER, pedido.getEstadoEnum());
        assertNotNull(pedido.getFechaListoParaRecoger());

        // Vendedor intenta pasar a ENVIADO -> debe fallar (labor del transportista)
        assertThrows(IllegalStateException.class, () ->
                pedidoEstadoService.transicionar(pedido, EstadoPedido.ENVIADO, ActorTransicion.VENDEDOR, "Enviando"));

        // Vendedor intenta pasar a ENTREGADO -> debe fallar (labor del transportista)
        assertThrows(IllegalStateException.class, () ->
                pedidoEstadoService.transicionar(pedido, EstadoPedido.ENTREGADO, ActorTransicion.VENDEDOR, "Entregando"));
    }

    @Test
    @DisplayName("7.3 Toda compra confirmada crea Envio logístico para transportista")
    void testTodaCompraCreaEnvioLogistico() {
        com.example.demo.repository.EnvioRepository mockEnvioRepo = mock(com.example.demo.repository.EnvioRepository.class);
        PagoConfirmacionService pService = new PagoConfirmacionService(
                pedidoRepository, ofertaRepository, null, notificacionService, carritoRepository, compraRepository
        );
        pService.setEnvioRepository(mockEnvioRepo);

        Compra compra = new Compra(new DatosComprador("u1", "Juan"), "REF-DOM-1", 100_000L, 10_000L);
        compra.setId("c_dom");
        compra.setModalidadEntrega(ModalidadEntrega.DOMICILIO);

        Pedido p = new Pedido();
        p.setId("p_1");
        p.setCompraId("c_dom");
        p.setModalidadEntrega(ModalidadEntrega.DOMICILIO);
        p.setDireccionEntrega(new DireccionPedido("Calle 123", "Cartagena", "Bolívar"));

        pService.crearEnvioParaCompra(compra, List.of(p));

        // Debe registrar el envío logístico en el repositorio
        verify(mockEnvioRepo, times(1)).save(any(Envio.class));
    }

    @Test
    @DisplayName("7.4 Timeout de retiro en tienda retorna 0 al estar deshabilitado")
    void testTimeoutRetiroEnTiendaRetornaCero() {
        int notificados = pedidoEstadoService.verificarTimeoutsRetiroTienda();
        assertEquals(0, notificados);
    }
}
