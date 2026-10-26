package com.example.demo.services;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
     * Catálogo paginado con filtros por categoría(es), precio máximo y orden.
     */
    public Page<ProductoCatalogo> listarCatalogo(int page, int size, List<String> categoriaIds, String orden, Double precioMax) {
        int pagina = Math.max(0, page);
        int tamano = Math.min(48, Math.max(1, size));

        Sort sort = construirOrden(orden);
        Pageable pageable = PageRequest.of(pagina, tamano, sort);

        Page<ProductoCatalogo> resultado = consultarPagina(categoriaIds, precioMax, pageable);

        // Si la página pedida no existe, usar la última disponible
        if (resultado.getTotalPages() > 0 && pagina >= resultado.getTotalPages()) {
            pagina = resultado.getTotalPages() - 1;
            pageable = PageRequest.of(pagina, tamano, sort);
            resultado = consultarPagina(categoriaIds, precioMax, pageable);
        }

        return resultado;
    }

    private Page<ProductoCatalogo> consultarPagina(List<String> categoriaIds, Double precioMax, Pageable pageable) {
        boolean tieneCategorias = categoriaIds != null && !categoriaIds.isEmpty();
        boolean tienePrecio = precioMax != null;

        if (tieneCategorias && tienePrecio) {
            return catalogoRepository.findPublicoByCategoriasAndPrecioMax(categoriaIds, precioMax, pageable);
        }
        if (tieneCategorias) {
            return catalogoRepository.findPublicoByCategorias(categoriaIds, pageable);
        }
        if (tienePrecio) {
            return catalogoRepository.findPublicoByPrecioMax(precioMax, pageable);
        }
        return catalogoRepository.findPublico(pageable);
    }

    private org.springframework.data.domain.Sort construirOrden(String orden) {
        if (orden == null) {
            orden = "relevancia";
        }
        switch (orden) {
            case "precio-menor":
                return org.springframework.data.domain.Sort.by(
                        new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "precioMinimo")
                                .with(org.springframework.data.domain.Sort.NullHandling.NULLS_LAST));
            case "precio-mayor":
                return org.springframework.data.domain.Sort.by(
                        new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "precioMinimo")
                                .with(org.springframework.data.domain.Sort.NullHandling.NULLS_LAST));
            case "nombre":
                return org.springframework.data.domain.Sort.by("nombre").ascending();
            case "recientes":
                return org.springframework.data.domain.Sort.by("createdAt").descending()
                        .and(org.springframework.data.domain.Sort.by("id").ascending());
            case "relevancia":
            default:
                return org.springframework.data.domain.Sort.by("createdAt").descending()
                        .and(org.springframework.data.domain.Sort.by("id").ascending());
        }
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

    /**
     * Obtiene las ofertas aprobadas y disponibles para un producto con paginación.
     */
    public Page<OfertaVendedor> obtenerOfertasAprobadasPaginadas(String productoCatalogoId, int page, int size) {
        List<OfertaVendedor> todas = obtenerOfertasAprobadas(productoCatalogoId);
        int total = todas.size();
        int tamano = Math.min(48, Math.max(1, size));
        int totalPages = total > 0 ? (int) Math.ceil((double) total / tamano) : 1;
        int pagina = Math.max(0, page);
        if (pagina >= totalPages) {
            pagina = Math.max(0, totalPages - 1);
        }

        int fromIndex = Math.min(pagina * tamano, total);
        int toIndex = Math.min(fromIndex + tamano, total);
        List<OfertaVendedor> sublist = todas.subList(fromIndex, toIndex);

        return new org.springframework.data.domain.PageImpl<>(sublist, PageRequest.of(pagina, tamano), total);
    }

    /**
     * Obtiene el producto anterior en el catálogo (prioriza productos de la misma categoría).
     */
    public ProductoCatalogo obtenerProductoAnterior(ProductoCatalogo actual) {
        if (actual == null || actual.getId() == null) {
            return null;
        }
        List<ProductoCatalogo> lista = null;
        if (actual.getCategoria() != null && actual.getCategoria().getId() != null) {
            lista = listarPorCategoria(actual.getCategoria().getId());
        }
        if (lista == null || lista.size() <= 1) {
            lista = listarCatalogo();
        }
        if (lista == null || lista.isEmpty()) {
            return null;
        }

        for (int i = 0; i < lista.size(); i++) {
            if (actual.getId().equals(lista.get(i).getId())) {
                if (i > 0) {
                    return lista.get(i - 1);
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Obtiene el producto siguiente en el catálogo (prioriza productos de la misma categoría).
     */
    public ProductoCatalogo obtenerProductoSiguiente(ProductoCatalogo actual) {
        if (actual == null || actual.getId() == null) {
            return null;
        }
        List<ProductoCatalogo> lista = null;
        if (actual.getCategoria() != null && actual.getCategoria().getId() != null) {
            lista = listarPorCategoria(actual.getCategoria().getId());
        }
        if (lista == null || lista.size() <= 1) {
            lista = listarCatalogo();
        }
        if (lista == null || lista.isEmpty()) {
            return null;
        }

        for (int i = 0; i < lista.size(); i++) {
            if (actual.getId().equals(lista.get(i).getId())) {
                if (i < lista.size() - 1) {
                    return lista.get(i + 1);
                } else {
                    return null;
                }
            }
        }
        return null;
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

    public org.springframework.data.domain.Page<ProductoCatalogo> listarPendientesPaginados(int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return catalogoRepository.findByAprobadoFalseAndActivoTrue(pageable);
    }

    public long obtenerCantidadCatalogoPendiente() {
        return catalogoRepository.findByAprobadoFalseAndActivoTrue(org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
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
