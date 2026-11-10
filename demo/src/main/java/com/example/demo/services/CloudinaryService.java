package com.example.demo.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Subida y borrado de fotos de ofertas en Cloudinary.
 *
 * <p>Las credenciales llegan por variables de entorno (CLOUDINARY_CLOUD_NAME,
 * CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET) o archivo local {@code .env}. Si faltan,
 * la aplicación arranca igual y {@link #subirImagenOferta(byte[])} lanza
 * {@link CloudinaryNoConfiguradoException}.</p>
 */
@Service
public class CloudinaryService {

    private static final Logger logger = LoggerFactory.getLogger(CloudinaryService.class);

    public static final String CARPETA_OFERTAS = "cultivus/ofertas";

    /** Extrae el public_id de una URL: .../upload/[transformaciones/][v123/]cultivus/ofertas/abc.webp */
    private static final Pattern PUBLIC_ID = Pattern.compile(
            "res\\.cloudinary\\.com/[^/]+/image/upload/(?:[^/]+/)*?(?:v\\d+/)?(" + CARPETA_OFERTAS + "/[^/.?]+)(?:\\.[a-zA-Z0-9]+)?(?:\\?.*)?$");

    private static final List<Path> RUTAS_ENV_LOCALES = List.of(
            Path.of(".env"),
            Path.of("demo", ".env"),
            Path.of("..", ".env"),
            Path.of("CULTIVUS-CO", "demo", ".env"),
            Path.of("CULTIVUS-CO", ".env")
    );

    private volatile String cloudName;
    private volatile String apiKey;
    private volatile String apiSecret;

    private volatile Cloudinary cliente;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.cloudName = cloudName != null ? cloudName.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiSecret = apiSecret != null ? apiSecret.trim() : "";
        asegurarCredencialesLocales();
    }

    public boolean estaConfigurado() {
        if (!tieneCredenciales()) {
            asegurarCredencialesLocales();
        }
        return tieneCredenciales();
    }

    private boolean tieneCredenciales() {
        return StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret);
    }

    /**
     * Si el servidor arrancó desde otro directorio de trabajo o antes de actualizar el .env local,
     * intenta recargar CLOUDINARY_* desde los archivos .env locales disponibles.
     */
    private synchronized void asegurarCredencialesLocales() {
        if (tieneCredenciales()) {
            return;
        }
        for (Path ruta : RUTAS_ENV_LOCALES) {
            try {
                if (Files.exists(ruta) && Files.isRegularFile(ruta)) {
                    Properties props = new Properties();
                    try (FileInputStream fis = new FileInputStream(ruta.toFile())) {
                        props.load(fis);
                    }
                    String cn = props.getProperty("CLOUDINARY_CLOUD_NAME", "").trim();
                    String ak = props.getProperty("CLOUDINARY_API_KEY", "").trim();
                    String as = props.getProperty("CLOUDINARY_API_SECRET", "").trim();
                    if (StringUtils.hasText(cn) && StringUtils.hasText(ak) && StringUtils.hasText(as)) {
                        this.cloudName = cn;
                        this.apiKey = ak;
                        this.apiSecret = as;
                        this.cliente = null;
                        logger.info("Credenciales de Cloudinary cargadas desde {}", ruta.toAbsolutePath().normalize());
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** Sube la imagen (ya validada) a la carpeta de ofertas y devuelve la URL segura. */
    public String subirImagenOferta(byte[] bytes) {
        if (!estaConfigurado()) {
            throw new CloudinaryNoConfiguradoException();
        }
        try {
            Map<?, ?> resultado = cliente().uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", CARPETA_OFERTAS,
                    "resource_type", "image",
                    "overwrite", false));
            Object url = resultado.get("secure_url");
            if (url == null) {
                throw new IllegalStateException("Cloudinary no devolvió la URL de la imagen");
            }
            return url.toString();
        } catch (CloudinaryNoConfiguradoException e) {
            throw e;
        } catch (Exception e) {
            logger.error("No se pudo subir la imagen a Cloudinary: {}", e.getMessage());
            throw new IllegalStateException("No pudimos guardar la imagen. Inténtalo de nuevo.", e);
        }
    }

    /**
     * Borra una imagen de Cloudinary a partir de su URL (mejor esfuerzo: nunca lanza excepción).
     * Ignora URLs que no sean de nuestra carpeta (placeholders, rutas locales, etc.).
     */
    public void eliminarPorUrl(String url) {
        try {
            String publicId = extraerPublicId(url);
            if (publicId == null || !estaConfigurado()) {
                return;
            }
            cliente().uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
        } catch (Exception e) {
            logger.warn("No se pudo borrar la imagen anterior de Cloudinary ({}): {}", url, e.getMessage());
        }
    }

    /** public_id de una URL de Cloudinary de la carpeta de ofertas, o null si no corresponde. */
    static String extraerPublicId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher m = PUBLIC_ID.matcher(url.trim());
        return m.find() ? m.group(1) : null;
    }

    private Cloudinary cliente() {
        Cloudinary c = cliente;
        if (c == null) {
            synchronized (this) {
                if (cliente == null) {
                    cliente = new Cloudinary(ObjectUtils.asMap(
                            "cloud_name", cloudName,
                            "api_key", apiKey,
                            "api_secret", apiSecret,
                            "secure", true));
                }
                c = cliente;
            }
        }
        return c;
    }

    /** El servicio de imágenes no tiene credenciales configuradas. */
    public static class CloudinaryNoConfiguradoException extends RuntimeException {
        public CloudinaryNoConfiguradoException() {
            super("La subida de imágenes no está disponible en este momento.");
        }
    }
}
