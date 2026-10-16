package com.example.demo.services;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.MovimientoBilletera;

import java.util.List;
import java.util.Optional;

/**
 * US-22: puerto de persistencia de billeteras. Solo permite lo estrictamente necesario:
 * obtener/crear la billetera, INSERTAR movimientos y sumar a un saldo. No hay operaciones
 * de actualizar ni borrar movimientos.
 */
public interface BilleteraAlmacen {

    /** Devuelve la billetera (usuario, rol), creándola vacía si no existe. Atómico. */
    Billetera obtenerOCrear(String usuarioId, Billetera.Rol rol);

    /**
     * Inserta el movimiento. Devuelve false (sin insertar) si ya existe uno con la misma
     * clave de idempotencia; así un reintento nunca duplica el asiento.
     */
    boolean insertarMovimiento(MovimientoBilletera movimiento);

    /** Suma (o resta, si es negativo) centavos al saldo indicado de forma atómica. */
    void incrementarSaldo(String billeteraId, Billetera.EstadoSaldo estado, long deltaCentavos);

    /** Lectura: billetera del usuario para el rol, sin crearla. */
    Optional<Billetera> buscar(String usuarioId, Billetera.Rol rol);

    /** Lectura: movimientos del usuario para el rol, más recientes primero. */
    List<MovimientoBilletera> listarMovimientos(String usuarioId, Billetera.Rol rol);
}
