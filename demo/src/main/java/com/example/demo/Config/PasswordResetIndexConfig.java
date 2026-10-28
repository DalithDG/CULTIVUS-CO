package com.example.demo.Config;

import com.example.demo.Model.PasswordResetToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Crea los índices que garantizan el rendimiento y la unicidad del token.
 *
 * No se activa auto-index-creation global para no alterar índices de otras
 * colecciones existentes. Si MongoDB no está disponible al arrancar, se
 * registra una advertencia y la aplicación sigue funcionando (las consultas
 * siguen validando la expiración en código).
 */
@Configuration
public class PasswordResetIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetIndexConfig.class);

    @Bean
    public ApplicationRunner passwordResetIndexInitializer(MongoTemplate mongoTemplate) {
        return args -> {
            try {
                IndexOperations indexOps = mongoTemplate.indexOps(PasswordResetToken.class);

                // Búsqueda por hash y garantía de que no haya dos tokens iguales.
                indexOps.ensureIndex(new Index()
                        .on("tokenHash", Sort.Direction.ASC)
                        .unique()
                        .named("idx_password_reset_token_hash"));

                // Limpieza automática de documentos vencidos. La expiración
                // real se valida siempre en cada operación.
                indexOps.ensureIndex(new Index()
                        .on("expiraEn", Sort.Direction.ASC)
                        .expire(0L)
                        .named("ttl_password_reset_expira_en"));
            } catch (Exception e) {
                // Sin datos sensibles en el mensaje.
                log.warn("No se pudieron crear/verificar los índices de password_reset_tokens. "
                        + "La recuperación seguirá validando la expiración en código.");
            }
        };
    }
}
