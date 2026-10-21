package com.example.demo.repository;

import com.example.demo.Model.Billetera;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * US-22: lectura de billeteras. Los saldos se modifican solo con incrementos atómicos
 * desde BilleteraAlmacen, por eso aquí no se expone save/delete.
 */
@org.springframework.stereotype.Repository
public interface BilleteraRepository extends Repository<Billetera, String> {

    Optional<Billetera> findByUsuarioIdAndRol(String usuarioId, Billetera.Rol rol);
}
