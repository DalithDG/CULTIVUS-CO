package com.example.demo.services;

import com.example.demo.Model.Categoria;
import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.embebidos.CategoriaProducto;
import com.example.demo.Model.embebidos.UnidadMedida;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * US-06: siembra la lista de productos por defecto y migra, de forma idempotente, los datos
 * anteriores (unidad por producto y por oferta). Lo invoca el DataLoader al arrancar.
 *
 * <p>Orden: categorías → depuración de sintéticos → siembra → productos existentes → ofertas existentes.
 * Cada fase se puede ejecutar varias veces sin duplicar ni sobrescribir datos ya migrados.</p>
 */
@Service
public class CatalogoInicializador {

    private static final Logger logger = LoggerFactory.getLogger(CatalogoInicializador.class);

    static final String IMAGEN_PLACEHOLDER = "/images/placeholder.jpg";

    /** Categorías que deben existir antes de sembrar (incluye Café y Cacao y Miel). */
    static final List<String> CATEGORIAS_BASE = List.of(
            "Frutas", "Verduras", "Lácteos", "Carnes y Aves", "Granos y Cereales",
            "Bebidas", "Panadería", "Condimentos", "Otros", "Café y Cacao", "Miel");

    /** Descripción corta genérica por categoría (no se siembran productos en Bebidas, Panadería ni Otros). */
    private static final Map<String, String> DESCRIPCION_POR_CATEGORIA = Map.of(
            "Verduras", "Verdura fresca cosechada por productores locales del Caribe colombiano.",
            "Frutas", "Fruta fresca de temporada, directa del campo.",
            "Granos y Cereales", "Grano de calidad cultivado por productores locales.",
            "Café y Cacao", "Producto de café y cacao de origen local, procesado de forma artesanal.",
            "Lácteos", "Lácteo fresco elaborado por productores de la región.",
            "Carnes y Aves", "Producto fresco de granjas y fincas locales.",
            "Condimentos", "Condimento natural para dar sabor a tus recetas.",
            "Miel", "Miel pura de abejas, sin aditivos."
    );

    /** Formato: Producto | unidadPorDefecto | unidadesPermitidas (separadas por coma). */
    static final Map<String, String> LISTA_POR_DEFECTO = new LinkedHashMap<>();

    static {
        LISTA_POR_DEFECTO.put("Verduras", """
                Yuca | kg | kg,lb
                Ñame espino | kg | kg,lb
                Ñame diamante | kg | kg,lb
                Batata | kg | kg,lb
                Ahuyama | kg | kg,lb,un
                Berenjena | kg | kg,lb
                Tomate | kg | kg,lb
                Pimentón | kg | kg,lb
                Habichuela | kg | kg,lb
                Quimbombó | kg | kg,lb
                Pepino cohombro | kg | kg,lb
                Mazorca tierna | un | un,doc
                """);
        LISTA_POR_DEFECTO.put("Frutas", """
                Plátano hartón | un | un,doc,kg
                Banano | kg | kg,un,doc
                Mango de azúcar | kg | kg,lb,un
                Mango Tommy | kg | kg,lb,un
                Aguacate | kg | kg,lb,un
                Melón | un | un,kg
                Patilla | un | un,kg
                Ciruela costeña | kg | kg,lb
                Níspero | kg | kg,lb
                Limón tahití | kg | kg,lb,un
                Papaya | un | un,kg
                Guayaba | kg | kg,lb
                Coco | un | un,doc
                Maracuyá | kg | kg,lb
                """);
        LISTA_POR_DEFECTO.put("Granos y Cereales", """
                Maíz seco (grano) | kg | kg,lb
                Arroz paddy | kg | kg,lb
                Arroz blanco | kg | kg,lb
                Fríjol cabecita negra | kg | kg,lb
                Fríjol guandul | kg | kg,lb
                Ajonjolí | kg | kg,lb
                Maní | kg | kg,lb
                """);
        LISTA_POR_DEFECTO.put("Café y Cacao", """
                Café pergamino seco | kg | kg,lb
                Café tostado y molido | lb | lb,g,kg
                Cacao en grano seco | kg | kg,lb
                Chocolate de mesa artesanal | lb | lb,un,g
                """);
        LISTA_POR_DEFECTO.put("Lácteos", """
                Queso costeño | kg | kg,lb
                Suero costeño | L | L,ml
                Leche fresca | L | L
                Cuajada | kg | kg,lb
                Mantequilla costeña | lb | lb,g
                """);
        LISTA_POR_DEFECTO.put("Carnes y Aves", """
                Pollo criollo | kg | kg,lb,un
                Huevos criollos | doc | doc,bandeja,un
                Carne de res | kg | kg,lb
                Carne de cerdo | kg | kg,lb
                Pescado de río | kg | kg,un
                Mojarra roja | kg | kg,un
                Bocachico | kg | kg,un
                """);
        LISTA_POR_DEFECTO.put("Condimentos", """
                Ají dulce | kg | kg,lb
                Ají picante | kg | kg,g
                Cilantro | manojo | manojo,g
                Cebolla larga | manojo | manojo,kg
                Ajo | kg | kg,lb,g
                Orégano | manojo | manojo,g
                Panela | kg | kg,lb,un
                """);
        LISTA_POR_DEFECTO.put("Miel", """
                Miel de abeja | kg | kg,g,lb
                """);
    }

    private final CategoriaRepository categoriaRepository;
    private final ProductoCatalogoRepository catalogoRepository;
    private final OfertaRepository ofertaRepository;

    public CatalogoInicializador(CategoriaRepository categoriaRepository,
                                 ProductoCatalogoRepository catalogoRepository,
                                 OfertaRepository ofertaRepository) {
        this.categoriaRepository = categoriaRepository;
        this.catalogoRepository = catalogoRepository;
        this.ofertaRepository = ofertaRepository;
    }

    /** Ejecuta todas las fases; un fallo en una no impide las siguientes. */
    public void ejecutar() {
        try {
            asegurarCategorias();
        } catch (Exception e) {
            logger.error("❌ US-06: error asegurando categorías", e);
        }
        try {
            purgarProductosSinteticos();
        } catch (Exception e) {
            logger.error("❌ US-06: error purgando productos sintéticos", e);
        }
        try {
            sembrarListaPorDefecto();
        } catch (Exception e) {
            logger.error("❌ US-06: error sembrando la lista por defecto", e);
        }
        try {
            migrarProductosExistentes();
        } catch (Exception e) {
            logger.error("❌ US-06: error migrando productos existentes", e);
        }
        try {
            migrarOfertasExistentes();
        } catch (Exception e) {
            logger.error("❌ US-06: error migrando ofertas existentes", e);
        }
    }

    /**
     * Elimina productos sintéticos duplicados (con sufijos numéricos o clones de prueba)
     * generados por pruebas o mocks anteriores, siempre que no tengan ofertas activas.
     */
    public int purgarProductosSinteticos() {
        int eliminados = 0;
        Pattern patronSintetico = Pattern.compile(
                "(?i)(\\b(Familiar|Gourmet|Tradicional|Fresco|Especial|Extra|Artesanal|Premium|Ecol[oó]gico|de Temporada)\\s+\\d+$|\\s+\\d+$)"
        );
        for (ProductoCatalogo p : catalogoRepository.findAll()) {
            if (p.getNombre() != null && patronSintetico.matcher(p.getNombre()).find()) {
                boolean tieneOfertas = ofertaRepository.countByProductoCatalogoId(p.getId()) > 0;
                if (!tieneOfertas) {
                    catalogoRepository.delete(p);
                    eliminados++;
                }
            }
        }
        if (eliminados > 0) {
            logger.info("🧹 US-06: se eliminaron {} productos sintéticos repetidos del catálogo", eliminados);
        }
        return eliminados;
    }

    /** Crea las categorías que falten (incluye "Café y Cacao" y "Miel"). */
    public int asegurarCategorias() {
        int creadas = 0;
        for (String nombre : CATEGORIAS_BASE) {
            if (categoriaRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
                Categoria cat = new Categoria(nombre, "Productos frescos de " + nombre.toLowerCase());
                categoriaRepository.save(cat);
                creadas++;
            }
        }
        if (creadas > 0) {
            logger.info("✅ US-06: {} categorías base creadas o aseguradas", creadas);
        }
        return creadas;
    }

    /**
     * Siembra los productos por defecto por categoría sin duplicar por nombreNormalizado.
     * Solo crea los que falten; no sobrescribe cambios manuales si ya tienen unidades.
     */
    public int sembrarListaPorDefecto() {
        int creados = 0;
        int completados = 0;

        for (Map.Entry<String, String> bloque : LISTA_POR_DEFECTO.entrySet()) {
            String nombreCategoria = bloque.getKey();
            Categoria categoria = categoriaRepository.findByNombreIgnoreCase(nombreCategoria).orElse(null);
            if (categoria == null) {
                logger.warn("US-06: no existe la categoría '{}', se omite su siembra", nombreCategoria);
                continue;
            }
            CategoriaProducto categoriaEmbebida = new CategoriaProducto(categoria.getId(), categoria.getNombre());

            for (String linea : bloque.getValue().split("\\R")) {
                if (linea.isBlank()) {
                    continue;
                }
                String[] partes = linea.split("\\|");
                if (partes.length != 3) {
                    logger.warn("US-06: línea de siembra inválida: '{}'", linea);
                    continue;
                }
                String nombre = partes[0].trim();
                UnidadMedida porDefecto = UnidadMedida.deAbreviatura(partes[1].trim());
                List<UnidadMedida> permitidas = new ArrayList<>();
                for (String ab : partes[2].split(",")) {
                    permitidas.add(UnidadMedida.deAbreviatura(ab.trim()));
                }

                String normalizado = ProductoCatalogo.normalizarNombre(nombre);
                ProductoCatalogo existente = catalogoRepository.findByNombreNormalizado(normalizado).orElse(null);
                if (existente != null) {
                    // No se duplica; solo se completan las unidades si aún no las tiene
                    if (existente.getUnidadPorDefecto() == null || existente.getUnidadesPermitidas() == null || existente.getUnidadesPermitidas().isEmpty()) {
                        existente.definirUnidades(porDefecto, permitidas);
                        existente.setUpdatedAt(LocalDateTime.now());
                        catalogoRepository.save(existente);
                        completados++;
                    }
                    continue;
                }

                ProductoCatalogo nuevo = new ProductoCatalogo(
                        nombre,
                        DESCRIPCION_POR_CATEGORIA.getOrDefault(nombreCategoria, "Producto fresco de productores locales."),
                        IMAGEN_PLACEHOLDER,
                        categoriaEmbebida,
                        porDefecto,
                        permitidas);
                nuevo.setAprobado(true);
                nuevo.setActivo(true);
                nuevo.setSugeridoPor(null);
                catalogoRepository.save(nuevo);
                creados++;
            }
        }
        if (creados > 0 || completados > 0) {
            logger.info("✅ US-06: lista por defecto sembrada ({} nuevos, {} completados)", creados, completados);
        }
        return creados;
    }

    /** Completa unidadPorDefecto y unidadesPermitidas en los productos que no las tienen (desde la unidad legacy). */
    public int migrarProductosExistentes() {
        int migrados = 0;
        for (ProductoCatalogo p : catalogoRepository.findAll()) {
            if (p.getUnidadPorDefecto() != null && p.getUnidadesPermitidas() != null && !p.getUnidadesPermitidas().isEmpty()) {
                continue;
            }
            String legado = p.getUnidadMedida(); // e.g. "kg", "un"
            UnidadMedida unidad = UnidadMedida.deAbreviatura(legado);
            List<UnidadMedida> permitidas = new ArrayList<>();
            permitidas.add(unidad.copia());
            if ("kg".equalsIgnoreCase(unidad.getAbreviatura())) {
                permitidas.add(UnidadMedida.LB.copia());
            } else if ("un".equalsIgnoreCase(unidad.getAbreviatura())) {
                permitidas.add(UnidadMedida.DOC.copia());
            }
            p.definirUnidades(unidad, permitidas);
            p.setUpdatedAt(LocalDateTime.now());
            catalogoRepository.save(p);
            migrados++;
        }
        if (migrados > 0) {
            logger.info("✅ US-06: {} productos existentes completados con sus unidades", migrados);
        }
        return migrados;
    }

    /**
     * Completa unidadMedida embebida en las ofertas existentes leyendo la unidad de su producto,
     * o por defecto "un" si el producto no existe.
     */
    public int migrarOfertasExistentes() {
        int migradas = 0;
        Map<String, ProductoCatalogo> cache = new LinkedHashMap<>();
        for (OfertaVendedor oferta : ofertaRepository.findAll()) {
            if (oferta.getUnidadMedida() != null && oferta.getUnidadMedida().getAbreviatura() != null) {
                continue;
            }
            ProductoCatalogo producto = oferta.getProductoCatalogoId() == null ? null
                    : cache.computeIfAbsent(oferta.getProductoCatalogoId(),
                            id -> catalogoRepository.findById(id).orElse(null));
            String legado = producto != null ? producto.getUnidadMedida() : null;
            UnidadMedida unidad = UnidadMedida.deAbreviatura(legado); // sin mapeo = "un"
            oferta.setUnidadMedida(unidad);
            if (oferta.getEstadoPublicacion() == null) {
                oferta.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
            }
            ofertaRepository.save(oferta);

            // Mantener coherencia: la unidad de la oferta debe poder elegirse en el producto (máx. 3)
            if (producto != null) {
                if (producto.buscarUnidadPermitida(unidad.getAbreviatura()) == null
                        && producto.getUnidadesPermitidas().size() < 3) {
                    producto.getUnidadesPermitidas().add(unidad.copia());
                    catalogoRepository.save(producto);
                }
            }
            migradas++;
        }
        if (migradas > 0) {
            logger.info("✅ US-06: {} ofertas migradas con su unidad de venta", migradas);
        }
        return migradas;
    }
}
