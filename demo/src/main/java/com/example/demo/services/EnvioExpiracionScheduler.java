package com.example.demo.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea periódica que verifica si existen envíos pendientes de asignación
 * que hayan superado las horas máximas configuradas (HORAS_MAX_SIN_TRANSPORTISTA).
 */
@Component
public class EnvioExpiracionScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnvioExpiracionScheduler.class);

    @Autowired
    private TransportistaService transportistaService;

    // Ejecuta cada 15 minutos (900.000 ms) con retraso inicial de 60 segundos
    @Scheduled(initialDelay = 60000, fixedDelay = 900000)
    public void ejecutarRevisionEnviosExpirados() {
        try {
            int expirados = transportistaService.verificarEnviosSinTransportista();
            if (expirados > 0) {
                log.info("Scheduler: Se procesaron {} envíos sin transportista.", expirados);
            }
        } catch (Exception e) {
            log.error("Error en EnvioExpiracionScheduler: {}", e.getMessage(), e);
        }
    }
}
