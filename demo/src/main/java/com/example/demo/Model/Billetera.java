package com.example.demo.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * US-22: billetera interna de un usuario para un rol concreto.
 *
 * Una billetera por (usuario, rol). Hoy solo existe el rol VENDEDOR; para sumar TRANSPORTISTA
 * basta con agregar el valor al enum {@link Rol}: el rol se guarda como texto y la clave de la
 * billetera es (usuarioId, rol), así que no hace falta migrar datos existentes.
 *
 * Todo el dinero va en centavos enteros. Los saldos NO se editan desde código de aplicación:
 * se modifican solo con incrementos atómicos desde BilleteraAlmacen, siempre acompañados de un
 * movimiento inmutable que los justifica.
 *
 * US-22 solo usa el saldo EN_RETENCION; liberación (DISPONIBLE) y retiro (RETIRADO) son de la US-24.
 */
@Document(collection = "billeteras")
public class Billetera {

    public enum Rol {
        VENDEDOR
        // TRANSPORTISTA: se agrega aquí cuando llegue su historia (sin migración).
    }

    public enum EstadoSaldo {
        EN_RETENCION, DISPONIBLE, RETIRADO
    }

    @Id
    private String id;

    @Field("usuario_id")
    private String usuarioId;

    @Field("rol")
    private Rol rol;

    @Field("saldo_retencion_centavos")
    private long saldoRetencionCentavos;

    @Field("saldo_disponible_centavos")
    private long saldoDisponibleCentavos;

    @Field("saldo_retirado_centavos")
    private long saldoRetiradoCentavos;

    @Field("created_at")
    private LocalDateTime createdAt;

    protected Billetera() {
    }

    public Billetera(String usuarioId, Rol rol) {
        this.usuarioId = usuarioId;
        this.rol = rol;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public Rol getRol() { return rol; }
    public long getSaldoRetencionCentavos() { return saldoRetencionCentavos; }
    public long getSaldoDisponibleCentavos() { return saldoDisponibleCentavos; }
    public long getSaldoRetiradoCentavos() { return saldoRetiradoCentavos; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Helpers de vista (pesos)
    public double getSaldoRetencion() { return saldoRetencionCentavos / 100.0; }
    public double getSaldoDisponible() { return saldoDisponibleCentavos / 100.0; }
    public double getSaldoRetirado() { return saldoRetiradoCentavos / 100.0; }

    public long saldoDe(EstadoSaldo estado) {
        switch (estado) {
            case EN_RETENCION: return saldoRetencionCentavos;
            case DISPONIBLE: return saldoDisponibleCentavos;
            default: return saldoRetiradoCentavos;
        }
    }
}
