package com.example.demo.services;

import com.example.demo.Model.OfertaVendedor;
import org.springframework.stereotype.Component;

/**
 * Helper de entrega de imágenes. Disponible en Thymeleaf como {@code @imagenes}.
 *
 * <ul>
 *   <li>Inserta transformaciones en las URLs de Cloudinary (formato/calidad automáticos y recorte).</li>
 *   <li>Resuelve el fallback: foto propia de la oferta → foto de referencia del catálogo → "sin foto".</li>
 * </ul>
 */
@Component("imagenes")
public class ImagenUrlHelper {

    /** Único fallback de imágenes de toda la aplicación. */
    public static final String SIN_FOTO = "/images/placeholders/sin-foto.svg";

    static final String TRANSFORMACION_TARJETA = "f_auto,q_auto,c_fill,w_800,h_800";
    static final String TRANSFORMACION_DETALLE = "f_auto,q_auto,w_1200";

    private static final String MARCA_UPLOAD = "/upload/";

    /** URL optimizada para tarjetas (800×800 recortado). */
    public String tarjeta(String url) {
        return transformar(url, TRANSFORMACION_TARJETA);
    }

    /** URL optimizada para la vista de detalle (ancho 1200). */
    public String detalle(String url) {
        return transformar(url, TRANSFORMACION_DETALLE);
    }

    /** Imagen de tarjeta de una oferta (propia, referencia o "sin foto"). */
    public String tarjeta(OfertaVendedor oferta) {
        return tarjeta(resolver(oferta));
    }

    /** Imagen de detalle de una oferta (propia, referencia o "sin foto"). */
    public String detalle(OfertaVendedor oferta) {
        return detalle(resolver(oferta));
    }

    /** true si la imagen mostrada es la de referencia del catálogo (la oferta no tiene foto propia). */
    public boolean esReferencia(OfertaVendedor oferta) {
        return oferta != null && !oferta.tieneFotoPropia();
    }

    /** Devuelve la URL a usar, antes de transformar. */
    public String resolver(OfertaVendedor oferta) {
        if (oferta != null && oferta.tieneFotoPropia()) {
            return oferta.getImagenUrl().trim();
        }
        if (oferta != null && oferta.getImagenReferencia() != null && !oferta.getImagenReferencia().isBlank()) {
            return oferta.getImagenReferencia().trim();
        }
        if (oferta != null && oferta.getNombreProducto() != null && !oferta.getNombreProducto().isBlank()) {
            return rutaReferenciaProducto(oferta.getNombreProducto());
        }
        return SIN_FOTO;
    }

    // ── Rutas locales por convención ──

    /** Slugs de las categorías que se muestran al público (con imagen en /images/categorias/). */
    public static final java.util.List<String> CATEGORIAS_PUBLICAS = java.util.List.of(
            "frutas", "verduras", "lacteos", "carnes-y-aves", "granos-y-cereales",
            "cafe-y-cacao", "miel", "condimentos");

    private static final java.util.regex.Pattern DIACRITICOS =
            java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Slug: minúsculas, sin tildes (ñ → n) y caracteres no alfanuméricos → guion.
     * "Mango de azúcar" → "mango-de-azucar".
     */
    public static String slug(String texto) {
        if (texto == null) {
            return "";
        }
        String s = java.text.Normalizer.normalize(texto.trim().toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD);
        s = DIACRITICOS.matcher(s).replaceAll("");
        s = s.replaceAll("[^a-z0-9]+", "-");
        return s.replaceAll("^-+|-+$", "");
    }

    /** Ruta de la foto de referencia de un producto del catálogo: /images/productos/{slug}.webp */
    public static String rutaReferenciaProducto(String nombre) {
        return "/images/productos/" + slug(nombre) + ".webp";
    }

    /** Ruta de la imagen de una categoría: /images/categorias/{slug}.webp */
    public String categoria(String nombreCategoria) {
        String s = slug(nombreCategoria);
        return s.isEmpty() ? SIN_FOTO : "/images/categorias/" + s + ".webp";
    }

    /** Solo las categorías públicas (sin Bebidas, Panadería ni Otros), en el orden de CATEGORIAS_PUBLICAS. */
    public java.util.List<com.example.demo.Model.Categoria> categoriasPublicas(
            java.util.List<com.example.demo.Model.Categoria> categorias) {
        java.util.List<com.example.demo.Model.Categoria> resultado = new java.util.ArrayList<>();
        if (categorias == null) {
            return resultado;
        }
        for (String s : CATEGORIAS_PUBLICAS) {
            for (com.example.demo.Model.Categoria c : categorias) {
                if (c != null && s.equals(slug(c.getNombre()))) {
                    resultado.add(c);
                    break;
                }
            }
        }
        return resultado;
    }

    /** Url con fallback único a "sin foto" cuando viene vacía. */
    public String conFallback(String url) {
        return url == null || url.isBlank() ? SIN_FOTO : url.trim();
    }

    static String transformar(String url, String transformacion) {
        if (url == null || url.isBlank()) {
            return SIN_FOTO;
        }
        String limpia = url.trim();
        if (!limpia.contains("res.cloudinary.com")) {
            return limpia;
        }
        int i = limpia.indexOf(MARCA_UPLOAD);
        if (i < 0) {
            return limpia;
        }
        int inicio = i + MARCA_UPLOAD.length();
        String resto = limpia.substring(inicio);
        // Ya transformada: no se vuelve a insertar (idempotente)
        if (resto.startsWith("f_auto") || resto.startsWith(transformacion)) {
            return limpia;
        }
        return limpia.substring(0, inicio) + transformacion + "/" + resto;
    }
}
