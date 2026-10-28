package com.example.demo.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de frecuencia en memoria con ventana deslizante.
 *
 * Adecuado para el despliegue actual (una instancia en Render). Si algún día
 * se escala a varias instancias, el contador debe moverse a Redis para que el
 * límite sea global. Se acota el número de claves para que un atacante no
 * pueda agotar la memoria creando correos/IPs distintas.
 */
@Component
public class PasswordResetRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRateLimiter.class);

    private static final int MAX_CLAVES = 20_000;

    private final Clock clock;
    private final Map<String, Deque<Long>> eventos = new ConcurrentHashMap<>();

    public PasswordResetRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Registra un intento si no se superó el máximo dentro de la ventana.
     *
     * @return true si el intento se permite; false si debe descartarse.
     */
    public boolean permitir(String clave, int maximo, Duration ventana) {
        if (clave == null || maximo <= 0 || ventana == null || ventana.isNegative() || ventana.isZero()) {
            return false;
        }

        if (eventos.size() >= MAX_CLAVES && !eventos.containsKey(clave)) {
            // Bajo inundación de claves nuevas se rechazan para proteger memoria.
            log.warn("Límite de frecuencia: capacidad de claves alcanzada; se rechaza un intento nuevo");
            return false;
        }

        long ahora = clock.millis();
        long desde = ahora - ventana.toMillis();

        Deque<Long> cola = eventos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (cola) {
            while (!cola.isEmpty() && cola.peekFirst() <= desde) {
                cola.pollFirst();
            }
            if (cola.size() >= maximo) {
                return false;
            }
            cola.addLast(ahora);
            return true;
        }
    }

    /**
     * Limpieza periódica de claves inactivas. La ventana máxima configurada es
     * pequeña; una hora sin actividad es suficiente para descartar la clave.
     */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 600_000L)
    public void limpiarClavesInactivas() {
        long limite = clock.millis() - Duration.ofHours(1).toMillis();
        eventos.entrySet().removeIf(entry -> {
            Deque<Long> cola = entry.getValue();
            synchronized (cola) {
                return cola.isEmpty() || cola.peekLast() <= limite;
            }
        });
    }

    /** Solo para pruebas y diagnóstico: cantidad de claves en memoria. */
    int cantidadDeClaves() {
        return eventos.size();
    }
}
