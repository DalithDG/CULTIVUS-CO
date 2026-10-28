package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Token de recuperación de contraseña.
 *
 * Se guarda UN SOLO documento por usuario (el _id es el id del usuario). Al
 * solicitar un enlace nuevo se reemplaza el documento anterior, por lo que
 * cualquier enlace previo deja de ser válido de inmediato.
 *
 * Nunca se almacena el token original: solo su hash SHA-256.
 *
 * El índice TTL borra documentos vencidos, pero la expiración SIEMPRE se
 * comprueba en la consulta/código; no se depende del TTL para autorizar.
 */
@Document(collection = "password_reset_tokens")
public class PasswordResetToken {

    /** Id del usuario propietario. Un único token activo por usuario. */
    @Id
    private String usuarioId;

    /** Hash SHA-256 en hexadecimal del token original. */
    @Indexed(unique = true)
    @Field("token_hash")
    private String tokenHash;

    /** Correo normalizado del usuario (referencia legible, no es un secreto). */
    @Field("email")
    private String email;

    /** Momento exacto en el que el enlace deja de ser válido. */
    @Indexed(expireAfterSeconds = 0)
    @Field("expira_en")
    private Instant expiraEn;

    @Field("creado_en")
    private Instant creadoEn;

    @Field("usado")
    private boolean usado;

    @Field("usado_en")
    private Instant usadoEn;

    public PasswordResetToken() {
        // Requerido por Spring Data MongoDB.
    }

    public PasswordResetToken(String usuarioId, String tokenHash, String email,
                              Instant creadoEn, Instant expiraEn) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.email = email;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
        this.usado = false;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(Instant expiraEn) {
        this.expiraEn = expiraEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public boolean isUsado() {
        return usado;
    }

    public void setUsado(boolean usado) {
        this.usado = usado;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public void setUsadoEn(Instant usadoEn) {
        this.usadoEn = usadoEn;
    }
}
