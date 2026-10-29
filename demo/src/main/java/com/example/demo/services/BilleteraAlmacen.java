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

    /** Comprueba si ya existe un movimiento con la clave de idempotencia dada. */
    boolean existeMovimientoPorClave(String claveIdempotencia);

    /** Busca un movimiento por su clave de idempotencia única. */
    Optional<MovimientoBilletera> buscarMovimientoPorClave(String claveIdempotencia);

    /**
     * Reserva saldo para retiro de forma atómica condicional:
     * Exige saldo_disponible_centavos >= montoCentavos.
     * Pasa saldo de DISPONIBLE a EN_PROCESO_RETIRO.
     * Devuelve true si la condición se cumplió y se modificó exactamente 1 documento.
     */
    boolean reservarParaRetiro(String billeteraId, long montoCentavos);

    /**
     * Confirma un retiro de forma atómica condicional:
     * Exige saldo_en_proceso_retiro_centavos >= montoCentavos.
     * Pasa saldo de EN_PROCESO_RETIRO a RETIRADO.
     * Devuelve true si se ejecutó exitosamente.
     */
    boolean confirmarRetiro(String billeteraId, long montoCentavos);

    /**
     * Revierte la reserva de un retiro de forma atómica condicional:
     * Exige saldo_en_proceso_retiro_centavos >= montoCentavos.
     * Pasa saldo de EN_PROCESO_RETIRO de vuelta a DISPONIBLE.
     * Devuelve true si se ejecutó exitosamente.
     */
    boolean revertirRetiro(String billeteraId, long montoCentavos);
}
