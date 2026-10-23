package com.example.demo.services;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Catálogo fijo de las 6 zonas logísticas de Bolívar.
 *
 * Los ids y nombres de zona viven en código (no cambian). La relación municipio -> zona y la
 * matriz de tarifas viven en Mongo (colecciones zonas_municipio y tarifas_envio); este catálogo
 * solo sirve como semilla para DataLoader y para validar ids de zona.
 */
public final class ZonasLogistica {

    private ZonasLogistica() {
    }

    public record ZonaDef(int id, String nombre, List<String> municipios) {
    }

    public static final List<ZonaDef> ZONAS = List.of(
            new ZonaDef(1, "Dique", List.of(
                    "Cartagena", "Arjona", "Arroyohondo", "Calamar", "Clemencia", "Mahates",
                    "San Cristóbal", "San Estanislao", "Santa Catalina", "Santa Rosa", "Turbaco",
                    "Turbaná", "Villanueva")),
            new ZonaDef(2, "Montes de María", List.of(
                    "El Carmen de Bolívar", "El Guamo", "María la Baja", "San Jacinto",
                    "San Juan Nepomuceno", "Soplaviento", "Zambrano")),
            new ZonaDef(3, "Mojana", List.of(
                    "Achí", "Córdoba", "Magangué", "Montecristo", "Pinillos",
                    "San Jacinto del Cauca", "Tiquisio")),
            new ZonaDef(4, "Depresión Momposina", List.of(
                    "Cicuco", "Hatillo de Loba", "Margarita", "San Fernando", "Mompox",
                    "Talaigua Nuevo")),
            new ZonaDef(5, "Loba", List.of(
                    "Altos del Rosario", "Barranco de Loba", "El Peñón", "San Martín de Loba",
                    "Regidor", "Río Viejo", "Norosí")),
            new ZonaDef(6, "Magdalena Medio", List.of(
                    "Arenal", "Cantagallo", "Morales", "San Pablo", "Santa Rosa del Sur", "Simití")));

    public static List<Integer> idsTodas() {
        return ZONAS.stream().map(ZonaDef::id).collect(Collectors.toList());
    }

    public static boolean esIdValido(Integer id) {
        return id != null && ZONAS.stream().anyMatch(z -> z.id() == id);
    }

    public static String nombreDe(Integer id) {
        if (id == null) return null;
        return ZONAS.stream().filter(z -> z.id() == id).map(ZonaDef::nombre).findFirst().orElse(null);
    }

    /**
     * Normaliza un nombre de municipio: sin tildes, en mayúsculas y con espacios colapsados.
     */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String sinTildes = java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase().replaceAll("\\s+", " ").trim();
    }

    /**
     * Tarifa de ejemplo (inventada) entre dos zonas, en centavos.
     * Las zonas están numeradas de norte a sur, así que la distancia aproximada es |origen - destino|:
     * misma zona $6.000 y +$4.000 por cada zona de distancia (máximo $26.000).
     */
    public static long tarifaEjemploCentavos(int zonaOrigen, int zonaDestino) {
        int distancia = Math.abs(zonaOrigen - zonaDestino);
        long cop = 6_000L + 4_000L * distancia;
        return cop * 100L;
    }
}
