package com.example.demo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.Model.Categoria;

import java.util.Optional;

@Repository
public interface CategoriaRepository extends MongoRepository<Categoria, String> {

    Optional<Categoria> findByNombre(String nombre);

    boolean existsByNombre(String nombre);
}