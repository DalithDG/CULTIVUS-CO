package com.example.demo.repository;

import com.example.demo.Model.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    /**
     * Búsqueda por hash. El campo token_hash tiene un índice único.
     * La vigencia (usado/expiración) se comprueba siempre en código.
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
