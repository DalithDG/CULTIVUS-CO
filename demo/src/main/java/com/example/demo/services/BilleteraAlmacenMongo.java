package com.example.demo.services;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.MovimientoBilletera;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Implementación Mongo del almacén de billeteras (incrementos atómicos con $inc). */
@Component
public class BilleteraAlmacenMongo implements BilleteraAlmacen {

    private static final Logger log = LoggerFactory.getLogger(BilleteraAlmacenMongo.class);

    private final MongoTemplate mongoTemplate;
    private final com.example.demo.repository.BilleteraRepository billeteraRepository;
    private final com.example.demo.repository.MovimientoBilleteraRepository movimientoRepository;

    public BilleteraAlmacenMongo(MongoTemplate mongoTemplate,
                                 com.example.demo.repository.BilleteraRepository billeteraRepository,
                                 com.example.demo.repository.MovimientoBilleteraRepository movimientoRepository) {
        this.mongoTemplate = mongoTemplate;
        this.billeteraRepository = billeteraRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @Override
    public Optional<Billetera> buscar(String usuarioId, Billetera.Rol rol) {
        return billeteraRepository.findByUsuarioIdAndRol(usuarioId, rol);
    }

    @Override
    public List<MovimientoBilletera> listarMovimientos(String usuarioId, Billetera.Rol rol) {
        return movimientoRepository.findByUsuarioIdAndRolOrderByFechaDesc(usuarioId, rol);
    }

    /** Garantiza los índices únicos que sostienen la idempotencia y la unicidad por (usuario, rol). */
    @PostConstruct
    void asegurarIndices() {
        try {
            mongoTemplate.indexOps(Billetera.class)
                    .ensureIndex(new Index().on("usuario_id", org.springframework.data.domain.Sort.Direction.ASC)
                            .on("rol", org.springframework.data.domain.Sort.Direction.ASC).unique());
            mongoTemplate.indexOps(MovimientoBilletera.class)
                    .ensureIndex(new Index().on("clave_idempotencia", org.springframework.data.domain.Sort.Direction.ASC)
                            .unique().sparse());
        } catch (Exception e) {
            log.warn("No se pudieron crear los índices de billetera: {}", e.getMessage());
        }
    }

    @Override
    public Billetera obtenerOCrear(String usuarioId, Billetera.Rol rol) {
        Query q = new Query(Criteria.where("usuario_id").is(usuarioId).and("rol").is(rol.name()));
        Update u = new Update()
                .setOnInsert("saldo_retencion_centavos", 0L)
                .setOnInsert("saldo_disponible_centavos", 0L)
                .setOnInsert("saldo_retirado_centavos", 0L)
                .setOnInsert("created_at", LocalDateTime.now());
        return mongoTemplate.findAndModify(q, u,
                FindAndModifyOptions.options().upsert(true).returnNew(true), Billetera.class);
    }

    @Override
    public boolean insertarMovimiento(MovimientoBilletera movimiento) {
        try {
            mongoTemplate.insert(movimiento);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public void incrementarSaldo(String billeteraId, Billetera.EstadoSaldo estado, long deltaCentavos) {
        String campo;
        switch (estado) {
            case EN_RETENCION: campo = "saldo_retencion_centavos"; break;
            case DISPONIBLE: campo = "saldo_disponible_centavos"; break;
            default: campo = "saldo_retirado_centavos"; break;
        }
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(billeteraId)),
                new Update().inc(campo, deltaCentavos), Billetera.class);
    }

    @Override
    public boolean existeMovimientoPorClave(String claveIdempotencia) {
        if (claveIdempotencia == null || claveIdempotencia.isBlank()) {
            return false;
        }
        return mongoTemplate.exists(
                new Query(Criteria.where("clave_idempotencia").is(claveIdempotencia)),
                MovimientoBilletera.class
        );
    }
}
