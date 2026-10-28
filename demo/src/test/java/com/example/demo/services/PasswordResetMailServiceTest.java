package com.example.demo.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pruebas del enlace y de la normalización de APP_BASE_URL, sin abrir SMTP.
 */
class PasswordResetMailServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);

    private PasswordResetMailService servicio(String baseUrl) {
        return new PasswordResetMailService(provider, "smtp.gmail.com", "cuenta@gmail.com",
                "app-password", "cuenta@gmail.com", baseUrl);
    }

    @Test
    @DisplayName("El enlace usa APP_BASE_URL y codifica el token")
    void construyeEnlaceSeguro() {
        PasswordResetMailService service = servicio("https://cultivus-co.onrender.com/");

        assertEquals("https://cultivus-co.onrender.com", service.getBaseUrl());
        assertEquals("https://cultivus-co.onrender.com/usuario/restablecer?token=abc_DEF-123",
                service.construirEnlace("abc_DEF-123"));
    }

    @Test
    @DisplayName("Una APP_BASE_URL con esquema peligroso cae al valor local")
    void baseUrlPeligrosaSeRechaza() {
        assertEquals("http://localhost:8080",
                PasswordResetMailService.normalizarBaseUrl("javascript:alert(1)"));
        assertEquals("http://localhost:8080",
                PasswordResetMailService.normalizarBaseUrl("file:///etc/passwd"));
    }

    @Test
    @DisplayName("Sin MAIL_HOST no intenta usar el proveedor SMTP")
    void sinHostNoEnvia() {
        PasswordResetMailService service = new PasswordResetMailService(
                provider, "", "", "", "", "http://localhost:8080");

        service.enviarEnlace("ana@example.com", "Ana", "token");

        verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("La URL por defecto es local y segura")
    void baseUrlPorDefecto() {
        assertTrue(servicio("").getBaseUrl().startsWith("http://localhost"));
    }
}
