package com.example.demo.services;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar el catálogo centralizado de productos.
 */
@Service
public class CatalogoService {

    @Autowired
    private ProductoCatalogoRepository catalogoRepository;

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private NotificacionService notificacionService;

    // ══════════════════════════════════════════════════════════
    // Catálogo público
    // ══════════════════════════════════════════════════════════

    /**
     * Lista todos los productos aprobados y activos del catálogo (vista pública).
     * Cada item tiene precioMinimo y totalVendedores ya calculados.
     */
    public List<ProductoCatalogo> listarCatalogo() {
        return catalogoRepository.findByAprobadoTrueAndActivoTrue();
    }

    /**
     * Lista productos del catálogo filtrados por categoría.
     */
    public List<ProductoCatalogo> listarPorCategoria(String categoriaId) {
        return catalogoRepository.findByCategoriaIdAndAprobadoTrueAndActivoTrue(categoriaId);
    }

    /**
     * Busca productos en el catálogo por nombre.
     */
    public List<ProductoCatalogo> buscarPorNombre(String nombre) {
        return catalogoRepository.findByNombreContainingIgnoreCaseAndAprobadoTrueAndActivoTrue(nombre);
    }

    /**
     * Obtiene un producto del catálogo por ID.
     */
    public ProductoCatalogo obtenerPorId(String id) {
        return catalogoRepository.findById(id).orElse(null);
    }

    /**
     * Obtiene las ofertas aprobadas y disponibles para un producto del catálogo.
     * Ordenadas por precio ascendente.
     */
    public List<OfertaVendedor> obtenerOfertasAprobadas(String productoCatalogoId) {
        return ofertaRepository.findByProductoCatalogoIdAndDisponibleTrue(
                        productoCatalogoId)
                .stream()
                .sorted(Comparator.comparingDouble(OfertaVendedor::getPrecio))
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════
    // Gestión del catálogo (admin / vendedor con moderación)
    // ══════════════════════════════════════════════════════════

    /**
     * Sugiere un nuevo producto al catálogo (creado por vendedor, pendiente de aprobación).
     */
    public ProductoCatalogo sugerirProducto(ProductoCatalogo producto, String vendedorId) {
        // Verificar que no exista ya
        String nombreNorm = ProductoCatalogo.normalizarNombre(producto.getNombre());
        if (catalogoRepository.existsByNombreNormalizado(nombreNorm)) {
            throw new IllegalArgumentException(
                    "Ya existe un producto con el nombre '" + producto.getNombre() + "' en el catálogo.");
        }

        producto.setNombreNormalizado(nombreNorm);
        producto.setAprobado(false); // Pendiente de moderación
        producto.setSugeridoPor(vendedorId);
        producto.setCreatedAt(LocalDateTime.now());

        ProductoCatalogo guardado = catalogoRepository.save(producto);

        // Notificar a los administradores (Notificación General)
        notificacionService.enviarGeneral(
                "Nueva Sugerencia de Producto",
                "Un vendedor ha sugerido un nuevo producto: '" + producto.getNombre() + "'. Revisa el panel de moderación.",
                "INFO"
        );

        return guardado;
    }

    /**
     * Crea un producto directamente aprobado (usado por admin o migración).
     */
    public ProductoCatalogo crearProductoAprobado(ProductoCatalogo producto) {
        String nombreNorm = ProductoCatalogo.normalizarNombre(producto.getNombre());

        // Si ya existe, retornar el existente
        Optional<ProductoCatalogo> existente = catalogoRepository.findByNombreNormalizado(nombreNorm);
        if (existente.isPresent()) {
            return existente.get();
        }

        producto.setNombreNormalizado(nombreNorm);
        producto.setAprobado(true);
        producto.setActivo(true);
        producto.setCreatedAt(LocalDateTime.now());

        return catalogoRepository.save(producto);
    }

    /**
     * Aprueba un producto pendiente en el catálogo (acción del admin).
     */
    public ProductoCatalogo aprobarProducto(String productoId) {
        ProductoCatalogo producto = catalogoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto del catálogo no encontrado"));

        producto.setAprobado(true);
        producto.setActivo(true);
        producto.setUpdatedAt(LocalDateTime.now());

        ProductoCatalogo guardado = catalogoRepository.save(producto);

        // Notificar al vendedor que sugirió el producto (si aplica)
        if (guardado.getSugeridoPor() != null) {
            notificacionService.enviar(
                    guardado.getSugeridoPor(),
                    "Sugerencia Aprobada",
                    "¡Buenas noticias! Tu sugerencia para '" + guardado.getNombre() + "' ha sido aprobada. Ya puedes crear tu oferta.",
                    "SUCCESS"
            );
        }

        return guardado;
    }

    /**
     * Rechaza un producto pendiente en el catálogo (acción del admin).
     */
    public void rechazarProducto(String productoId) {
        ProductoCatalogo producto = catalogoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto del catálogo no encontrado"));

        producto.setActivo(false);
        producto.setUpdatedAt(LocalDateTime.now());

        catalogoRepository.save(producto);
    }

    /**
     * Lista productos pendientes de aprobación (para admin).
     */
    public List<ProductoCatalogo> listarPendientes() {
        return catalogoRepository.findByAprobadoFalseAndActivoTrue();
    }

    // ══════════════════════════════════════════════════════════
    // Recálculo de estadísticas
    // ══════════════════════════════════════════════════════════

    /**
     * Recalcula precioMinimo, totalVendedores y stockTotal
     * a partir de las ofertas aprobadas y disponibles.
     */
    public void actualizarEstadisticas(String productoCatalogoId) {
        ProductoCatalogo producto = catalogoRepository.findById(productoCatalogoId).orElse(null);
        if (producto == null) return;

        List<OfertaVendedor> ofertasActivas = ofertaRepository
                .findByProductoCatalogoIdAndDisponibleTrue(productoCatalogoId);

        if (ofertasActivas.isEmpty()) {
            producto.setPrecioMinimo(null);
            producto.setTotalVendedores(0);
            producto.setStockTotal(0.0);
        } else {
            double precioMin = ofertasActivas.stream()
                    .mapToDouble(OfertaVendedor::getPrecio)
                    .min()
                    .orElse(0.0);
            double stockTotal = ofertasActivas.stream()
                    .mapToDouble(OfertaVendedor::getStock)
                    .sum();

            producto.setPrecioMinimo(precioMin);
            producto.setTotalVendedores(ofertasActivas.size());
            producto.setStockTotal(stockTotal);
        }

        producto.setUpdatedAt(LocalDateTime.now());
        catalogoRepository.save(producto);
    }

    /**
     * Obtiene todos los productos del catálogo (para dropdown del vendedor al crear oferta).
     * Solo muestra productos aprobados.
     */
    public List<ProductoCatalogo> listarAprobados() {
        return catalogoRepository.findByAprobadoTrueAndActivoTrue();
    }

    /**
     * Obtiene una oferta específica por su ID.
     */
    public OfertaVendedor obtenerOfertaPorId(String id) {
        return ofertaRepository.findById(id).orElse(null);
    }
}
