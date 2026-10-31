package com.example.demo.repository;

import com.example.demo.Model.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificacionRepository extends MongoRepository<Notificacion, String> {
    List<Notificacion> findByUsuarioIdOrderByFechaDesc(String usuarioId);
    List<Notificacion> findByUsuarioIdIsNullOrderByFechaDesc();
    List<Notificacion> findByLeidaFalse();

    long countByUsuarioIdAndLeidaFalse(String usuarioId);
}
