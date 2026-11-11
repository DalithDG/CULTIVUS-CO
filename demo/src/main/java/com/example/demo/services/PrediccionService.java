package com.example.demo.services;

import com.example.demo.dto.PrediccionesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class PrediccionService {

    private static final Logger log = LoggerFactory.getLogger(PrediccionService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${prediccion.service.url:http://localhost:8000}")
    private String serviceUrl;

    /**
     * Consume el microservicio FastAPI en GET /predicciones
     * utilizando RestTemplate.
     */
    public PrediccionesResponse obtenerPredicciones() {
        String baseUrl = (serviceUrl != null && !serviceUrl.isBlank()) ? serviceUrl.trim().replaceAll("/+$", "") : "http://localhost:8000";
        String url = baseUrl + "/predicciones";
        try {
            log.info("Consultando microservicio de predicción en: {}", url);
            PrediccionesResponse response = restTemplate.getForObject(url, PrediccionesResponse.class);
            if (response == null) {
                throw new RuntimeException("Respuesta nula del microservicio de predicción");
            }
            return response;
        } catch (ResourceAccessException e) {
            log.warn("Microservicio de predicción no alcanzable en {}: {}", url, e.getMessage());
            throw new RuntimeException("Servicio de predicción no disponible (conexión rechazada o timeout)", e);
        } catch (RestClientResponseException e) {
            log.warn("Error HTTP del microservicio de predicción ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error del servicio de predicción: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Fallo inesperado al consultar predicciones: {}", e.getMessage());
            throw new RuntimeException("Error al consultar predicciones: " + e.getMessage(), e);
        }
    }
}
