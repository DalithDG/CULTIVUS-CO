package com.example.demo.Model;

import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.DireccionPedido;
import com.example.demo.Model.embebidos.ProductoPedido;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "pedidos")
public class Pedido {

    // Constantes de compatibilidad (nombres canónicos de EstadoPedido). La lógica de negocio debe
    // usar el enum EstadoPedido y PedidoEstadoService; estas constantes solo existen para API
    // pública/tests históricos y para expresiones `case` en switch de strings.
    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONFIRMADO = "CONFIRMADO";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String LISTO_PARA_RECOGER = "LISTO_PARA_RECOGER";
    public static final String ENVIADO = "ENVIADO";
    public static final String ENTREGADO = "ENTREGADO";
    public static final String CANCELADO = "CANCELADO";
    public static final String EN_REVISION = "EN_REVISION";

    @Id
    private String id;

    /** Bloqueo optimista: dos actores no pueden pisarse el cambio de estado. */
    @Version
    @Field("version")
    private Long version;

    // US-21: compra (pago único) a la que pertenece este sub-pedido. Null en pedidos antiguos.
    @Indexed
    @Field("compra_id")
    private String compraId;

    @Field("vendedor")
    private DatosVendedor vendedor;

    // Ver EstadoPedido. Se persiste siempre el nombre canónico (los legados PROCESANDO,
    // COMPLETADO y PENDIENTE_PAGO se normalizan al leer y al escribir).
    @Field("estado")
    private String estado = EstadoPedido.PENDIENTE.name();

    /** Modalidad heredada de la compra (snapshot). Null en pedidos antiguos = DOMICILIO. */
    @Field("modalidad_entrega")
    private ModalidadEntrega modalidadEntrega;

    // Subtotal de productos de este sub-pedido en centavos (base para abonar al vendedor en US-22)
    @Field("subtotal_centavos")
    private Long subtotalCentavos;

    // Desglose de costo de envío asignado a este sub-pedido en centavos
    @Field("envio_centavos")
    private Long envioCentavos;

    // Snapshot del tramo de envío (US: costo por zona)
    @Field("ciudad_origen_envio")
    private String ciudadOrigenEnvio;

    @Field("ciudad_destino_envio")
    private String ciudadDestinoEnvio;

    @Field("zona_origen_envio")
    private Integer zonaOrigenEnvio;

    @Field("zona_destino_envio")
    private Integer zonaDestinoEnvio;

    @Field("tarifa_envio_centavos")
    private Long tarifaEnvioCentavos;

    @Field("requiere_revision")
    private boolean requiereRevision;

    @Field("motivo_revision")
    private String motivoRevision;

    // ── Cancelación ──
    @Field("cancelado_por")
    private CanceladoPor canceladoPor;

    @Field("motivo_cancelacion")
    private String motivoCancelacion;

    @Field("fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    // ── Stock y reembolso ──
    /** true si al confirmar el pago se descontó stock de las ofertas. Null = pedido legado (se infiere). */
    @Field("stock_descontado")
    private Boolean stockDescontado;

    /** Pagado pero cancelado: el reembolso al comprador es manual y sigue pendiente. */
    @Field("requiere_reembolso")
    private boolean requiereReembolso;

    @Field("monto_reembolso_centavos")
    private Long montoReembolsoCentavos;

    @Field("reembolso_realizado_en")
    private LocalDateTime reembolsoRealizadoEn;

    // ── Recogida en tienda (código de retiro) ──
    /** Código de 6 dígitos. SOLO lo ve el comprador: nunca debe llegar a vistas ni notificaciones del vendedor. */
    @Field("codigo_retiro")
    private String codigoRetiro;

    @Field("retiro_intentos_fallidos")
    private int retiroIntentosFallidos;

    @Field("retiro_bloqueado")
    private boolean retiroBloqueado;

    @Field("fecha_listo_para_recoger")
    private LocalDateTime fechaListoParaRecoger;

    @Field("recordatorio_retiro_enviado")
    private boolean recordatorioRetiroEnviado;

    @Field("entrega_manual")
    private boolean entregaManual;

    @Field("motivo_entrega_manual")
    private String motivoEntregaManual;

    @Field("entrega_manual_admin_id")
    private String entregaManualAdminId;

    @Field("fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Field("fecha_pedido")
    private LocalDateTime fechaPedido;

    @Field("total")
    private Double total;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    // Comprador embebido — snapshot del usuario al momento del pedido
    @Field("comprador")
    private DatosComprador comprador;

    // Dirección de entrega — snapshot al momento del pedido (corrige ERR-03)
    @Field("direccion_entrega")
    private DireccionPedido direccionEntrega;

    // Items embebidos — snapshots históricos de precio y nombre
    @Field("items")
    private List<ProductoPedido> items = new ArrayList<>();

    // Pago embebido — corrige ERR-02 (ya no es 1:1 con UNIQUE)
    @Field("pago")
    private DatosPago pago;

    // Constructores
    public Pedido() {
        this.fechaPedido = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.estado = EstadoPedido.PENDIENTE.name();
        this.items = new ArrayList<>();
    }

    public Pedido(DatosComprador comprador, DatosVendedor vendedor, DireccionPedido direccionEntrega,
                   List<ProductoPedido> items, DatosPago pago) {
        this.comprador = comprador;
        this.vendedor = vendedor;
        this.direccionEntrega = direccionEntrega;
        this.items = items;
        this.pago = pago;
        this.estado = EstadoPedido.PENDIENTE.name();
        this.fechaPedido = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.total = calcularTotal();
    }

    // Método auxiliar para calcular el total
    public Double calcularTotal() {
        if (items == null) return 0.0;
        return items.stream()
                .mapToDouble(i -> i != null && i.getSubtotal() != null ? i.getSubtotal() : 0.0)
                .sum();
    }

    /**
     * Retorna un número de factura legible basado en el ID.
     * Ejemplo: CLT-1A2B3C
     */
    public String getNumeroFactura() {
        if (id == null) return "PENDIENTE";
        String suffix = id.length() > 6 ? id.substring(id.length() - 6) : id;
        return "CLT-" + suffix.toUpperCase();
    }

    // ── Estado (enum + helpers de vista) ──

    /** Estado como enum (legados ya normalizados). Null solo si el valor persistido es irreconocible. */
    public EstadoPedido getEstadoEnum() {
        return EstadoPedido.interpretar(estado).orElse(null);
    }

    public void setEstadoEnum(EstadoPedido nuevo) {
        this.estado = nuevo != null ? nuevo.name() : EstadoPedido.PENDIENTE.name();
    }

    public boolean esEstado(EstadoPedido e) {
        return getEstadoEnum() == e;
    }

    public boolean isPendiente() { return esEstado(EstadoPedido.PENDIENTE); }
    public boolean isConfirmado() { return esEstado(EstadoPedido.CONFIRMADO); }
    public boolean isEnProceso() { return esEstado(EstadoPedido.EN_PROCESO); }
    public boolean isListoParaRecoger() { return esEstado(EstadoPedido.LISTO_PARA_RECOGER); }
    public boolean isEnviado() { return esEstado(EstadoPedido.ENVIADO); }
    public boolean isEntregado() { return esEstado(EstadoPedido.ENTREGADO); }
    public boolean isCancelado() { return esEstado(EstadoPedido.CANCELADO); }
    public boolean isEnRevision() { return esEstado(EstadoPedido.EN_REVISION); }

    /** Normaliza un estado legado antes de persistir (lo invoca el callback BeforeConvert). */
    public void normalizarEstadoLegado() {
        this.estado = EstadoPedido.normalizar(this.estado);
    }

    // ── Modalidad ──

    public ModalidadEntrega getModalidadEntrega() {
        return ModalidadEntrega.DOMICILIO;
    }

    public void setModalidadEntrega(ModalidadEntrega modalidadEntrega) {
        this.modalidadEntrega = ModalidadEntrega.DOMICILIO;
    }

    public boolean isRecogeEnTienda() {
        return false;
    }

    public boolean isDomicilio() {
        return true;
    }

    /** Etiqueta del estado para vendedor/admin/logística. */
    public String getEstadoEtiqueta() {
        EstadoPedido e = getEstadoEnum();
        return e != null ? e.etiqueta() : (estado != null ? estado : EstadoPedido.PENDIENTE.etiqueta());
    }

    /** Etiqueta del estado para las vistas del comprador. */
    public String getEstadoEtiquetaComprador() {
        return getEstadoEtiqueta();
    }

    /** Sufijo CSS del estado: pendiente, confirmado, proceso, listo, enviado, completado, cancelado, revision. */
    public String getEstadoCss() {
        EstadoPedido e = getEstadoEnum();
        return e != null ? e.css() : EstadoPedido.PENDIENTE.css();
    }

    /** Etiquetas de los pasos del stepper de tracking del comprador (envío a domicilio). */
    public List<String> getEtiquetasPasos() {
        return List.of("Pago", "Confirmado", "Preparación", "Listo para recoger", "Enviado", "Entregado");
    }

    public int getTotalPasos() {
        return getEtiquetasPasos().size();
    }

    /**
     * Paso actual (1..N) del stepper para entrega a domicilio.
     */
    public int getPasoTracking() {
        EstadoPedido e = getEstadoEnum();
        if (e == null) return 1;
        switch (e) {
            case PENDIENTE: return 1;
            case CONFIRMADO:
            case EN_REVISION: return 2;
            case EN_PROCESO: return 3;
            case LISTO_PARA_RECOGER: return 4;
            case ENVIADO: return 5;
            case ENTREGADO: return 6;
            case CANCELADO:
            default: return 0;
        }
    }

    /** false si el pedido está cancelado: se oculta el stepper y se muestra el banner de cancelación. */
    public boolean isMostrarStepper() {
        return !isCancelado();
    }

    // ── Stock / secretos ──

    /**
     * ¿Este pedido descontó stock? Si el flag no está (pedido legado), se infiere: solo los estados
     * CONFIRMADO..ENTREGADO lo descontaron (EN_REVISION y los de pago fallido nunca).
     */
    public boolean isStockDescontado() {
        if (stockDescontado != null) return stockDescontado;
        EstadoPedido e = getEstadoEnum();
        return e != null && e.esVentaConfirmada();
    }

    public Boolean getStockDescontadoRaw() { return stockDescontado; }
    public void setStockDescontado(Boolean stockDescontado) { this.stockDescontado = stockDescontado; }

    public boolean isTieneCodigoRetiro() { return codigoRetiro != null && !codigoRetiro.isBlank(); }

    /** El vendedor nunca debe conocer el código de retiro: se limpia en la instancia que se le entrega. */
    public void ocultarSecretosParaVendedor() {
        this.codigoRetiro = null;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getCompraId() { return compraId; }
    public void setCompraId(String compraId) { this.compraId = compraId; }

    public Long getSubtotalCentavos() { return subtotalCentavos; }
    public void setSubtotalCentavos(Long subtotalCentavos) { this.subtotalCentavos = subtotalCentavos; }

    /** Retorna el subtotal de productos en pesos (sin envío), calculando desde subtotalCentavos o items. */
    public double getSubtotalProductos() {
        if (subtotalCentavos != null) {
            return subtotalCentavos / 100.0;
        }
        if (items != null && !items.isEmpty()) {
            return items.stream()
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(i -> i.getSubtotal() != null ? i.getSubtotal() : 0.0)
                    .sum();
        }
        return total != null ? total : 0.0;
    }

    public Long getEnvioCentavos() { return envioCentavos; }
    public void setEnvioCentavos(Long envioCentavos) { this.envioCentavos = envioCentavos; }

    public double getEnvio() {
        return envioCentavos != null ? envioCentavos / 100.0 : 0.0;
    }

    public String getCiudadOrigenEnvio() { return ciudadOrigenEnvio; }
    public void setCiudadOrigenEnvio(String ciudadOrigenEnvio) { this.ciudadOrigenEnvio = ciudadOrigenEnvio; }

    public String getCiudadDestinoEnvio() { return ciudadDestinoEnvio; }
    public void setCiudadDestinoEnvio(String ciudadDestinoEnvio) { this.ciudadDestinoEnvio = ciudadDestinoEnvio; }

    public Integer getZonaOrigenEnvio() { return zonaOrigenEnvio; }
    public void setZonaOrigenEnvio(Integer zonaOrigenEnvio) { this.zonaOrigenEnvio = zonaOrigenEnvio; }

    public Integer getZonaDestinoEnvio() { return zonaDestinoEnvio; }
    public void setZonaDestinoEnvio(Integer zonaDestinoEnvio) { this.zonaDestinoEnvio = zonaDestinoEnvio; }

    public Long getTarifaEnvioCentavos() { return tarifaEnvioCentavos; }
    public void setTarifaEnvioCentavos(Long tarifaEnvioCentavos) { this.tarifaEnvioCentavos = tarifaEnvioCentavos; }

    public boolean isRequiereRevision() { return requiereRevision; }
    public void setRequiereRevision(boolean requiereRevision) { this.requiereRevision = requiereRevision; }

    public String getMotivoRevision() { return motivoRevision; }
    public void setMotivoRevision(String motivoRevision) { this.motivoRevision = motivoRevision; }

    public CanceladoPor getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(CanceladoPor canceladoPor) { this.canceladoPor = canceladoPor; }

    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }

    public LocalDateTime getFechaCancelacion() { return fechaCancelacion; }
    public void setFechaCancelacion(LocalDateTime fechaCancelacion) { this.fechaCancelacion = fechaCancelacion; }

    public boolean isRequiereReembolso() { return requiereReembolso; }
    public void setRequiereReembolso(boolean requiereReembolso) { this.requiereReembolso = requiereReembolso; }

    public Long getMontoReembolsoCentavos() { return montoReembolsoCentavos; }
    public void setMontoReembolsoCentavos(Long montoReembolsoCentavos) { this.montoReembolsoCentavos = montoReembolsoCentavos; }

    public LocalDateTime getReembolsoRealizadoEn() { return reembolsoRealizadoEn; }
    public void setReembolsoRealizadoEn(LocalDateTime reembolsoRealizadoEn) { this.reembolsoRealizadoEn = reembolsoRealizadoEn; }

    public String getCodigoRetiro() { return codigoRetiro; }
    public void setCodigoRetiro(String codigoRetiro) { this.codigoRetiro = codigoRetiro; }

    public int getRetiroIntentosFallidos() { return retiroIntentosFallidos; }
    public void setRetiroIntentosFallidos(int retiroIntentosFallidos) { this.retiroIntentosFallidos = retiroIntentosFallidos; }

    public boolean isRetiroBloqueado() { return retiroBloqueado; }
    public void setRetiroBloqueado(boolean retiroBloqueado) { this.retiroBloqueado = retiroBloqueado; }

    public LocalDateTime getFechaListoParaRecoger() { return fechaListoParaRecoger; }
    public void setFechaListoParaRecoger(LocalDateTime fechaListoParaRecoger) { this.fechaListoParaRecoger = fechaListoParaRecoger; }

    public boolean isRecordatorioRetiroEnviado() { return recordatorioRetiroEnviado; }
    public void setRecordatorioRetiroEnviado(boolean recordatorioRetiroEnviado) { this.recordatorioRetiroEnviado = recordatorioRetiroEnviado; }

    public boolean isEntregaManual() { return entregaManual; }
    public void setEntregaManual(boolean entregaManual) { this.entregaManual = entregaManual; }

    public String getMotivoEntregaManual() { return motivoEntregaManual; }
    public void setMotivoEntregaManual(String motivoEntregaManual) { this.motivoEntregaManual = motivoEntregaManual; }

    public String getEntregaManualAdminId() { return entregaManualAdminId; }
    public void setEntregaManualAdminId(String entregaManualAdminId) { this.entregaManualAdminId = entregaManualAdminId; }

    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDateTime fechaEntrega) { this.fechaEntrega = fechaEntrega; }

    public DatosVendedor getVendedor() { return vendedor; }
    public void setVendedor(DatosVendedor vendedor) { this.vendedor = vendedor; }

    /** Estado canónico (los legados se devuelven ya normalizados). */
    public String getEstado() { return EstadoPedido.normalizar(estado); }
    /** Normaliza legados al escribir: nunca se vuelve a persistir PROCESANDO/COMPLETADO/PENDIENTE_PAGO. */
    public void setEstado(String estado) { this.estado = EstadoPedido.normalizar(estado); }

    public LocalDateTime getFechaPedido() { return fechaPedido; }
    public void setFechaPedido(LocalDateTime fechaPedido) { this.fechaPedido = fechaPedido; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public DatosComprador getComprador() { return comprador; }
    public void setComprador(DatosComprador comprador) { this.comprador = comprador; }

    public DireccionPedido getDireccionEntrega() { return direccionEntrega; }
    public void setDireccionEntrega(DireccionPedido direccionEntrega) { this.direccionEntrega = direccionEntrega; }

    public List<ProductoPedido> getItems() {
        if (items == null) items = new ArrayList<>();
        return items;
    }
    public void setItems(List<ProductoPedido> items) {
        this.items = items != null ? items : new ArrayList<>();
        this.total = calcularTotal();
    }

    public DatosPago getPago() { return pago; }
    public void setPago(DatosPago pago) { this.pago = pago; }
}