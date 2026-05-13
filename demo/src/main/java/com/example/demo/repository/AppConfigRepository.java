package com.example.demo.repository;

import com.example.demo.Model.AppConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppConfigRepository extends MongoRepository<AppConfig, String> {
    Optional<AppConfig> findByClave(String clave);
}
