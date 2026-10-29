package com.example.demo.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * US-24 / Fase 0: Tarea periódica que verifica pedidos y envíos entregados
 * cuya ventana de reclamos (VENTANA_RECLAMOS_MINUTOS) haya vencido,
 * liberando los fondos retenidos al saldo DISPONIBLE del vendedor o transportista.
 */
@Component
public class BilleteraLiberacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(BilleteraLiberacionScheduler.class);

    private final BilleteraService billeteraService;

    @Autowired
    public BilleteraLiberacionScheduler(BilleteraService billeteraService) {
        this.billeteraService = billeteraService;
    }

    // Ejecuta cada 5 minutos (300.000 ms) con retraso inicial de 30 segundos
    @Scheduled(initialDelay = 30000, fixedDelay = 300000)
    public void ejecutarLiberacionFondos() {
        try {
            int liberados = billeteraService.procesarLiberacionesPendientes();
            if (liberados > 0) {
                log.info("BilleteraLiberacionScheduler: Se liberaron fondos de {} pedidos/envíos a DISPONIBLE.", liberados);
            }
        } catch (Exception e) {
            log.error("Error en BilleteraLiberacionScheduler: {}", e.getMessage(), e);
        }
    }
}

