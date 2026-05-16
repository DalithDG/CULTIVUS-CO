package com.example.demo.services;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio para gestionar las ofertas de los vendedores.
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

    // ══════════════════════════════════════════════════════════
    // CRUD de ofertas
    // ══════════════════════════════════════════════════════════

    /**
     * Crea una nueva oferta para un producto del catálogo.
     * La oferta se publica inmediatamente.
     */
    public OfertaVendedor crearOferta(OfertaVendedor oferta) {
        // Validaciones
        if (oferta.getProductoCatalogoId() == null || oferta.getProductoCatalogoId().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar un producto del catálogo");
        }

        ProductoCatalogo producto = catalogoRepository.findById(oferta.getProductoCatalogoId())
                .orElseThrow(() -> new IllegalArgumentException("Producto del catálogo no encontrado"));

        if (!producto.isAprobado() || !producto.isActivo()) {
            throw new IllegalArgumentException("El producto del catálogo no está disponible");
        }

        if (oferta.getVendedor() == null || oferta.getVendedor().getId() == null) {
            throw new IllegalArgumentException("La oferta debe tener un vendedor asociado");
        }

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

        if (oferta.getPrecio() == null || oferta.getPrecio() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a cero");
        }
        if (oferta.getStock() == null || oferta.getStock() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
        
        // Garantizar que el peso no sea null o cero para evitar errores de validación
        if (oferta.getPeso() == null || oferta.getPeso() <= 0) {
            // Si el producto es por peso, asumimos 1.0 como base
            if (producto.getTipoVenta() != null && "PESO".equalsIgnoreCase(producto.getTipoVenta())) {
                oferta.setPeso(1.0);
            } else {
                // Si es por unidad, usamos el peso promedio si existe, sino 1.0
                Double defaultPeso = (oferta.getPesoPromedioUnidad() != null && oferta.getPesoPromedioUnidad() > 0) 
                        ? oferta.getPesoPromedioUnidad() : 1.0;
                oferta.setPeso(defaultPeso);
            }
        }

        // Guardar nombre del producto desnormalizado
        oferta.setNombreProducto(producto.getNombre());
        oferta.setDisponible(true);
        oferta.setCreatedAt(LocalDateTime.now());

        OfertaVendedor ofertaGuardada = ofertaRepository.save(oferta);

        // Actualizar estadísticas del producto del catálogo inmediatamente
        catalogoService.actualizarEstadisticas(oferta.getProductoCatalogoId());

        return ofertaGuardada;
    }



    /**
     * Actualiza una oferta existente.
     */
    public OfertaVendedor actualizarOferta(OfertaVendedor oferta) {
        if (oferta.getId() == null || oferta.getId().isEmpty()) {
            throw new IllegalArgumentException("ID de oferta inválido");
        }
        if (!ofertaRepository.existsById(oferta.getId())) {
            throw new IllegalArgumentException("Oferta no encontrada");
        }
        if (oferta.getPrecio() == null || oferta.getPrecio() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a cero");
        }
        if (oferta.getStock() == null || oferta.getStock() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }

        oferta.setUpdatedAt(LocalDateTime.now());
        OfertaVendedor ofertaActualizada = ofertaRepository.save(oferta);

        // Recalcular estadísticas del catálogo
        catalogoService.actualizarEstadisticas(oferta.getProductoCatalogoId());

        return ofertaActualizada;
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
    // Consultas
    // ══════════════════════════════════════════════════════════

    /**
     * Lista todas las ofertas de un vendedor (para "Mis Ofertas").
     */
    public List<OfertaVendedor> listarOfertasVendedor(String vendedorId) {
        return ofertaRepository.findByVendedor_Id(vendedorId);
    }

    /**
     * Busca una oferta por ID.
     */
    public OfertaVendedor buscarPorId(String ofertaId) {
        return ofertaRepository.findById(ofertaId).orElse(null);
    }


}
