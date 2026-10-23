package com.example.demo.repository;

import com.example.demo.Model.TarifaEnvio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TarifaEnvioRepository extends MongoRepository<TarifaEnvio, String> {

    Optional<TarifaEnvio> findByZonaOrigenAndZonaDestino(Integer zonaOrigen, Integer zonaDestino);
}
