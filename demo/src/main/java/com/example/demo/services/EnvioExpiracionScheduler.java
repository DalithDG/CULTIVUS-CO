package com.example.demo.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea periódica que verifica si existen envíos pendientes de asignación
 * que hayan superado las horas máximas configuradas (HORAS_MAX_SIN_TRANSPORTISTA),
 * envíos asignados sin recolección (HORAS_MAX_ASIGNADO_SIN_RECOGIDA)
 * y reconcilia envíos faltantes de compras a domicilio.
 */
@Component
public class EnvioExpiracionScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnvioExpiracionScheduler.class);

    @Autowired
    private TransportistaService transportistaService;

    @Autowired(required = false)
    private PedidoEstadoService pedidoEstadoService;

    // Ejecuta cada 15 minutos (900.000 ms) con retraso inicial de 60 segundos
    @Scheduled(initialDelay = 60000, fixedDelay = 900000)
    public void ejecutarRevisionEnviosExpirados() {
        try {
            int sinTrans = transportistaService.verificarEnviosSinTransportista();
            if (sinTrans > 0) {
                log.info("Scheduler: Se procesaron {} envíos sin transportista.", sinTrans);
            }

            int timeoutAsignados = transportistaService.verificarEnviosAsignadosExpirados();
            if (timeoutAsignados > 0) {
                log.info("Scheduler: Se devolvieron {} envíos a bolsa por timeout de recolección.", timeoutAsignados);
            }

        } catch (Exception e) {
            log.error("Error en EnvioExpiracionScheduler: {}", e.getMessage(), e);
        }
    }

    // Ejecuta cada hora (3.600.000 ms) con retraso inicial de 2 minutos
    @Scheduled(initialDelay = 120000, fixedDelay = 3600000)
    public void ejecutarReconciliacionEnvios() {
        try {
            int reconciliados = transportistaService.reconciliarEnviosFaltantes();
            if (reconciliados > 0) {
                log.info("Scheduler: Se reconciliaron {} envíos faltantes de compras a domicilio.", reconciliados);
            }
        } catch (Exception e) {
            log.error("Error en reconciliación automática de envíos: {}", e.getMessage(), e);
        }
    }
}
