package com.example.demo.repository;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.SolicitudRetiro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitudRetiroRepository extends MongoRepository<SolicitudRetiro, String> {

    List<SolicitudRetiro> findByUsuarioIdAndRolOrderByFechaSolicitudDesc(String usuarioId, Billetera.Rol rol);

    Optional<SolicitudRetiro> findByUsuarioIdAndRolAndEstado(String usuarioId, Billetera.Rol rol, SolicitudRetiro.Estado estado);

    boolean existsByUsuarioIdAndRolAndEstado(String usuarioId, Billetera.Rol rol, SolicitudRetiro.Estado estado);

    Page<SolicitudRetiro> findByEstadoOrderByFechaSolicitudDesc(SolicitudRetiro.Estado estado, Pageable pageable);

    Page<SolicitudRetiro> findAllByOrderByFechaSolicitudDesc(Pageable pageable);

    long countByEstado(SolicitudRetiro.Estado estado);

    Optional<SolicitudRetiro> findByNumeroComprobante(String numeroComprobante);
}

