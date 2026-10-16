package com.example.demo.services;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.Compra;
import com.example.demo.Model.MovimientoBilletera;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Pruebas de US-22: billetera, comisión, reparto por vendedor, idempotencia e inmutabilidad. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BilleteraServiceTest {

    private static final String REFERENCIA = "CUL-1720000000000-WAL001";

    /** Almacén en memoria que imita el índice único de la clave de idempotencia. */
    static class AlmacenEnMemoria implements BilleteraAlmacen {
        final Map<String, SaldosMutables> billeteras = new HashMap<>();
        final Map<String, Billetera> entidades = new HashMap<>();
        final List<MovimientoBilletera> movimientos = new ArrayList<>();
        final Set<String> claves = new HashSet<>();

        static class SaldosMutables { long retencion; long disponible; long retirado; }

        @Override
        public Billetera obtenerOCrear(String usuarioId, Billetera.Rol rol) {
            String k = usuarioId + "|" + rol;
            return entidades.computeIfAbsent(k, x -> {
                Billetera b = new Billetera(usuarioId, rol);
                try {
                    Field f = Billetera.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(b, "wal_" + usuarioId);
                } catch (Exception e) { throw new RuntimeException(e); }
                billeteras.put("wal_" + usuarioId, new SaldosMutables());
                return b;
            });
        }

        @Override
        public boolean insertarMovimiento(MovimientoBilletera m) {
            if (m.getClaveIdempotencia() != null && !claves.add(m.getClaveIdempotencia())) {
                return false;
            }
            movimientos.add(m);
            return true;
        }

        @Override
        public void incrementarSaldo(String billeteraId, Billetera.EstadoSaldo estado, long delta) {
            SaldosMutables s = billeteras.get(billeteraId);
            switch (estado) {
                case EN_RETENCION: s.retencion += delta; break;
                case DISPONIBLE: s.disponible += delta; break;
                default: s.retirado += delta;
            }
        }

        @Override
        public Optional<Billetera> buscar(String usuarioId, Billetera.Rol rol) {
            return Optional.ofNullable(entidades.get(usuarioId + "|" + rol));
        }

        @Override
        public List<MovimientoBilletera> listarMovimientos(String usuarioId, Billetera.Rol rol) {
            return movimientos.stream().filter(m -> m.getUsuarioId().equals(usuarioId)).collect(Collectors.toList());
        }

        long retencion(String vendedorId) { return billeteras.get("wal_" + vendedorId).retencion; }
        long disponible(String vendedorId) { return billeteras.get("wal_" + vendedorId).disponible; }
        long retirado(String vendedorId) { return billeteras.get("wal_" + vendedorId).retirado; }
        List<MovimientoBilletera> deVendedor(String v) {
            return movimientos.stream().filter(m -> m.getUsuarioId().equals(v)).collect(Collectors.toList());
        }
    }

    @Mock private AppConfigService configService;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private OfertaRepository ofertaRepository;
    @Mock private CompraRepository compraRepository;

    private AlmacenEnMemoria almacen;
    private BilleteraService billeteraService;

    @BeforeEach
    void setUp() {
        almacen = new AlmacenEnMemoria();
        when(configService.obtenerValorDouble(eq(BilleteraService.CLAVE_COMISION), anyDouble()))
                .thenAnswer(inv -> inv.getArgument(1)); // sin valor configurado: usa el 8 % por defecto
        billeteraService = new BilleteraService(almacen, configService);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        when(compraRepository.save(any(Compra.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static double anyDouble() { return org.mockito.ArgumentMatchers.anyDouble(); }
    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }

    private Pedido pedido(String id, String vendedorId, String ofertaId, long subtotalCentavos, long totalFirmado) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setCompraId("compra_w");
        p.setSubtotalCentavos(subtotalCentavos);
        p.setEstado("PENDIENTE");
        p.setComprador(new DatosComprador("comprador_1", "Comprador"));
        p.setVendedor(new DatosVendedor(vendedorId, "Finca " + vendedorId, true));
        ProductoPedido item = new ProductoPedido();
        item.setProductoId(ofertaId);
        item.setNombreSnapshot("Producto " + id);
        item.setCantidad(1.0);
        item.setPrecioUnitario(subtotalCentavos / 100.0);
        item.setSubtotal(subtotalCentavos / 100.0);
        p.setItems(List.of(item));
        DatosPago pago = new DatosPago("WOMPI", subtotalCentavos / 100.0);
        pago.setEstado("PENDIENTE");
        pago.setReferencia(REFERENCIA);
        pago.setMontoCentavosFirmado(totalFirmado);
        p.setPago(pago);
        return p;
    }

    private Compra compra(long subtotal, long envio) {
        Compra c = new Compra(new DatosComprador("comprador_1", "Comprador"), REFERENCIA, subtotal, envio);
        c.setId("compra_w");
        return c;
    }

    // ───────── comisión y reparto ─────────

    @Test
    @DisplayName("Reparto con varios vendedores: cada uno recibe su subtotal menos 8 % en retención, sin envío")
    void repartoVariosVendedoresSinEnvio() {
        // Subtotales: 1,000,000 y 333,333 centavos; envío 500,000 (no debe repartirse ni llevar comisión)
        Pedido p1 = pedido("ped_1", "vend_1", "of_1", 1_000_000L, 1_833_333L);
        Pedido p2 = pedido("ped_2", "vend_2", "of_2", 333_333L, 1_833_333L);

        billeteraService.registrarRepartoVentas(compra(1_333_333L, 500_000L), List.of(p1, p2));

        // vend_1: 1,000,000 * 92 % = 920,000 ; comisión 80,000
        assertEquals(920_000L, almacen.retencion("vend_1"));
        // vend_2: floor(333,333 * 0.92) = 306,666 ; comisión 26,667 (incluye el residuo)
        assertEquals(306_666L, almacen.retencion("vend_2"));
        // Solo se usa "en retención"
        assertEquals(0L, almacen.disponible("vend_1"));
        assertEquals(0L, almacen.retirado("vend_1"));
        // Cada pedido deja abono + comisión como movimientos separados, con pedido y compra
        List<MovimientoBilletera> movs1 = almacen.deVendedor("vend_1");
        assertEquals(2, movs1.size());
        MovimientoBilletera abono = movs1.stream().filter(m -> m.getTipo() == MovimientoBilletera.Tipo.ABONO).findFirst().orElseThrow();
        MovimientoBilletera comision = movs1.stream().filter(m -> m.getTipo() == MovimientoBilletera.Tipo.COMISION).findFirst().orElseThrow();
        assertEquals(1_000_000L, abono.getMontoCentavos());
        assertEquals(-80_000L, comision.getMontoCentavos());
        assertEquals("ped_1", abono.getPedidoId());
        assertEquals("compra_w", comision.getCompraId());
        assertNotNull(abono.getFecha());
        assertEquals(Billetera.EstadoSaldo.EN_RETENCION, abono.getEstadoSaldo());
        assertEquals(Billetera.Rol.VENDEDOR, abono.getRol());
    }

    @Test
    @DisplayName("Residuo de centavos: queda para la plataforma y abono + comisión = subtotal exacto")
    void residuoDeCentavosQuedaParaLaPlataforma() {
        Pedido p = pedido("ped_r", "vend_r", "of_r", 333_333L, 333_333L);

        billeteraService.registrarRepartoVentas(compra(333_333L, 0L), List.of(p));

        long neto = almacen.retencion("vend_r");
        long comision = -almacen.deVendedor("vend_r").stream()
                .filter(m -> m.getTipo() == MovimientoBilletera.Tipo.COMISION).mapToLong(MovimientoBilletera::getMontoCentavos).sum();
        // 8 % exacto sería 26,666.64 -> el vendedor recibe el piso de su parte; la plataforma, el resto
        assertEquals(306_666L, neto);
        assertEquals(26_667L, comision);
        assertEquals(333_333L, neto + comision);
        assertEquals(306_666L, BilleteraService.calcularNeto(333_333L, 800));
    }

    @Test
    @DisplayName("La comisión es configurable (parámetro único) y vale 8 % por defecto")
    void comisionConfigurable() {
        assertEquals(800, billeteraService.comisionPuntosBasicos());

        when(configService.obtenerValorDouble(eq(BilleteraService.CLAVE_COMISION), anyDouble())).thenReturn(10.0);
        billeteraService.registrarRepartoVentas(compra(1_000_000L, 0L),
                List.of(pedido("ped_c", "vend_c", "of_c", 1_000_000L, 1_000_000L)));

        assertEquals(900_000L, almacen.retencion("vend_c")); // 10 %
    }

    // ───────── idempotencia ─────────

    @Test
    @DisplayName("Idempotencia: repetir el reparto no abona dos veces")
    void idempotenciaAnteReintento() {
        Pedido p1 = pedido("ped_i1", "vend_i1", "of_1", 1_000_000L, 1_000_000L);
        Pedido p2 = pedido("ped_i2", "vend_i2", "of_2", 500_000L, 1_000_000L);
        Compra c = compra(1_500_000L, 0L);

        billeteraService.registrarRepartoVentas(c, List.of(p1, p2));
        billeteraService.registrarRepartoVentas(c, List.of(p1, p2));
        billeteraService.registrarRepartoVentas(c, List.of(p1, p2));

        assertEquals(920_000L, almacen.retencion("vend_i1"));
        assertEquals(460_000L, almacen.retencion("vend_i2"));
        assertEquals(4, almacen.movimientos.size()); // abono + comisión por pedido, una sola vez
    }

    @Test
    @DisplayName("Idempotencia a través de aplicarResultado: webhook y consulta de respaldo no duplican el abono")
    void idempotenciaViaAplicarResultado() {
        long total = 1_500_000L;
        Pedido p1 = pedido("ped_a1", "vend_a1", "of_a1", 1_000_000L, total);
        Pedido p2 = pedido("ped_a2", "vend_a2", "of_a2", 500_000L, total);
        OfertaVendedor o1 = oferta("of_a1", 10.0);
        OfertaVendedor o2 = oferta("of_a2", 10.0);
        when(ofertaRepository.findById("of_a1")).thenReturn(Optional.of(o1));
        when(ofertaRepository.findById("of_a2")).thenReturn(Optional.of(o2));
        Compra c = compra(1_500_000L, 0L);
        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(c));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1, p2));

        PagoConfirmacionService svc = servicio();

        assertEquals(PagoConfirmacionService.Resultado.APLICADO, svc.aplicarResultado(REFERENCIA, "tx1", "APPROVED", total));
        // Reintento del webhook / consulta de respaldo
        assertEquals(PagoConfirmacionService.Resultado.YA_FINALIZADO, svc.aplicarResultado(REFERENCIA, "tx1", "APPROVED", total));

        assertEquals(920_000L, almacen.retencion("vend_a1"));
        assertEquals(460_000L, almacen.retencion("vend_a2"));
        assertEquals(4, almacen.movimientos.size());
        assertEquals("CONFIRMADO", p1.getEstado());
    }

    @Test
    @DisplayName("Reintento tras un fallo a medias: el abono ya hecho no se repite")
    void reintentoTrasFalloParcialNoDuplica() {
        long total = 1_000_000L;
        Pedido p = pedido("ped_f", "vend_f", "of_f", total, total);
        when(ofertaRepository.findById("of_f")).thenReturn(Optional.of(oferta("of_f", 5.0)));
        Compra c = compra(total, 0L);
        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(c));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p));
        // Primer intento: el guardado del pedido falla DESPUÉS del abono
        when(pedidoRepository.save(any(Pedido.class))).thenThrow(new RuntimeException("Mongo caído"))
                .thenAnswer(i -> i.getArgument(0));

        PagoConfirmacionService svc = servicio();
        assertThrows(RuntimeException.class, () -> svc.aplicarResultado(REFERENCIA, "tx", "APPROVED", total));
        long tras1 = almacen.retencion("vend_f");

        svc.aplicarResultado(REFERENCIA, "tx", "APPROVED", total);

        assertEquals(920_000L, tras1);
        assertEquals(920_000L, almacen.retencion("vend_f")); // no se abonó otra vez
        assertEquals(2, almacen.movimientos.size());
    }

    // ───────── pago fallido / pedidos en revisión ─────────

    @Test
    @DisplayName("Pago fallido: no se registra ningún movimiento ni se crea billetera")
    void pagoFallidoNoRegistraNada() {
        long total = 1_500_000L;
        Pedido p1 = pedido("ped_x1", "vend_x1", "of_1", 1_000_000L, total);
        Pedido p2 = pedido("ped_x2", "vend_x2", "of_2", 500_000L, total);
        Compra c = compra(1_500_000L, 0L);
        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(c));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(p1, p2));

        PagoConfirmacionService svc = servicio();
        svc.aplicarResultado(REFERENCIA, "tx", "DECLINED", total);

        assertTrue(almacen.movimientos.isEmpty());
        assertTrue(almacen.entidades.isEmpty());
        assertEquals("CANCELADO", p1.getEstado());
    }

    @Test
    @DisplayName("Un sub-pedido en revisión por stock no se abona; los demás sí")
    void pedidoEnRevisionNoSeAbona() {
        long total = 1_500_000L;
        Pedido ok = pedido("ped_ok", "vend_ok", "of_ok", 1_000_000L, total);
        Pedido sinStock = pedido("ped_ss", "vend_ss", "of_ss", 500_000L, total);
        sinStock.getItems().get(0).setCantidad(5.0);
        when(ofertaRepository.findById("of_ok")).thenReturn(Optional.of(oferta("of_ok", 10.0)));
        when(ofertaRepository.findById("of_ss")).thenReturn(Optional.of(oferta("of_ss", 1.0)));
        Compra c = compra(1_500_000L, 0L);
        when(compraRepository.findByReferenciaPago(REFERENCIA)).thenReturn(Optional.of(c));
        when(pedidoRepository.findByPago_Referencia(REFERENCIA)).thenReturn(List.of(ok, sinStock));

        servicio().aplicarResultado(REFERENCIA, "tx", "APPROVED", total);

        assertEquals(920_000L, almacen.retencion("vend_ok"));
        assertTrue(almacen.deVendedor("vend_ss").isEmpty());
        assertEquals(Pedido.EN_REVISION, sinStock.getEstado());
    }

    // ───────── inmutabilidad ─────────

    @Test
    @DisplayName("Inmutabilidad: el movimiento no tiene setters y todos sus campos son final")
    void movimientoEsInmutable() {
        for (Field f : MovimientoBilletera.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            assertTrue(Modifier.isFinal(f.getModifiers()), "Campo no final: " + f.getName());
        }
        for (Method m : MovimientoBilletera.class.getDeclaredMethods()) {
            assertFalse(m.getName().startsWith("set"), "No debe haber setters: " + m.getName());
        }
        assertTrue(Modifier.isFinal(MovimientoBilletera.class.getModifiers()));
    }

    @Test
    @DisplayName("Inmutabilidad: ni el repositorio ni el puerto exponen guardar/editar/borrar movimientos")
    void noExistenOperacionesDeEdicionNiBorrado() {
        assertFalse(CrudRepository.class.isAssignableFrom(com.example.demo.repository.MovimientoBilleteraRepository.class));
        assertFalse(org.springframework.data.mongodb.repository.MongoRepository.class
                .isAssignableFrom(com.example.demo.repository.MovimientoBilleteraRepository.class));
        assertFalse(CrudRepository.class.isAssignableFrom(com.example.demo.repository.BilleteraRepository.class));

        List<String> prohibidos = List.of("save", "delete", "update", "remove", "edit");
        for (Class<?> tipo : List.of(com.example.demo.repository.MovimientoBilleteraRepository.class, BilleteraAlmacen.class)) {
            for (Method m : tipo.getMethods()) {
                String n = m.getName().toLowerCase();
                for (String p : prohibidos) {
                    assertFalse(n.contains(p), tipo.getSimpleName() + " expone " + m.getName());
                }
            }
        }
    }

    @Test
    @DisplayName("Inmutabilidad: ningún endpoint de billetera acepta POST/PUT/PATCH/DELETE")
    void endpointsDeBilleteraSoloLectura() {
        for (Method m : com.example.demo.Controller.VendedorController.class.getDeclaredMethods()) {
            boolean esBilletera = m.getName().toLowerCase().contains("billetera")
                    || m.getName().toLowerCase().contains("movimiento");
            if (!esBilletera) continue;
            assertNotNull(m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class),
                    "El endpoint " + m.getName() + " debe ser solo GET");
            assertNull(m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class));
            assertNull(m.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class));
            assertNull(m.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class));
            assertNull(m.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class));
        }
    }

    // ───────── helpers ─────────

    private OfertaVendedor oferta(String id, double stock) {
        OfertaVendedor o = new OfertaVendedor();
        o.setId(id);
        o.setStock(stock);
        o.setDisponible(true);
        return o;
    }

    private PagoConfirmacionService servicio() {
        when(ofertaRepository.save(any(OfertaVendedor.class))).thenAnswer(i -> i.getArgument(0));
        PagoConfirmacionService svc = new PagoConfirmacionService(
                pedidoRepository, ofertaRepository, null, null, null, compraRepository);
        svc.setBilleteraService(billeteraService);
        return svc;
    }
}
