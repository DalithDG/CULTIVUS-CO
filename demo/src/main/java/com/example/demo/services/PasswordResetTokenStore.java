package com.example.demo.services;

import com.example.demo.Model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de persistencia de tokens de recuperación.
 *
 * Se abstrae para poder probar el flujo sin MongoDB y para garantizar que el
 * consumo del token sea una operación atómica en la implementación real.
 */
public interface PasswordResetTokenStore {

    /**
     * Guarda (o reemplaza) el token del usuario. Un usuario tiene como máximo
     * un token vigente: el documento anterior se sobrescribe.
     */
    void guardar(PasswordResetToken token);

    /**
     * Devuelve el token solo si no fue usado y no está vencido.
     * No consume el token.
     */
    Optional<PasswordResetToken> buscarVigente(String tokenHash, Instant ahora);

    /**
     * Consume el token de forma atómica: marca usado=true únicamente si en ese
     * instante sigue sin usarse y no ha expirado. Dos peticiones concurrentes
     * con el mismo token: solo una recibe el documento.
     */
    Optional<PasswordResetToken> consumirAtomicamente(String tokenHash, Instant ahora);

    /**
     * Invalida cualquier otro token pendiente del usuario. En el diseño actual
     * solo existe uno, pero se mantiene por seguridad/evolución.
     */
    void invalidarPendientes(String usuarioId, Instant ahora);
}
