package com.example.demo.repository;

import com.example.demo.Model.Envio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvioRepository extends MongoRepository<Envio, String> {

    Optional<Envio> findByCompraId(String compraId);

    boolean existsByCompraId(String compraId);

    List<Envio> findByEstado(String estado);

    List<Envio> findByTransportistaId(String transportistaId);

    List<Envio> findByTransportistaIdOrderByFechaAsignacionDesc(String transportistaId);
}
