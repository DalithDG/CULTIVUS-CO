package com.example.demo.Controller;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.MovimientoBilletera;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.BilleteraService;
import com.example.demo.services.VendedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class VendedorControllerBilleteraTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VendedorService vendedorService;

    @MockBean
    private BilleteraService billeteraService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    private Usuario vendedor;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        vendedor = new Usuario();
        vendedor.setId("vend-123");
        vendedor.setNombre("Juan Vendedor");
        vendedor.setEmail("juan@demo.com");
        vendedor.setRol(Role.VENDEDOR);

        session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", vendedor);

        when(vendedorService.esVendedor("vend-123")).thenReturn(true);
    }

    @Test
    void testMostrarBilleteraSinMovimientos() throws Exception {
        Billetera b = new Billetera("vend-123", Billetera.Rol.VENDEDOR);
        when(billeteraService.obtenerOCrearBilleteraVendedor("vend-123")).thenReturn(b);
        when(billeteraService.listarMovimientosVendedor("vend-123")).thenReturn(List.of());

        mockMvc.perform(get("/vendedor/billetera").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("vendedor/billetera-vendedor"));
    }

    @Test
    void testMostrarBilleteraConMovimientos() throws Exception {
        Billetera b = new Billetera("vend-123", Billetera.Rol.VENDEDOR);
        MovimientoBilletera m1 = new MovimientoBilletera("m1", b.getId(), "vend-123", Billetera.Rol.VENDEDOR,
                MovimientoBilletera.Tipo.ABONO, Billetera.EstadoSaldo.EN_RETENCION, 5000000L,
                "ped-1", "comp-1", LocalDateTime.now(), "Venta ped 1", "ABONO:ped-1");
        MovimientoBilletera m2 = new MovimientoBilletera("m2", b.getId(), "vend-123", Billetera.Rol.VENDEDOR,
                MovimientoBilletera.Tipo.COMISION, Billetera.EstadoSaldo.EN_RETENCION, -400000L,
                "ped-1", "comp-1", LocalDateTime.now(), "Comisión ped 1", "COMISION:ped-1");

        when(billeteraService.obtenerOCrearBilleteraVendedor("vend-123")).thenReturn(b);
        when(billeteraService.listarMovimientosVendedor("vend-123")).thenReturn(List.of(m1, m2));

        mockMvc.perform(get("/vendedor/billetera").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("vendedor/billetera-vendedor"));
    }

    @Test
    void testRedirigirBilleteraDesdeUsuarioVendedor() throws Exception {
        mockMvc.perform(get("/usuario/billetera").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/vendedor/billetera"));
    }

    @Test
    void testRedirigirBilleteraDesdeUsuarioNoVendedor() throws Exception {
        when(vendedorService.esVendedor("vend-123")).thenReturn(false);

        mockMvc.perform(get("/usuario/billetera").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/vendedor/registro"));
    }
}
