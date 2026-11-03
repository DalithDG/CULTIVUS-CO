package com.example.demo.services;

import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.embebidos.UnidadMedida;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio para gestionar las ofertas de los vendedores.
 *
 * <p>US-06: toda validación de cantidades usa la unidad guardada en la OFERTA
 * ({@link OfertaVendedor#getUnidadMedida()}), nunca la del catálogo.</p>
 */
@Service
public class OfertaService {

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private ProductoCatalogoRepository catalogoRepository;

    @Autowired
    private CatalogoService catalogoService;

    @Autowired
    private AppConfigService configService;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private NotificacionService notificacionService;

    // ══════════════════════════════════════════════════════════
    // CRUD de ofertas
    // ══════════════════════════════════════════════════════════

    /**
     * Crea una nueva oferta.
     * <ul>
     *   <li>Producto de la lista (aprobado y activo): la oferta se publica de inmediato.</li>
     *   <li>Oferta con estado PENDIENTE (producto sugerido): se guarda no visible al público.</li>
     * </ul>
     */
    public OfertaVendedor crearOferta(OfertaVendedor oferta) {
        // Validaciones
        if (oferta.getProductoCatalogoId() == null || oferta.getProductoCatalogoId().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar un producto");
        }

        ProductoCatalogo producto = catalogoRepository.findById(oferta.getProductoCatalogoId())
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        boolean pendiente = oferta.getEstadoPublicacion() == EstadoPublicacion.PENDIENTE;
        if (pendiente) {
            if (producto.isAprobado() || !producto.isActivo()) {
                throw new IllegalArgumentException("El producto sugerido no está pendiente de aprobación");
            }
        } else if (!producto.isAprobado() || !producto.isActivo()) {
            throw new IllegalArgumentException("El producto no está disponible");
        }

        if (oferta.getVendedor() == null || oferta.getVendedor().getId() == null) {
            throw new IllegalArgumentException("La oferta debe tener un vendedor asociado");
        }

        // La unidad debe ser una de las permitidas por el producto (se guarda una copia en la oferta)
        UnidadMedida unidad = oferta.getUnidadMedida();
        if (unidad == null || unidad.getAbreviatura() == null) {
            throw new IllegalArgumentException("Elige la unidad de venta.");
        }
        UnidadMedida permitida = producto.buscarUnidadPermitida(unidad.getAbreviatura());
        if (permitida == null) {
            throw new IllegalArgumentException("La unidad '" + unidad.getAbreviatura()
                    + "' no está permitida para " + producto.getNombre() + ".");
        }
        oferta.setUnidadMedida(permitida.copia());

        // Verificar que el vendedor no tenga ya una oferta para este producto
        if (ofertaRepository.existsByProductoCatalogoIdAndVendedor_Id(
                oferta.getProductoCatalogoId(), oferta.getVendedor().getId())) {
            throw new IllegalArgumentException("Ya tienes una oferta para este producto. Puedes editarla desde 'Mis Ofertas'.");
        }

        // Validar límite de ofertas por vendedor
        int maxOfertas = configService.obtenerValorInt("MAX_PRODUCTOS_VENDEDOR", 50);
        long ofertasActuales = ofertaRepository.findByVendedor_Id(oferta.getVendedor().getId()).size();
        if (ofertasActuales >= maxOfertas) {
            throw new IllegalArgumentException("Has alcanzado el límite máximo de " + maxOfertas + " ofertas permitidas.");
        }

        validarDatosOferta(oferta);
        asegurarPeso(oferta);

        // Guardar nombre del producto desnormalizado
        oferta.setNombreProducto(producto.getNombre());
        oferta.setDisponible(!pendiente);
        oferta.setEstadoPublicacion(pendiente ? EstadoPublicacion.PENDIENTE : EstadoPublicacion.PUBLICADO);
        oferta.setMotivoRechazo(null);
        oferta.setCreatedAt(LocalDateTime.now());

        OfertaVendedor ofertaGuardada = ofertaRepository.save(oferta);

        // Actualizar estadísticas del producto del catálogo inmediatamente
        catalogoService.actualizarEstadisticas(oferta.getProductoCatalogoId());

        return ofertaGuardada;
    }

    /**
     * "No encuentro mi producto": valida la oferta, guarda el producto sugerido (no aprobado) y la
     * oferta en estado PENDIENTE ligada a él. Notifica a los administradores.
     */
    public OfertaVendedor sugerirProductoConOferta(ProductoCatalogo sugerido, OfertaVendedor oferta, String vendedorId) {
        if (sugerido.getUnidadPorDefecto() == null) {
            throw new IllegalArgumentException("Elige la unidad de venta del producto.");
        }
        oferta.setUnidadMedida(sugerido.getUnidadPorDefecto().copia());
        // Validar la oferta ANTES de guardar la sugerencia, para no dejar sugerencias huérfanas
        validarDatosOferta(oferta);

        ProductoCatalogo guardado = catalogoService.sugerirProducto(sugerido, vendedorId);
        try {
            oferta.setProductoCatalogoId(guardado.getId());
            oferta.setEstadoPublicacion(EstadoPublicacion.PENDIENTE);
            return crearOferta(oferta);
        } catch (RuntimeException e) {
            catalogoRepository.delete(guardado); // compensación: no dejar la sugerencia sin oferta
            throw e;
        }
    }

    /**
     * Actualiza una oferta existente. Precio, stock y descripción siempre son editables;
     * la unidad solo cambia si no existen pedidos ni carritos con esa oferta.
     */
    public OfertaVendedor actualizarOferta(OfertaVendedor oferta) {
        if (oferta.getId() == null || oferta.getId().isEmpty()) {
            throw new IllegalArgumentException("ID de oferta inválido");
        }
        OfertaVendedor actual = ofertaRepository.findById(oferta.getId())
                .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));

        UnidadMedida unidadActual = actual.getUnidadMedida();
        UnidadMedida unidadNueva = oferta.getUnidadMedida();
        if (unidadNueva == null) {
            oferta.setUnidadMedida(unidadActual != null ? unidadActual : UnidadMedida.porDefecto());
        } else if (unidadActual != null && !mismaUnidad(unidadActual, unidadNueva) && unidadBloqueada(oferta.getId())) {
            throw new IllegalArgumentException(
                    "No se puede cambiar la unidad porque ya hay pedidos o carritos con esta oferta.");
        }

        validarDatosOferta(oferta);
        asegurarPeso(oferta);

        oferta.setUpdatedAt(LocalDateTime.now());
        OfertaVendedor ofertaActualizada = ofertaRepository.save(oferta);

        // Recalcular estadísticas del catálogo
        catalogoService.actualizarEstadisticas(oferta.getProductoCatalogoId());

        return ofertaActualizada;
    }

    /**
     * Corrige y reenvía una oferta RECHAZADA: actualiza nombre y unidad de la sugerencia,
     * vuelve a PENDIENTE y avisa a los administradores.
     */
    public OfertaVendedor reenviarOferta(String ofertaId, String vendedorId, String nuevoNombre, UnidadMedida nuevaUnidad) {
        OfertaVendedor oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));
        if (oferta.getVendedor() == null || !vendedorId.equals(oferta.getVendedor().getId())) {
            throw new IllegalArgumentException("No tienes permiso para modificar esta oferta");
        }
        if (oferta.getEstadoPublicacion() != EstadoPublicacion.RECHAZADO) {
            throw new IllegalArgumentException("Solo se pueden reenviar ofertas rechazadas.");
        }
        ProductoCatalogo producto = catalogoRepository.findById(oferta.getProductoCatalogoId())
                .orElseThrow(() -> new IllegalArgumentException("La sugerencia ya no existe; crea una nueva."));
        if (producto.isAprobado()) {
            throw new IllegalArgumentException("El producto ya fue aprobado.");
        }
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw new IllegalArgumentException("Escribe el nombre del producto.");
        }
        String nombreNorm = ProductoCatalogo.normalizarNombre(nuevoNombre);
        catalogoRepository.findByNombreNormalizado(nombreNorm).ifPresent(otro -> {
            if (!otro.getId().equals(producto.getId())) {
                throw new IllegalArgumentException("Ya existe un producto llamado '" + otro.getNombre()
                        + "'. Elígelo en la lista al crear una oferta.");
            }
        });
        if (nuevaUnidad == null) {
            throw new IllegalArgumentException("Elige la unidad de venta.");
        }

        oferta.setUnidadMedida(nuevaUnidad.copia());
        validarDatosOferta(oferta); // stock y mínimo deben seguir siendo coherentes con la nueva unidad

        producto.setNombre(nuevoNombre.trim());
        producto.definirUnidades(nuevaUnidad, List.of(nuevaUnidad));
        producto.setActivo(true);
        producto.setAprobado(false);
        producto.setUpdatedAt(LocalDateTime.now());
        catalogoRepository.save(producto);

        oferta.setNombreProducto(producto.getNombre());
        oferta.setEstadoPublicacion(EstadoPublicacion.PENDIENTE);
        oferta.setMotivoRechazo(null);
        oferta.setDisponible(false);
        oferta.setUpdatedAt(LocalDateTime.now());
        OfertaVendedor guardada = ofertaRepository.save(oferta);

        notificacionService.enviarAAdmins(
                "Sugerencia corregida",
                "Un vendedor corrigió y reenvió la sugerencia '" + producto.getNombre() + "'. Revisa el panel de moderación.",
                "INFO");
        return guardada;
    }

    /** true si hay pedidos o carritos con esta oferta (la unidad queda bloqueada). */
    public boolean unidadBloqueada(String ofertaId) {
        if (ofertaId == null) return false;
        return carritoRepository.existsByItems_OfertaId(ofertaId)
                || pedidoRepository.existsByItems_ProductoId(ofertaId);
    }

    /**
     * Elimina una oferta y recalcula estadísticas.
     */
    public void eliminarOferta(String ofertaId) {
        OfertaVendedor oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));

        String productoCatalogoId = oferta.getProductoCatalogoId();
        ofertaRepository.delete(oferta);

        // Recalcular estadísticas del catálogo
        catalogoService.actualizarEstadisticas(productoCatalogoId);
    }

    // ══════════════════════════════════════════════════════════
    // Validaciones
    // ══════════════════════════════════════════════════════════

    /**
     * Precio, stock y compra mínima coherentes con la unidad de la oferta
     * (no se puede publicar "2.5 docenas").
     */
    void validarDatosOferta(OfertaVendedor oferta) {
        UnidadMedida unidad = oferta.getUnidadMedida();
        if (unidad == null) {
            throw new IllegalArgumentException("Elige la unidad de venta.");
        }
        if (oferta.getPrecio() == null || oferta.getPrecio() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a cero");
        }
        if (oferta.getStock() == null || oferta.getStock() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
        String errStock = ValidacionCantidad.validarCoherenciaUnidad(unidad, oferta.getStock(), "El stock");
        if (errStock != null) {
            throw new IllegalArgumentException(errStock);
        }
        Double minima = oferta.getCompraMinima();
        if (minima == null || minima <= 0) {
            throw new IllegalArgumentException("La compra mínima debe ser mayor a cero");
        }
        String errMin = ValidacionCantidad.validarCoherenciaUnidad(unidad, minima, "La compra mínima");
        if (errMin != null) {
            throw new IllegalArgumentException(errMin);
        }
    }

    /** Garantiza que el peso no sea null o cero (se usa por compatibilidad con la logística). */
    private void asegurarPeso(OfertaVendedor oferta) {
        if (oferta.getPeso() == null || oferta.getPeso() <= 0) {
            UnidadMedida u = oferta.getUnidadMedida();
            if (u != null && u.permiteDecimales()) {
                // 1 unidad de stock = 1 kg/lb/L...
                oferta.setPeso(1.0);
            } else {
                Double defaultPeso = (oferta.getPesoPromedioUnidad() != null && oferta.getPesoPromedioUnidad() > 0)
                        ? oferta.getPesoPromedioUnidad() : 1.0;
                oferta.setPeso(defaultPeso);
            }
        }
    }

    private static boolean mismaUnidad(UnidadMedida a, UnidadMedida b) {
        return a.getAbreviatura() != null && a.getAbreviatura().equalsIgnoreCase(b.getAbreviatura());
    }

    // ══════════════════════════════════════════════════════════
    // Consultas
    // ══════════════════════════════════════════════════════════

    /**
     * Lista todas las ofertas de un vendedor (para "Mis Ofertas").
     */
    public List<OfertaVendedor> listarOfertasVendedor(String vendedorId) {
        return ofertaRepository.findByVendedor_Id(vendedorId);
    }

    /**
     * Obtiene ofertas paginadas (para el panel de administración).
     */
    public org.springframework.data.domain.Page<OfertaVendedor> obtenerOfertasPaginadas(int page, int size) {
        return ofertaRepository.findAll(org.springframework.data.domain.PageRequest.of(page, size));
    }

    /**
     * Busca una oferta por ID.
     */
    public OfertaVendedor buscarPorId(String ofertaId) {
        return ofertaRepository.findById(ofertaId).orElse(null);
    }


}
