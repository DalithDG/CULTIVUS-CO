package com.example.demo.Controller;

import com.example.demo.dto.PrediccionDto;
import com.example.demo.dto.PrediccionesResponse;
import com.example.demo.services.PrediccionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/predicciones")
public class PrediccionController {

    @Autowired
    private PrediccionService prediccionService;

    @GetMapping
    public ResponseEntity<?> obtenerPredicciones(
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String categoria) {
        try {
            PrediccionesResponse resp = prediccionService.obtenerPredicciones();
            if (resp == null || resp.predicciones() == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "El servicio de predicción devolvió una respuesta vacía."));
            }

            List<PrediccionDto> filtradas = resp.predicciones().stream()
                    .filter(p -> ciudad == null || ciudad.isBlank() || (p.ciudad() != null && p.ciudad().equalsIgnoreCase(ciudad.trim())))
                    .filter(p -> categoria == null || categoria.isBlank() || (p.categoria() != null && p.categoria().equalsIgnoreCase(categoria.trim())))
                    .sorted((a, b) -> Double.compare(b.demandaEsperadaKg(), a.demandaEsperadaKg()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new PrediccionesResponse(resp.semana(), filtradas));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "El microservicio de predicción no está disponible en este momento. Por favor verifica que esté iniciado en http://localhost:8000 o intenta más tarde.",
                            "detalle", e.getMessage() != null ? e.getMessage() : "Conexión rechazada"
                    ));
        }
    }
}
