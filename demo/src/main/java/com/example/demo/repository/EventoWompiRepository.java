package com.example.demo.repository;

import com.example.demo.Model.EventoWompi;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoWompiRepository extends MongoRepository<EventoWompi, String> {
}
