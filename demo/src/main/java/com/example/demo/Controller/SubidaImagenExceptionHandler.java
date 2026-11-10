package com.example.demo.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Respuesta JSON clara cuando una subida de foto supera el límite de multipart (3 MB).
 * Ese error ocurre antes de llegar al controlador, por eso se maneja aquí.
 * Para cualquier otra ruta se vuelve a lanzar la excepción y se conserva el comportamiento anterior.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SubidaImagenExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> archivoDemasiadoGrande(MaxUploadSizeExceededException e,
            HttpServletRequest request) {
        if (request.getRequestURI() != null && request.getRequestURI().startsWith("/vendedor/imagenes/")) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "La imagen supera el máximo de 3 MB."));
        }
        throw e;
    }
}

