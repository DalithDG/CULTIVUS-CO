package com.example.demo.repository;

import com.example.demo.Model.ZonaMunicipio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZonaMunicipioRepository extends MongoRepository<ZonaMunicipio, String> {

    Optional<ZonaMunicipio> findByMunicipioNormalizado(String municipioNormalizado);
}
