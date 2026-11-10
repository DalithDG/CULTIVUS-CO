package com.example.demo.services;

import com.example.demo.Model.OfertaVendedor;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ImagenCloudinaryTest {

    // 1x1 PNG transparente válido
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Test
    void validadorAceptaPngRealYRechazaTextoDisfrazado() {
        assertEquals(ImagenValidador.PNG, ImagenValidador.validar(PNG_1X1));
        assertThrows(IllegalArgumentException.class,
                () -> ImagenValidador.validar("no soy una imagen".getBytes()));
    }

    @Test
    void helperTransformaUrlsCloudinaryYResuelveSlugs() {
        ImagenUrlHelper helper = new ImagenUrlHelper();

        assertEquals("mango-de-azucar", helper.slug("Mango de azúcar"));
        assertEquals("/images/productos/mango-de-azucar.webp", helper.rutaReferenciaProducto("Mango de azúcar"));
        assertEquals("/images/categorias/cafe-y-cacao.webp", helper.categoria("Café y Cacao"));

        String urlOriginal = "https://res.cloudinary.com/coohnl8s/image/upload/v1700000000/cultivus/ofertas/foto123.webp";
        assertEquals(
                "https://res.cloudinary.com/coohnl8s/image/upload/f_auto,q_auto,c_fill,w_800,h_800/v1700000000/cultivus/ofertas/foto123.webp",
                helper.tarjeta(urlOriginal));
        assertEquals(
                "https://res.cloudinary.com/coohnl8s/image/upload/f_auto,q_auto,w_1200/v1700000000/cultivus/ofertas/foto123.webp",
                helper.detalle(urlOriginal));

        OfertaVendedor sinFotoPropia = new OfertaVendedor();
        sinFotoPropia.setNombreProducto("Aguacate Hass");
        assertTrue(helper.esReferencia(sinFotoPropia));
        assertEquals("/images/productos/aguacate-hass.webp", helper.tarjeta(sinFotoPropia));
    }

    @Test
    void subidaYBorradoRealEnCloudinarySiEnvConfigurado() throws Exception {
        Properties props = new Properties();
        for (Path candidate : java.util.List.of(Path.of("..", ".env"), Path.of(".env"))) {
            if (Files.exists(candidate)) {
                try (FileInputStream fis = new FileInputStream(candidate.toFile())) {
                    props.load(fis);
                }
            }
        }
        String cloudName = props.getProperty("CLOUDINARY_CLOUD_NAME", "").trim();
        String apiKey = props.getProperty("CLOUDINARY_API_KEY", "").trim();
        String apiSecret = props.getProperty("CLOUDINARY_API_SECRET", "").trim();

        CloudinaryService service = new CloudinaryService(cloudName, apiKey, apiSecret);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                service.estaConfigurado(),
                "Omitiendo prueba en vivo de Cloudinary porque no hay credenciales en .env");

        String secureUrl = service.subirImagenOferta(PNG_1X1);
        assertNotNull(secureUrl);
        assertTrue(secureUrl.startsWith("https://res.cloudinary.com/" + cloudName + "/image/upload/"),
                "URL inesperada: " + secureUrl);
        assertTrue(secureUrl.contains("cultivus/ofertas/"), "Debe guardarse en cultivus/ofertas: " + secureUrl);

        String publicId = CloudinaryService.extraerPublicId(secureUrl);
        assertNotNull(publicId, "Debe extraer el public_id de " + secureUrl);
        assertTrue(publicId.startsWith("cultivus/ofertas/"));

        // Limpiar la imagen de prueba en Cloudinary
        service.eliminarPorUrl(secureUrl);
    }
}
