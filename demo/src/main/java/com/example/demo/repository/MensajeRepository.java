package com.example.demo.repository;

import com.example.demo.Model.Mensaje;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MensajeRepository extends MongoRepository<Mensaje, String> {
    List<Mensaje> findByReceptorIdOrderByFechaDesc(String receptorId);
    List<Mensaje> findByReceptorIdIsNullOrderByFechaDesc();
    List<Mensaje> findByLeidoFalse();
}
