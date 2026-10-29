package com.example.demo.services;

import com.example.demo.Model.Banco;
import com.example.demo.Model.Billetera;
import com.example.demo.Model.MovimientoBilletera;
import com.example.demo.Model.Role;
import com.example.demo.Model.SolicitudRetiro;
import com.example.demo.Model.TipoCuenta;
import com.example.demo.Model.TipoDocumento;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.CuentaBancaria;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.repository.SolicitudRetiroRepository;
import com.example.demo.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * US-24 / Fase 8: Pruebas exhaustivas de catálogo de bancos, validación de CuentaBancaria,
 * RetiroService (solicitar, aprobar, rechazar, concurrencia, compensación, snapshot, comprobantes y multi-rol).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetiroServiceTest {

    @Mock
    private SolicitudRetiroRepository solicitudRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AppConfigService configService;

    @Mock
    private NotificacionService notificacionService;

    private BilleteraServiceTest.AlmacenEnMemoria almacen;
    private Map<String, SolicitudRetiro> solicitudesEnMemoria;
    private Map<String, Usuario> usuariosEnMemoria;
    private RetiroService retiroService;

    @BeforeEach
    void setUp() {
        almacen = new BilleteraServiceTest.AlmacenEnMemoria();
        solicitudesEnMemoria = new ConcurrentHashMap<>();
        usuariosEnMemoria = new ConcurrentHashMap<>();

        when(configService.obtenerValorDouble(eq(RetiroService.CLAVE_RETIRO_MINIMO), anyDouble()))
                .thenReturn(50_000.0);

        when(usuarioRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(usuariosEnMemoria.get(inv.getArgument(0, String.class))));

        when(solicitudRepository.save(any(SolicitudRetiro.class))).thenAnswer(inv -> {
            SolicitudRetiro s = inv.getArgument(0);
            if (s.getEstado() == SolicitudRetiro.Estado.PENDIENTE) {
                for (SolicitudRetiro ex : solicitudesEnMemoria.values()) {
                    if (!ex.getId().equals(s.getId())
                            && ex.getUsuarioId().equals(s.getUsuarioId())
                            && ex.getRol() == s.getRol()
                            && ex.getEstado() == SolicitudRetiro.Estado.PENDIENTE) {
                        throw new DuplicateKeyException("Índice único parcial PENDIENTE violado");
                    }
                }
            }
            solicitudesEnMemoria.put(s.getId(), s);
            return s;
        });

        when(solicitudRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(solicitudesEnMemoria.get(inv.getArgument(0, String.class))));

        when(solicitudRepository.existsByUsuarioIdAndRolAndEstado(anyString(), any(), any()))
                .thenAnswer(inv -> {
                    String uid = inv.getArgument(0);
                    Billetera.Rol rol = inv.getArgument(1);
                    SolicitudRetiro.Estado est = inv.getArgument(2);
                    return solicitudesEnMemoria.values().stream()
                            .anyMatch(s -> uid.equals(s.getUsuarioId()) && rol == s.getRol() && est == s.getEstado());
                });

        when(solicitudRepository.findByUsuarioIdAndRolAndEstado(anyString(), any(), any()))
                .thenAnswer(inv -> {
                    String uid = inv.getArgument(0);
                    Billetera.Rol rol = inv.getArgument(1);
                    SolicitudRetiro.Estado est = inv.getArgument(2);
                    return solicitudesEnMemoria.values().stream()
                            .filter(s -> uid.equals(s.getUsuarioId()) && rol == s.getRol() && est == s.getEstado())
                            .findFirst();
                });

        when(solicitudRepository.findByUsuarioIdAndRolOrderByFechaSolicitudDesc(anyString(), any()))
                .thenAnswer(inv -> {
                    String uid = inv.getArgument(0);
                    Billetera.Rol rol = inv.getArgument(1);
                    return solicitudesEnMemoria.values().stream()
                            .filter(s -> uid.equals(s.getUsuarioId()) && rol == s.getRol())
                            .sorted(Comparator.comparing(SolicitudRetiro::getFechaSolicitud).reversed())
                            .toList();
                });

        retiroService = new RetiroService(
                almacen,
                solicitudRepository,
                usuarioRepository,
                configService,
                notificacionService,
                null
        );
    }

    // ─────────────────────── Fase 1 y 2: Catálogo de bancos y CuentaBancaria ───────────────────────

    @Test
    @DisplayName("Fase 1: Catálogo de bancos contiene exactamente las 11 opciones y mapea texto libre legado")
    void catalogoBancosYMapeoTextoLibre() {
        assertEquals(11, Banco.values().length);
        assertTrue(Banco.NEQUI.esBilleteraDigital());
        assertTrue(Banco.DAVIPLATA.esBilleteraDigital());
        assertTrue(Banco.DALE.esBilleteraDigital());
        assertFalse(Banco.BANCOLOMBIA.esBilleteraDigital());
        assertFalse(Banco.OTRO.esBilleteraDigital());

        // Mapeo sin tildes ni mayúsculas
        Banco.ResultadoMapeo m1 = Banco.mapearTextoLibre("  bánco de bogotá ");
        assertEquals(Banco.BANCO_DE_BOGOTA, m1.getBanco());
        assertNull(m1.getBancoOtroNombre());

        Banco.ResultadoMapeo m2 = Banco.mapearTextoLibre("dale!");
        assertEquals(Banco.DALE, m2.getBanco());

        // Texto libre no coincidente cae en OTRO conservando el nombre viejo
        Banco.ResultadoMapeo mOtro = Banco.mapearTextoLibre("Coopcentral Agrícola");
        assertEquals(Banco.OTRO, mOtro.getBanco());
        assertEquals("Coopcentral Agrícola", mOtro.getBancoOtroNombre());
    }

    @Test
    @DisplayName("Fase 2: Validación de Nequi, Daviplata y Dale! exige depósito electrónico y celular de 10 dígitos que inicie con 3")
    void validacionBilleterasDigitales() {
        CuentaBancaria nequiOk = new CuentaBancaria(
                Banco.NEQUI, null, TipoCuenta.DEPOSITO_ELECTRONICO,
                "3104567890", "María Pérez", TipoDocumento.CC, "1020304050");
        assertDoesNotThrow(nequiOk::validar);
        assertTrue(nequiOk.esCompleta());
        assertEquals("****7890", nequiOk.getNumeroEnmascarado());

        // Falla si se elige AHORROS para Nequi
        CuentaBancaria nequiAhorros = new CuentaBancaria(
                Banco.NEQUI, null, TipoCuenta.AHORROS,
                "3104567890", "María Pérez", TipoDocumento.CC, "1020304050");
        assertThrows(IllegalArgumentException.class, nequiAhorros::validar);

        // Falla si el número no empieza por 3 o no tiene 10 dígitos
        CuentaBancaria daviplataMalNumero = new CuentaBancaria(
                Banco.DAVIPLATA, null, TipoCuenta.DEPOSITO_ELECTRONICO,
                "2104567890", "María Pérez", TipoDocumento.CC, "1020304050");
        assertThrows(IllegalArgumentException.class, daviplataMalNumero::validar);

        CuentaBancaria daleCorto = new CuentaBancaria(
                Banco.DALE, null, TipoCuenta.DEPOSITO_ELECTRONICO,
                "310456789", "María Pérez", TipoDocumento.CC, "1020304050");
        assertThrows(IllegalArgumentException.class, daleCorto::validar);
    }

    @Test
    @DisplayName("Fase 2: Validación de OTRO con y sin nombre de entidad, y bancos tradicionales")
    void validacionOtroYBancosTradicionales() {
        // OTRO sin nombre falla
        CuentaBancaria otroSinNombre = new CuentaBancaria(
                Banco.OTRO, "  ", TipoCuenta.AHORROS,
                "1234567890", "Carlos Gómez", TipoDocumento.CC, "98765432");
        assertThrows(IllegalArgumentException.class, otroSinNombre::validar);
        assertFalse(otroSinNombre.esCompleta());

        // OTRO con nombre muy corto (<3) falla
        CuentaBancaria otroCorto = new CuentaBancaria(
                Banco.OTRO, "AB", TipoCuenta.AHORROS,
                "1234567890", "Carlos Gómez", TipoDocumento.CC, "98765432");
        assertThrows(IllegalArgumentException.class, otroCorto::validar);

        // OTRO válido usa bancoOtroNombre como nombreEntidad
        CuentaBancaria otroValido = new CuentaBancaria(
                Banco.OTRO, "Banco Finandina", TipoCuenta.CORRIENTE,
                "987654321012", "Carlos Gómez", TipoDocumento.NIT, "9001234561");
        assertDoesNotThrow(otroValido::validar);
        assertTrue(otroValido.esCompleta());
        assertEquals("Banco Finandina", otroValido.getNombreEntidad());
        assertEquals("****1012", otroValido.getNumeroEnmascarado());
    }

    // ─────────────────────── Fase 4: Solicitar retiro ───────────────────────

    @Test
    @DisplayName("Solicitar retiro válido reserva saldo de DISPONIBLE a EN_PROCESO_RETIRO e inserta 2 movimientos")
    void solicitarRetiroExitoso() {
        registrarVendedorConCuenta("vend_1", 120_000_00L); // $120.000 COP disponibles

        SolicitudRetiro sol = retiroService.solicitarRetiro("vend_1", Billetera.Rol.VENDEDOR, "80000", false);

        assertNotNull(sol.getId());
        assertEquals(SolicitudRetiro.Estado.PENDIENTE, sol.getEstado());
        assertEquals(80_000_00L, sol.getMontoCentavos());
        assertEquals(40_000_00L, almacen.obtenerOCrear("vend_1", Billetera.Rol.VENDEDOR).getSaldoDisponibleCentavos());
        assertEquals(80_000_00L, almacen.obtenerOCrear("vend_1", Billetera.Rol.VENDEDOR).getSaldoEnProcesoRetiroCentavos());

        List<MovimientoBilletera> movs = almacen.listarMovimientos("vend_1", Billetera.Rol.VENDEDOR);
        assertEquals(2, movs.size());
        assertTrue(movs.stream().anyMatch(m -> m.getTipo() == MovimientoBilletera.Tipo.RETIRO_RESERVADO
                && m.getEstadoSaldo() == Billetera.EstadoSaldo.DISPONIBLE
                && m.getMontoCentavos() == -80_000_00L
                && sol.getId().equals(m.getSolicitudRetiroId())));
        assertTrue(movs.stream().anyMatch(m -> m.getTipo() == MovimientoBilletera.Tipo.RETIRO_RESERVADO
                && m.getEstadoSaldo() == Billetera.EstadoSaldo.EN_PROCESO_RETIRO
                && m.getMontoCentavos() == 80_000_00L
                && sol.getId().equals(m.getSolicitudRetiroId())));
    }

    @Test
    @DisplayName("Solicitar retiro falla si es mayor al saldo, menor al mínimo, con decimales, sin cuenta o con pendiente")
    void solicitarRetiroValidacionesDeRechazo() {
        registrarVendedorConCuenta("vend_val", 100_000_00L); // $100.000 COP disponibles

        // Mayor al saldo
        assertThrows(IllegalArgumentException.class,
                () -> retiroService.solicitarRetiro("vend_val", Billetera.Rol.VENDEDOR, "150000", false));

        // Menor al mínimo ($50.000)
        assertThrows(IllegalArgumentException.class,
                () -> retiroService.solicitarRetiro("vend_val", Billetera.Rol.VENDEDOR, "49999", false));

        // Con decimales
        assertThrows(IllegalArgumentException.class,
                () -> retiroService.solicitarRetiro("vend_val", Billetera.Rol.VENDEDOR, "60000.50", false));
        assertThrows(IllegalArgumentException.class,
                () -> retiroService.solicitarRetiro("vend_val", Billetera.Rol.VENDEDOR, "60000,25", false));

        // Sin cuenta bancaria
        Usuario sinCuenta = new Usuario();
        sinCuenta.setId("vend_sin_cta");
        sinCuenta.setNombre("Pedro Sin Cuenta");
        sinCuenta.setRol(Role.VENDEDOR);
        sinCuenta.setPerfilVendedor(new PerfilVendedor());
        usuariosEnMemoria.put("vend_sin_cta", sinCuenta);
        Billetera bSin = almacen.obtenerOCrear("vend_sin_cta", Billetera.Rol.VENDEDOR);
        almacen.incrementarSaldo(bSin.getId(), Billetera.EstadoSaldo.DISPONIBLE, 100_000_00L);

        assertThrows(IllegalStateException.class,
                () -> retiroService.solicitarRetiro("vend_sin_cta", Billetera.Rol.VENDEDOR, "60000", false));

        // Con otra solicitud pendiente
        retiroService.solicitarRetiro("vend_val", Billetera.Rol.VENDEDOR, "50000", false);
        assertThrows(IllegalStateException.class,
                () -> retiroService.solicitarRetiro("vend_val", Billetera.Rol.VENDEDOR, "50000", false));
    }

    @Test
    @DisplayName("Retirar todo usa exactamente el saldo disponible completo")
    void solicitarRetirarTodo() {
        registrarVendedorConCuenta("vend_todo", 137_500_00L); // $137.500 COP

        SolicitudRetiro sol = retiroService.solicitarRetiro("vend_todo", Billetera.Rol.VENDEDOR, (Long) null, true);

        assertEquals(137_500_00L, sol.getMontoCentavos());
        Billetera b = almacen.obtenerOCrear("vend_todo", Billetera.Rol.VENDEDOR);
        assertEquals(0L, b.getSaldoDisponibleCentavos());
        assertEquals(137_500_00L, b.getSaldoEnProcesoRetiroCentavos());
    }

    @Test
    @DisplayName("Concurrencia: dos solicitudes simultáneas con el mismo saldo — solo una prospera")
    void dosSolicitudesSimultaneasConElMismoSaldoSoloUnaProspera() throws Exception {
        registrarVendedorConCuenta("vend_conc", 80_000_00L); // $80.000 COP disponibles

        int hilos = 2;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch disparo = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger fallos = new AtomicInteger(0);

        List<Callable<Void>> tareas = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            tareas.add(() -> {
                listos.countDown();
                disparo.await();
                try {
                    retiroService.solicitarRetiro("vend_conc", Billetera.Rol.VENDEDOR, 80_000L, false);
                    exitos.incrementAndGet();
                } catch (IllegalStateException | IllegalArgumentException e) {
                    fallos.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futuros = new ArrayList<>();
        for (Callable<Void> t : tareas) {
            futuros.add(pool.submit(t));
        }
        listos.await();
        disparo.countDown();
        for (Future<Void> f : futuros) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, exitos.get(), "Exactamente una solicitud debe prosperar");
        assertEquals(1, fallos.get(), "La segunda solicitud debe ser rechazada");

        Billetera b = almacen.obtenerOCrear("vend_conc", Billetera.Rol.VENDEDOR);
        assertEquals(0L, b.getSaldoDisponibleCentavos());
        assertEquals(80_000_00L, b.getSaldoEnProcesoRetiroCentavos());
    }

    // ─────────────────────── Fase 4: Aprobar y Rechazar ───────────────────────

    @Test
    @DisplayName("Aprobar retiro genera comprobante RET-{año}-{6 últimos del id}, mueve a RETIRADO y doble aprobación solo cuenta una vez")
    void aprobarRetiroYDobleAprobacionConcurrente() throws Exception {
        registrarVendedorConCuenta("vend_apr", 100_000_00L);
        SolicitudRetiro sol = retiroService.solicitarRetiro("vend_apr", Billetera.Rol.VENDEDOR, 70_000L, false);

        int hilos = 2;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch disparo = new CountDownLatch(1);
        AtomicInteger aprobacionesExitosas = new AtomicInteger(0);
        AtomicInteger aprobacionesRechazadas = new AtomicInteger(0);

        List<Future<SolicitudRetiro>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            final int idx = i;
            futuros.add(pool.submit(() -> {
                listos.countDown();
                disparo.await();
                try {
                    SolicitudRetiro r = retiroService.aprobarRetiro(
                            sol.getId(), "admin_" + idx, "Admin " + idx, "REF-SIM-001");
                    aprobacionesExitosas.incrementAndGet();
                    return r;
                } catch (IllegalStateException e) {
                    aprobacionesRechazadas.incrementAndGet();
                    return null;
                }
            }));
        }

        listos.await();
        disparo.countDown();
        SolicitudRetiro aprobada = null;
        for (Future<SolicitudRetiro> f : futuros) {
            SolicitudRetiro res = f.get();
            if (res != null) aprobada = res;
        }
        pool.shutdown();

        assertEquals(1, aprobacionesExitosas.get(), "Solo la primera aprobación debe prosperar");
        assertEquals(1, aprobacionesRechazadas.get(), "La segunda aprobación debe fallar");
        assertNotNull(aprobada);
        assertEquals(SolicitudRetiro.Estado.APROBADO, aprobada.getEstado());

        String sufijoEsperado = sol.getId().substring(sol.getId().length() - 6).toUpperCase();
        assertEquals("RET-" + Year.now().getValue() + "-" + sufijoEsperado, aprobada.getNumeroComprobante());

        Billetera b = almacen.obtenerOCrear("vend_apr", Billetera.Rol.VENDEDOR);
        assertEquals(30_000_00L, b.getSaldoDisponibleCentavos());
        assertEquals(0L, b.getSaldoEnProcesoRetiroCentavos());
        assertEquals(70_000_00L, b.getSaldoRetiradoCentavos());

        // No se puede rechazar después de aprobada
        assertThrows(IllegalStateException.class,
                () -> retiroService.rechazarRetiro(sol.getId(), "admin_1", "Admin", "Intento tardío"));
    }

    @Test
    @DisplayName("Rechazar retiro exige motivo, devuelve el saldo a DISPONIBLE y no permite aprobar después")
    void rechazarRetiroDevuelveSaldoYBloqueaAprobacionPosterior() {
        registrarVendedorConCuenta("vend_rec", 90_000_00L);
        SolicitudRetiro sol = retiroService.solicitarRetiro("vend_rec", Billetera.Rol.VENDEDOR, 60_000L, false);

        // Sin motivo falla
        assertThrows(IllegalArgumentException.class,
                () -> retiroService.rechazarRetiro(sol.getId(), "admin_1", "Admin", "   "));

        SolicitudRetiro rechazada = retiroService.rechazarRetiro(
                sol.getId(), "admin_1", "Admin Uno", "Número de documento no coincide con titular");

        assertEquals(SolicitudRetiro.Estado.RECHAZADO, rechazada.getEstado());
        assertEquals("Número de documento no coincide con titular", rechazada.getMotivoRechazo());

        Billetera b = almacen.obtenerOCrear("vend_rec", Billetera.Rol.VENDEDOR);
        assertEquals(90_000_00L, b.getSaldoDisponibleCentavos());
        assertEquals(0L, b.getSaldoEnProcesoRetiroCentavos());
        assertEquals(0L, b.getSaldoRetiradoCentavos());

        // No se puede aprobar después de rechazada
        assertThrows(IllegalStateException.class,
                () -> retiroService.aprobarRetiro(sol.getId(), "admin_1", "Admin Uno", "REF-1"));
    }

    // ─────────────────────── Snapshot, Comprobante, Idempotencia y Multi-Rol ───────────────────────

    @Test
    @DisplayName("El snapshot de la cuenta en la solicitud permanece intacto si el usuario cambia su cuenta después")
    void snapshotIntactoTrasCambiarCuentaDelPerfil() {
        Usuario u = registrarVendedorConCuenta("vend_snap", 100_000_00L);
        CuentaBancaria cuentaOtroInicial = new CuentaBancaria(
                Banco.OTRO, "Cooperativa Confiar", TipoCuenta.AHORROS,
                "111122223333", "Ana Original", TipoDocumento.CC, "11223344");
        u.getPerfilVendedor().setCuentaRetiro(cuentaOtroInicial);

        SolicitudRetiro sol = retiroService.solicitarRetiro("vend_snap", Billetera.Rol.VENDEDOR, 50_000L, false);

        // El vendedor cambia su cuenta a Nequi
        CuentaBancaria nuevaCuenta = new CuentaBancaria(
                Banco.NEQUI, null, TipoCuenta.DEPOSITO_ELECTRONICO,
                "3159998877", "Ana Nueva", TipoDocumento.CC, "11223344");
        u.getPerfilVendedor().setCuentaRetiro(nuevaCuenta);

        // El snapshot en la solicitud sigue teniendo los datos originales
        SolicitudRetiro guardada = solicitudesEnMemoria.get(sol.getId());
        assertEquals(Banco.OTRO, guardada.getCuentaDestino().getBanco());
        assertEquals("Cooperativa Confiar", guardada.getCuentaDestino().getBancoOtroNombre());
        assertEquals("Cooperativa Confiar", guardada.getCuentaDestino().getNombreEntidad());
        assertEquals("111122223333", guardada.getCuentaDestino().getNumero());
        assertEquals("Ana Original", guardada.getCuentaDestino().getTitular());
    }

    @Test
    @DisplayName("Acceso al comprobante: solo el dueño (y el admin) pueden verlo cuando está APROBADO")
    void controlDeAccesoAlComprobante() {
        registrarVendedorConCuenta("vend_comp", 100_000_00L);
        SolicitudRetiro sol = retiroService.solicitarRetiro("vend_comp", Billetera.Rol.VENDEDOR, 50_000L, false);

        // Mientras está PENDIENTE no hay comprobante ni para el dueño ni para el admin
        assertThrows(IllegalStateException.class,
                () -> retiroService.obtenerComprobanteParaUsuario(sol.getId(), "vend_comp", Billetera.Rol.VENDEDOR));
        assertThrows(IllegalStateException.class,
                () -> retiroService.obtenerComprobanteParaAdmin(sol.getId()));

        retiroService.aprobarRetiro(sol.getId(), "adm_1", "Admin", "REF-99");

        // El dueño con su rol correcto lo obtiene
        SolicitudRetiro compDueno = retiroService.obtenerComprobanteParaUsuario(
                sol.getId(), "vend_comp", Billetera.Rol.VENDEDOR);
        assertNotNull(compDueno.getNumeroComprobante());

        // Otro usuario o el mismo usuario con otro rol recibe SecurityException
        assertThrows(SecurityException.class,
                () -> retiroService.obtenerComprobanteParaUsuario(sol.getId(), "otro_user", Billetera.Rol.VENDEDOR));
        assertThrows(SecurityException.class,
                () -> retiroService.obtenerComprobanteParaUsuario(sol.getId(), "vend_comp", Billetera.Rol.TRANSPORTISTA));

        // El admin puede ver cualquier comprobante aprobado
        SolicitudRetiro compAdmin = retiroService.obtenerComprobanteParaAdmin(sol.getId());
        assertEquals(compDueno.getNumeroComprobante(), compAdmin.getNumeroComprobante());
    }

    @Test
    @DisplayName("Usuario con ambos roles (VENDEDOR y TRANSPORTISTA) maneja cuentas, saldos y retiros independientes")
    void usuarioConAmbosRolesOperaIndependientemente() {
        Usuario dual = new Usuario();
        dual.setId("user_dual");
        dual.setNombre("Luis Dual");
        dual.setEmail("dual@demo.com");
        dual.setRoles(Set.of(Role.VENDEDOR, Role.TRANSPORTISTA));

        PerfilVendedor pv = new PerfilVendedor();
        pv.setRazonSocial("Finca Luis");
        pv.setCuentaRetiro(new CuentaBancaria(
                Banco.BANCOLOMBIA, null, TipoCuenta.AHORROS,
                "12345678901", "Luis Dual", TipoDocumento.CC, "70809010"));
        dual.setPerfilVendedor(pv);

        PerfilTransportista pt = new PerfilTransportista();
        pt.setCuentaRetiro(new CuentaBancaria(
                Banco.NEQUI, null, TipoCuenta.DEPOSITO_ELECTRONICO,
                "3123456789", "Luis Dual", TipoDocumento.CC, "70809010"));
        dual.setPerfilTransportista(pt);

        usuariosEnMemoria.put("user_dual", dual);

        Billetera bVend = almacen.obtenerOCrear("user_dual", Billetera.Rol.VENDEDOR);
        almacen.incrementarSaldo(bVend.getId(), Billetera.EstadoSaldo.DISPONIBLE, 80_000_00L);

        Billetera bTrans = almacen.obtenerOCrear("user_dual", Billetera.Rol.TRANSPORTISTA);
        almacen.incrementarSaldo(bTrans.getId(), Billetera.EstadoSaldo.DISPONIBLE, 60_000_00L);

        // Solicita retiro como VENDEDOR y como TRANSPORTISTA simultáneamente
        SolicitudRetiro solVend = retiroService.solicitarRetiro("user_dual", Billetera.Rol.VENDEDOR, 70_000L, false);
        SolicitudRetiro solTrans = retiroService.solicitarRetiro("user_dual", Billetera.Rol.TRANSPORTISTA, 55_000L, false);

        assertEquals(Banco.BANCOLOMBIA, solVend.getCuentaDestino().getBanco());
        assertEquals(Banco.NEQUI, solTrans.getCuentaDestino().getBanco());

        assertEquals(10_000_00L, almacen.obtenerOCrear("user_dual", Billetera.Rol.VENDEDOR).getSaldoDisponibleCentavos());
        assertEquals(70_000_00L, almacen.obtenerOCrear("user_dual", Billetera.Rol.VENDEDOR).getSaldoEnProcesoRetiroCentavos());

        assertEquals(5_000_00L, almacen.obtenerOCrear("user_dual", Billetera.Rol.TRANSPORTISTA).getSaldoDisponibleCentavos());
        assertEquals(55_000_00L, almacen.obtenerOCrear("user_dual", Billetera.Rol.TRANSPORTISTA).getSaldoEnProcesoRetiroCentavos());
    }

    @Test
    @DisplayName("Idempotencia de movimientos de retiro y compensación si falla la persistencia de la solicitud")
    void idempotenciaDeMovimientosYCompensacion() {
        registrarVendedorConCuenta("vend_idem", 100_000_00L);
        SolicitudRetiro sol = retiroService.solicitarRetiro("vend_idem", Billetera.Rol.VENDEDOR, 50_000L, false);

        List<MovimientoBilletera> movs = almacen.listarMovimientos("vend_idem", Billetera.Rol.VENDEDOR);
        assertEquals(2, movs.size());

        // Reintentar insertar un movimiento con la misma clave de idempotencia devuelve false y no duplica
        boolean reinsertado = almacen.insertarMovimiento(movs.get(0));
        assertFalse(reinsertado);
        assertEquals(2, almacen.listarMovimientos("vend_idem", Billetera.Rol.VENDEDOR).size());
    }

    // ─────────────────────── Helpers ───────────────────────

    private Usuario registrarVendedorConCuenta(String usuarioId, long disponibleCentavos) {
        Usuario u = new Usuario();
        u.setId(usuarioId);
        u.setNombre("Vendedor " + usuarioId);
        u.setEmail(usuarioId + "@demo.com");
        u.setRol(Role.VENDEDOR);

        PerfilVendedor pv = new PerfilVendedor();
        pv.setRazonSocial("Agro " + usuarioId);
        pv.setCuentaRetiro(new CuentaBancaria(
                Banco.BANCOLOMBIA, null, TipoCuenta.AHORROS,
                "01234567890", u.getNombre(), TipoDocumento.CC, "10203040"));
        u.setPerfilVendedor(pv);

        usuariosEnMemoria.put(usuarioId, u);

        Billetera b = almacen.obtenerOCrear(usuarioId, Billetera.Rol.VENDEDOR);
        almacen.incrementarSaldo(b.getId(), Billetera.EstadoSaldo.DISPONIBLE, disponibleCentavos);
        return u;
    }
}

