package com.example.demo.services;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.TarifaEnvio;
import com.example.demo.Model.Ubicacion;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.ZonaMunicipio;
import com.example.demo.Model.embebidos.ProductoCarrito;
import com.example.demo.Model.embebidos.UbicacionUsuario;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.TarifaEnvioRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.repository.ZonaMunicipioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalculoEnvioServiceTest {

    @Mock
    private ZonaMunicipioRepository zonaMunicipioRepository;

    @Mock
    private TarifaEnvioRepository tarifaEnvioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private OfertaRepository ofertaRepository;

    @Mock
    private AppConfigService configService;

    @InjectMocks
    private CalculoEnvioService calculoEnvioService;

    @BeforeEach
    void setUp() {
        when(configService.obtenerValorDouble(eq(CalculoEnvioService.CLAVE_TARIFA_DEFECTO), anyDouble()))
                .thenReturn(15000.0);

        // Mapeo de municipios a zonas
        when(zonaMunicipioRepository.findByMunicipioNormalizado("CARTAGENA"))
                .thenReturn(Optional.of(new ZonaMunicipio("Cartagena", 1, "Dique")));
        when(zonaMunicipioRepository.findByMunicipioNormalizado("TURBACO"))
                .thenReturn(Optional.of(new ZonaMunicipio("Turbaco", 1, "Dique")));
        when(zonaMunicipioRepository.findByMunicipioNormalizado("EL CARMEN DE BOLIVAR"))
                .thenReturn(Optional.of(new ZonaMunicipio("El Carmen de Bolívar", 2, "Montes de María")));
        when(zonaMunicipioRepository.findByMunicipioNormalizado("MAGANGUE"))
                .thenReturn(Optional.of(new ZonaMunicipio("Magangué", 3, "Mojana")));

        // Tarifas entre zonas
        // Zona 1 -> Zona 1 (Misma zona): $10.000 COP (1.000.000 centavos)
        when(tarifaEnvioRepository.findByZonaOrigenAndZonaDestino(1, 1))
                .thenReturn(Optional.of(new TarifaEnvio(1, 1, 1000000L)));

        // Zona 2 -> Zona 1 (Distinta zona Montes de María a Dique): $22.000 COP (2.200.000 centavos)
        when(tarifaEnvioRepository.findByZonaOrigenAndZonaDestino(2, 1))
                .thenReturn(Optional.of(new TarifaEnvio(2, 1, 2200000L)));

        // Zona 3 -> Zona 1 (Mojana a Dique): $25.000 COP (2.500.000 centavos)
        when(tarifaEnvioRepository.findByZonaOrigenAndZonaDestino(3, 1))
                .thenReturn(Optional.of(new TarifaEnvio(3, 1, 2500000L)));
    }

    @Test
    @DisplayName("Cálculo 1 vendedor: origen en misma zona (Turbaco -> Cartagena = Zona 1 -> Zona 1)")
    void testCalculoUnVendedorMismaZona() {
        Usuario vendedor = new Usuario();
        vendedor.setId("vend_1");
        vendedor.setUbicacion(new UbicacionUsuario("Turbaco", "Bolívar"));
        when(usuarioRepository.findById("vend_1")).thenReturn(Optional.of(vendedor));

        long tarifaCentavos = calculoEnvioService.calcularTarifaVendedor("vend_1", "Cartagena");

        assertEquals(1000000L, tarifaCentavos, "La tarifa en la misma zona (1 -> 1) debe ser 1.000.000 centavos ($10.000 COP)");
    }

    @Test
    @DisplayName("Cálculo 1 vendedor: origen en distinta zona (El Carmen de Bolívar -> Cartagena = Zona 2 -> Zona 1)")
    void testCalculoUnVendedorDistintaZona() {
        Usuario vendedor = new Usuario();
        vendedor.setId("vend_2");
        vendedor.setUbicacion(new UbicacionUsuario("El Carmen de Bolívar", "Bolívar"));
        when(usuarioRepository.findById("vend_2")).thenReturn(Optional.of(vendedor));

        long tarifaCentavos = calculoEnvioService.calcularTarifaVendedor("vend_2", "Cartagena");

        assertEquals(2200000L, tarifaCentavos, "La tarifa entre Zona 2 y Zona 1 debe ser 2.200.000 centavos ($22.000 COP)");
    }

    @Test
    @DisplayName("Cálculo múltiples vendedores: carrito con 2 vendedores (uno misma zona, otro distinta zona)")
    void testCalculoMultiplesVendedoresCarrito() {
        Usuario vendedor1 = new Usuario();
        vendedor1.setId("vend_1");
        vendedor1.setUbicacion(new UbicacionUsuario("Turbaco", "Bolívar")); // Zona 1
        when(usuarioRepository.findById("vend_1")).thenReturn(Optional.of(vendedor1));

        Usuario vendedor2 = new Usuario();
        vendedor2.setId("vend_2");
        vendedor2.setUbicacion(new UbicacionUsuario("El Carmen de Bolívar", "Bolívar")); // Zona 2
        when(usuarioRepository.findById("vend_2")).thenReturn(Optional.of(vendedor2));

        Carrito carrito = new Carrito("usr_comprador");
        ProductoCarrito item1 = new ProductoCarrito();
        item1.setVendedorId("vend_1");
        item1.setPrecioUnitario(10000.0);
        item1.setCantidad(2.0);

        ProductoCarrito item2 = new ProductoCarrito();
        item2.setVendedorId("vend_2");
        item2.setPrecioUnitario(10000.0);
        item2.setCantidad(3.0);

        carrito.setItems(List.of(item1, item2));

        CalculoEnvioService.ResultadoCalculoEnvio res = calculoEnvioService.calcularTarifaCarrito(carrito, "Cartagena");

        assertNotNull(res);
        // Zona 1 -> Zona 1 = 1000000L; Zona 2 -> Zona 1 = 2200000L; Total = 3200000L ($32.000 COP)
        assertEquals(3200000L, res.totalCentavos());
        assertEquals(32000.0, res.getTotalCop());
        assertEquals(1000000L, res.obtenerTarifaVendedor("vend_1"));
        assertEquals(2200000L, res.obtenerTarifaVendedor("vend_2"));
        assertEquals(1, res.zonaDestino());
    }

    @Test
    @DisplayName("Fallback a tarifa por defecto si la ciudad es desconocida o no hay registro de tarifa")
    void testFallbackTarifaDefecto() {
        Usuario vendedor = new Usuario();
        vendedor.setId("vend_desconocido");
        vendedor.setUbicacion(new UbicacionUsuario("Municipio Inexistente", "Bolívar"));
        when(usuarioRepository.findById("vend_desconocido")).thenReturn(Optional.of(vendedor));

        long tarifaCentavos = calculoEnvioService.calcularTarifaVendedor("vend_desconocido", "Cartagena");

        // Tarifa por defecto configurada: 15.000 COP = 1.500.000 centavos
        assertEquals(1500000L, tarifaCentavos, "Debe retornar la tarifa por defecto cuando no se reconoce la zona");
    }
}
