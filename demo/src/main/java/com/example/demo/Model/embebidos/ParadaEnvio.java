package com.example.demo.Model.embebidos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parada de recogida asociada a un sub-pedido dentro de la ruta del envío.
 */
public class ParadaEnvio {

    private String pedidoId;
    private String vendedorId;
    private String nombreVendedor;
    private String direccionRecogida;
    private String ciudadRecogida;
    private String vendedorTelefono;
    private Integer zona;
    private Long tarifaCentavos;
    private boolean recogido = false;
    private LocalDateTime fechaRecogida;

    // Resumen de productos del pedido para visualización rápida en las rutas disponibles
    private List<String> itemsResumen = new ArrayList<>();

    public ParadaEnvio() {
    }

    public ParadaEnvio(String pedidoId, String vendedorId, String nombreVendedor,
                       String direccionRecogida, String ciudadRecogida, Integer zona,
                       Long tarifaCentavos) {
        this.pedidoId = pedidoId;
        this.vendedorId = vendedorId;
        this.nombreVendedor = nombreVendedor;
        this.direccionRecogida = direccionRecogida;
        this.ciudadRecogida = ciudadRecogida;
        this.zona = zona;
        this.tarifaCentavos = tarifaCentavos;
        this.recogido = false;
    }

    public double getTarifaCop() {
        return tarifaCentavos != null ? tarifaCentavos / 100.0 : 0.0;
    }

    public String getTarifaFormateada() {
        long centavos = tarifaCentavos != null ? tarifaCentavos : 0L;
        return String.format(java.util.Locale.GERMANY, "$%,d COP", centavos / 100);
    }

    public String getPedidoId() { return pedidoId; }
    public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }

    public String getVendedorId() { return vendedorId; }
    public void setVendedorId(String vendedorId) { this.vendedorId = vendedorId; }

    public String getNombreVendedor() { return nombreVendedor; }
    public void setNombreVendedor(String nombreVendedor) { this.nombreVendedor = nombreVendedor; }
    public String getVendedorNombre() { return nombreVendedor; }
    public void setVendedorNombre(String vendedorNombre) { this.nombreVendedor = vendedorNombre; }

    public String getDireccionRecogida() { return direccionRecogida; }
    public void setDireccionRecogida(String direccionRecogida) { this.direccionRecogida = direccionRecogida; }
    public String getDireccionRecoleccion() { return direccionRecogida; }
    public void setDireccionRecoleccion(String direccionRecoleccion) { this.direccionRecogida = direccionRecoleccion; }

    public String getCiudadRecogida() { return ciudadRecogida; }
    public void setCiudadRecogida(String ciudadRecogida) { this.ciudadRecogida = ciudadRecogida; }
    public String getCiudad() { return ciudadRecogida; }
    public void setCiudad(String ciudad) { this.ciudadRecogida = ciudad; }

    public String getVendedorTelefono() { return vendedorTelefono; }
    public void setVendedorTelefono(String vendedorTelefono) { this.vendedorTelefono = vendedorTelefono; }

    public Integer getZona() { return zona; }
    public void setZona(Integer zona) { this.zona = zona; }

    public Long getTarifaCentavos() { return tarifaCentavos; }
    public void setTarifaCentavos(Long tarifaCentavos) { this.tarifaCentavos = tarifaCentavos; }

    public boolean isRecogido() { return recogido; }
    public void setRecogido(boolean recogido) { this.recogido = recogido; }
    public boolean isRecolectado() { return recogido; }
    public void setRecolectado(boolean recolectado) { this.recogido = recolectado; }

    public LocalDateTime getFechaRecogida() { return fechaRecogida; }
    public void setFechaRecogida(LocalDateTime fechaRecogida) { this.fechaRecogida = fechaRecogida; }

    public List<String> getItemsResumen() {
        if (itemsResumen == null) itemsResumen = new ArrayList<>();
        return itemsResumen;
    }
    public void setItemsResumen(List<String> itemsResumen) { this.itemsResumen = itemsResumen; }
}
