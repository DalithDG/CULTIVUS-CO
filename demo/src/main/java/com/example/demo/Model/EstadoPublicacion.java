package com.example.demo.Model;

/**
 * Estado de publicaci\u00f3n de una oferta (US-06).
 * Las ofertas de productos de la lista por defecto se publican autom\u00e1ticamente;
 * las ligadas a un producto sugerido quedan PENDIENTE hasta que el admin decida.
 */
public enum EstadoPublicacion {
    PUBLICADO,
    PENDIENTE,
    RECHAZADO
}

