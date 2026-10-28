package com.example.demo.services;

import com.example.demo.Model.PasswordResetToken;
import com.example.demo.repository.PasswordResetTokenRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementación MongoDB del almacén de tokens.
 *
 * Se usan nombres de campo explícitos (token_hash, expira_en) porque son los
 * almacenados por {@link PasswordResetToken}. El consumo se hace con
 * findAndModify, que es atómico a nivel de documento en MongoDB.
 */
@Component
public class MongoPasswordResetTokenStore implements PasswordResetTokenStore {

    private final PasswordResetTokenRepository repository;
    private final MongoTemplate mongoTemplate;

    public MongoPasswordResetTokenStore(PasswordResetTokenRepository repository,
                                        MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void guardar(PasswordResetToken token) {
        // El _id es el usuarioId: al existir, save() reemplaza el documento
        // anterior y deja inválido cualquier enlace previo.
        repository.save(token);
    }

    @Override
    public Optional<PasswordResetToken> buscarVigente(String tokenHash, Instant ahora) {
        if (tokenHash == null || ahora == null) {
            return Optional.empty();
        }
        return repository.findByTokenHash(tokenHash)
                .filter(token -> !token.isUsado())
                .filter(token -> token.getExpiraEn() != null && token.getExpiraEn().isAfter(ahora));
    }

    @Override
    public Optional<PasswordResetToken> consumirAtomicamente(String tokenHash, Instant ahora) {
        if (tokenHash == null || ahora == null) {
            return Optional.empty();
        }

        Query query = Query.query(
                Criteria.where("token_hash").is(tokenHash)
                        .and("usado").is(false)
                        .and("expira_en").gt(ahora));

        Update update = new Update()
                .set("usado", true)
                .set("usado_en", ahora);

        PasswordResetToken consumido = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                PasswordResetToken.class);

        return Optional.ofNullable(consumido);
    }

    @Override
    public void invalidarPendientes(String usuarioId, Instant ahora) {
        if (usuarioId == null || ahora == null) {
            return;
        }
        Query query = Query.query(
                Criteria.where("_id").is(usuarioId)
                        .and("usado").is(false));

        Update update = new Update()
                .set("usado", true)
                .set("usado_en", ahora);

        mongoTemplate.updateFirst(query, update, PasswordResetToken.class);
    }
}
