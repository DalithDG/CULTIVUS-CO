package com.example.demo.Controller;

import com.example.demo.Model.Banco;
import com.example.demo.Model.Billetera;
import com.example.demo.Model.Role;
import com.example.demo.Model.SolicitudRetiro;
import com.example.demo.Model.TipoCuenta;
import com.example.demo.Model.TipoDocumento;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.CuentaBancaria;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.AdminService;
import com.example.demo.services.BilleteraService;
import com.example.demo.services.RetiroService;
import com.example.demo.services.TransportistaService;
import com.example.demo.services.VendedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * US-24 / Fase 8: Pruebas de controladores para cuenta bancaria, solicitud de retiro,
 * comprobante imprimible y panel de administración de retiros.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class RetiroControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VendedorService vendedorService;

    @MockBean
    private TransportistaService transportistaService;

    @MockBean
    private BilleteraService billeteraService;

    @MockBean
    private RetiroService retiroService;

    @MockBean
    private AdminService adminService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    private Usuario vendedor;
    private Usuario transportista;
    private Usuario admin;
    private CuentaBancaria cuentaDemo;

    @BeforeEach
    void setUp() {
        cuentaDemo = new CuentaBancaria(
                Banco.BANCOLOMBIA, null, TipoCuenta.AHORROS,
                "9876543210", "Juan Pérez", TipoDocumento.CC, "10203040");

        vendedor = new Usuario();
        vendedor.setId("vend-1");
        vendedor.setNombre("Juan Pérez");
        vendedor.setEmail("juan@demo.com");
        vendedor.setRoles(Set.of(Role.VENDEDOR));
        PerfilVendedor pv = new PerfilVendedor();
        pv.setRazonSocial("Finca Juan");
        pv.setTelefonoContacto("3101234567");
        pv.setDireccionNegocio("Vereda 1");
        pv.setCuentaRetiro(cuentaDemo);
        vendedor.setPerfilVendedor(pv);

        transportista = new Usuario();
        transportista.setId("trans-1");
        transportista.setNombre("Carlos Ruta");
        transportista.setEmail("carlos@demo.com");
        transportista.setRoles(Set.of(Role.TRANSPORTISTA));
        PerfilTransportista pt = new PerfilTransportista();
        pt.setCuentaRetiro(cuentaDemo);
        transportista.setPerfilTransportista(pt);

        admin = new Usuario();
        admin.setId("adm-1");
        admin.setNombre("Admin General");
        admin.setEmail("admin@demo.com");
        admin.setRoles(Set.of(Role.ADMIN));

        when(vendedorService.esVendedor("vend-1")).thenReturn(true);
        when(adminService.esAdmin("adm-1")).thenReturn(true);
        when(usuarioRepository.findById("vend-1")).thenReturn(Optional.of(vendedor));
        when(usuarioRepository.findById("trans-1")).thenReturn(Optional.of(transportista));
        when(usuarioRepository.findById("adm-1")).thenReturn(Optional.of(admin));
    }

    @Test
    @DisplayName("VendedorController: guardar cuenta bancaria redirige a billetera y solicitar retiro invoca RetiroService")
    void vendedorCuentaYSolicitarRetiro() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", vendedor);

        when(vendedorService.guardarCuentaBancaria(eq("vend-1"), any(CuentaBancaria.class))).thenReturn(vendedor);

        mockMvc.perform(post("/vendedor/cuenta-bancaria")
                        .session(session)
                        .param("banco", "BANCOLOMBIA")
                        .param("tipo", "AHORROS")
                        .param("numero", "9876543210")
                        .param("titular", "Juan Pérez")
                        .param("tipoDocumento", "CC")
                        .param("numeroDocumento", "10203040"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendedor/billetera"));

        SolicitudRetiro sr = new SolicitudRetiro(
                "vend-1", Billetera.Rol.VENDEDOR, "Juan Pérez", "juan@demo.com", 60_000_00L, cuentaDemo);
        sr.setId("sol-vend-1");
        when(retiroService.solicitarRetiro("vend-1", Billetera.Rol.VENDEDOR, "60000", false)).thenReturn(sr);

        mockMvc.perform(post("/vendedor/retiros/solicitar")
                        .session(session)
                        .param("montoPesos", "60000")
                        .param("retirarTodo", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendedor/billetera"));

        verify(retiroService).solicitarRetiro("vend-1", Billetera.Rol.VENDEDOR, "60000", false);
    }

    @Test
    @DisplayName("TransportistaController: muestra billetera con cuenta enmascarada y permite ver comprobante aprobado")
    void transportistaBilleteraYComprobanteEnmascarado() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", transportista);

        Billetera b = new Billetera("trans-1", Billetera.Rol.TRANSPORTISTA);
        when(billeteraService.obtenerOCrearBilleteraTransportista("trans-1")).thenReturn(b);
        when(billeteraService.listarMovimientosTransportista("trans-1")).thenReturn(List.of());
        when(retiroService.obtenerRetiroMinimoCop()).thenReturn(50_000L);

        mockMvc.perform(get("/transportista/billetera").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("transportista/billetera-transportista"))
                .andExpect(content().string(containsString("****3210")))
                .andExpect(content().string(not(containsString("9876543210"))));

        SolicitudRetiro aprobada = new SolicitudRetiro(
                "trans-1", Billetera.Rol.TRANSPORTISTA, "Carlos Ruta", "carlos@demo.com", 75_000_00L, cuentaDemo);
        aprobada.setId("sol-trans-99");
        aprobada.setEstado(SolicitudRetiro.Estado.APROBADO);
        aprobada.setNumeroComprobante("RET-2026-RANS99");

        when(retiroService.obtenerComprobanteParaUsuario("sol-trans-99", "trans-1", Billetera.Rol.TRANSPORTISTA))
                .thenReturn(aprobada);

        mockMvc.perform(get("/transportista/retiros/sol-trans-99/comprobante").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("comprobante-retiro"))
                .andExpect(content().string(containsString("Comprobante simulado — no corresponde a una transferencia bancaria real")))
                .andExpect(content().string(containsString("RET-2026-RANS99")))
                .andExpect(content().string(containsString("****3210")))
                .andExpect(content().string(not(containsString("9876543210"))));
    }

    @Test
    @DisplayName("AdminController: lista retiros mostrando cuenta completa y permite aprobar y rechazar")
    void adminRetirosListaApruebaYRechaza() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogueado", admin);

        SolicitudRetiro pendiente = new SolicitudRetiro(
                "vend-1", Billetera.Rol.VENDEDOR, "Juan Pérez", "juan@demo.com", 80_000_00L, cuentaDemo);
        pendiente.setId("sol-adm-1");

        when(retiroService.contarPendientes()).thenReturn(1L);
        when(retiroService.listarParaAdmin(eq(SolicitudRetiro.Estado.PENDIENTE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendiente)));

        // El admin sí ve el número de cuenta completo (9876543210) en /admin/retiros
        mockMvc.perform(get("/admin/retiros").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/retiros"))
                .andExpect(content().string(containsString("9876543210")));

        pendiente.setEstado(SolicitudRetiro.Estado.APROBADO);
        pendiente.setNumeroComprobante("RET-2026-ADM001");
        when(retiroService.aprobarRetiro("sol-adm-1", "adm-1", "Admin General", "REF-777"))
                .thenReturn(pendiente);

        mockMvc.perform(post("/admin/retiros/sol-adm-1/aprobar")
                        .session(session)
                        .param("referencia", "REF-777"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/retiros"));

        verify(retiroService).aprobarRetiro("sol-adm-1", "adm-1", "Admin General", "REF-777");

        mockMvc.perform(post("/admin/retiros/sol-adm-2/rechazar")
                        .session(session)
                        .param("motivo", "Cuenta inválida"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/retiros"));

        verify(retiroService).rechazarRetiro("sol-adm-2", "adm-1", "Admin General", "Cuenta inválida");
    }
}

