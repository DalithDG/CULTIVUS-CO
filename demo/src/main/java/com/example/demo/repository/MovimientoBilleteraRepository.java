package com.example.demo.repository;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.MovimientoBilletera;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * US-22: repositorio de SOLO LECTURA del libro de movimientos.
 * Extiende Repository (no MongoRepository) a propósito: no expone save, update ni delete.
 * Los movimientos se insertan únicamente por BilleteraAlmacen.insertarMovimiento.
 */
@org.springframework.stereotype.Repository
public interface MovimientoBilleteraRepository extends Repository<MovimientoBilletera, String> {

    List<MovimientoBilletera> findByUsuarioIdAndRolOrderByFechaDesc(String usuarioId, Billetera.Rol rol);

    boolean existsByClaveIdempotencia(String claveIdempotencia);

    long countByPedidoId(String pedidoId);
}
