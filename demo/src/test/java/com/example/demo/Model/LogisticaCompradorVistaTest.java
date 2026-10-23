package com.example.demo.Model;

import com.example.demo.Model.embebidos.SnapshotTransportista;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LogisticaCompradorVistaTest {

    @Test
    void independienteMuestraNombreTelefonoYVehiculo() {
        SnapshotTransportista t = new SnapshotTransportista("1", "Carlos", "3101234567", "INDEPENDIENTE", "ABC123", null, "Moto");
        assertEquals("Carlos", t.getNombreMostrar());
        assertEquals("3101234567", t.getTelefonoLimpio());
        assertEquals("Moto ABC123", t.getVehiculoMostrar());
    }

    @Test
    void empresaMuestraRazonSocialConConductor() {
        SnapshotTransportista t = new SnapshotTransportista("1", "Ana", "300", "EMPRESA", "XYZ987", "Logistica SAS", "Camion");
        assertEquals("Logistica SAS (Ana)", t.getNombreMostrar());
        t.setNombre(null);
        assertEquals("Logistica SAS", t.getNombreMostrar());
    }

    @Test
    void snapshotIncompletoNuncaDevuelveNullTexto() {
        SnapshotTransportista t = new SnapshotTransportista("1", null, " ", null, "null", null, null);
        assertNull(t.getNombreMostrar());
        assertNull(t.getTelefonoLimpio());
        assertNull(t.getVehiculoMostrar());
        t.setPlaca("ABC123");
        assertEquals("ABC123", t.getVehiculoMostrar());
    }

    private Compra compra(String estadoCompra, String estadoEnvio, String... estadosSub) {
        Compra c = new Compra();
        c.setEstado(estadoCompra);
        if (estadoEnvio != null) { Envio e = new Envio(); e.setEstado(estadoEnvio); c.setEnvioLogistica(e); }
        java.util.ArrayList<Pedido> subs = new java.util.ArrayList<>();
        for (String s : estadosSub) { Pedido p = new Pedido(); p.setEstado(s); subs.add(p); }
        c.setSubPedidos(subs);
        return c;
    }

    @Test
    void filtrosPorPestana() {
        assertEquals("PENDIENTE", compra(Compra.PENDIENTE, null, "PENDIENTE").getEstadoFiltro());
        assertEquals("PROCESO", compra(Compra.PAGADA, Envio.ASIGNADO, "LISTO_PARA_RECOGER").getEstadoFiltro());
        assertEquals("PROCESO", compra(Compra.PAGADA, null, "CONFIRMADO").getEstadoFiltro());
        assertEquals("ENTREGADO", compra(Compra.PAGADA, Envio.ENTREGADO, "ENVIADO").getEstadoFiltro());
        assertEquals("ENTREGADO", compra(Compra.PAGADA, null, "ENTREGADO", "ENTREGADO").getEstadoFiltro());
        assertEquals("PROCESO", compra(Compra.PAGADA, Envio.EN_ENTREGA, "ENTREGADO", "ENVIADO").getEstadoFiltro());
    }
}
