package com.example.demo.Model.embebidos;

import com.example.demo.Model.Banco;
import com.example.demo.Model.TipoCuenta;
import com.example.demo.Model.TipoDocumento;

/**
 * US-24 / Fase 2: Datos estructurados de cuenta bancaria para retiros de billetera.
 * Se embebe en PerfilVendedor, PerfilTransportista y como snapshot inmutable en SolicitudRetiro.
 */
public class CuentaBancaria {

    private Banco banco;
    private String bancoOtroNombre;
    private TipoCuenta tipo;
    private String numero;
    private String titular;
    private TipoDocumento tipoDocumento;
    private String numeroDocumento;

    public CuentaBancaria() {
    }

    public CuentaBancaria(Banco banco, String bancoOtroNombre, TipoCuenta tipo,
                          String numero, String titular,
                          TipoDocumento tipoDocumento, String numeroDocumento) {
        this.banco = banco;
        this.bancoOtroNombre = (banco == Banco.OTRO && bancoOtroNombre != null) ? bancoOtroNombre.trim() : null;
        this.tipo = tipo;
        this.numero = numero != null ? numero.trim() : null;
        this.titular = titular != null ? titular.trim() : null;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento != null ? numeroDocumento.trim() : null;
    }

    public CuentaBancaria(Banco banco, TipoCuenta tipo, String numero,
                          String titular, TipoDocumento tipoDocumento, String numeroDocumento) {
        this(banco, null, tipo, numero, titular, tipoDocumento, numeroDocumento);
    }

    /**
     * Crea una copia independiente (snapshot) de esta cuenta bancaria.
     */
    public CuentaBancaria copiar() {
        return new CuentaBancaria(
                this.banco,
                this.bancoOtroNombre,
                this.tipo,
                this.numero,
                this.titular,
                this.tipoDocumento,
                this.numeroDocumento
        );
    }

    /**
     * Nombre visible de la entidad financiera (usa bancoOtroNombre cuando el banco es OTRO).
     */
    public String getNombreEntidad() {
        if (banco == null) {
            return "";
        }
        if (banco == Banco.OTRO) {
            return (bancoOtroNombre != null && !bancoOtroNombre.trim().isEmpty())
                    ? bancoOtroNombre.trim()
                    : Banco.OTRO.getNombreVisible();
        }
        return banco.getNombreVisible();
    }

    /**
     * Enmascara un número de cuenta dejando visibles únicamente los últimos 4 caracteres (ej. ****1234).
     */
    public static String enmascarar(String numero) {
        if (numero == null || numero.trim().isEmpty()) {
            return "****";
        }
        String limpio = numero.trim();
        if (limpio.length() <= 4) {
            return "****" + limpio;
        }
        return "****" + limpio.substring(limpio.length() - 4);
    }

    public String getNumeroEnmascarado() {
        return enmascarar(this.numero);
    }

    /**
     * Resumen corto seguro para vistas, notificaciones y logs (nunca expone el número completo).
     */
    public String getResumenEnmascarado() {
        String entidad = getNombreEntidad();
        String tipoStr = tipo != null ? tipo.getEtiqueta() : "";
        return (entidad.isEmpty() ? "Cuenta" : entidad)
                + (tipoStr.isEmpty() ? "" : " · " + tipoStr)
                + " (" + getNumeroEnmascarado() + ")";
    }

    /**
     * Validación única reutilizable de todas las reglas de negocio de una cuenta bancaria.
     * Lanza IllegalArgumentException si algún campo es inválido.
     */
    public void validar() {
        if (banco == null) {
            throw new IllegalArgumentException("Debe seleccionar una entidad bancaria");
        }

        if (banco == Banco.OTRO) {
            String otro = bancoOtroNombre != null ? bancoOtroNombre.trim() : "";
            if (otro.length() < 3 || otro.length() > 60) {
                throw new IllegalArgumentException("Debe indicar el nombre de la entidad financiera (entre 3 y 60 caracteres)");
            }
            this.bancoOtroNombre = otro;
        } else {
            this.bancoOtroNombre = null;
        }

        if (tipo == null) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de cuenta");
        }

        String num = numero != null ? numero.trim() : "";
        if (num.isEmpty()) {
            throw new IllegalArgumentException("El número de cuenta es obligatorio");
        }

        if (banco.esBilleteraDigital()) {
            if (tipo != TipoCuenta.DEPOSITO_ELECTRONICO) {
                throw new IllegalArgumentException(banco.getNombreVisible() + " solo permite tipo de cuenta Depósito electrónico");
            }
            if (!num.matches("^3\\d{9}$")) {
                throw new IllegalArgumentException("Para " + banco.getNombreVisible() + " el número debe ser un celular de 10 dígitos que empiece por 3");
            }
        } else {
            if (!num.matches("^\\d{6,20}$")) {
                throw new IllegalArgumentException("El número de cuenta debe contener solo dígitos (entre 6 y 20 dígitos)");
            }
        }
        this.numero = num;

        String tit = titular != null ? titular.trim() : "";
        if (tit.length() < 3 || tit.length() > 100) {
            throw new IllegalArgumentException("El nombre del titular debe tener entre 3 y 100 caracteres");
        }
        this.titular = tit;

        if (tipoDocumento == null) {
            throw new IllegalArgumentException("Debe seleccionar el tipo de documento del titular");
        }

        String doc = numeroDocumento != null ? numeroDocumento.trim() : "";
        if (!doc.matches("^[a-zA-Z0-9]{5,15}$")) {
            throw new IllegalArgumentException("El número de documento debe ser alfanumérico y tener entre 5 y 15 caracteres");
        }
        this.numeroDocumento = doc;
    }

    /**
     * Devuelve true si la cuenta tiene todos sus campos válidos y listos para operar retiros.
     */
    public boolean esCompleta() {
        try {
            validar();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Getters y Setters

    public Banco getBanco() { return banco; }
    public void setBanco(Banco banco) { this.banco = banco; }

    public String getBancoOtroNombre() { return bancoOtroNombre; }
    public void setBancoOtroNombre(String bancoOtroNombre) { this.bancoOtroNombre = bancoOtroNombre; }

    public TipoCuenta getTipo() { return tipo; }
    public void setTipo(TipoCuenta tipo) { this.tipo = tipo; }

    // Alias para plantillas si se accede como tipoCuenta
    public TipoCuenta getTipoCuenta() { return tipo; }
    public void setTipoCuenta(TipoCuenta tipo) { this.tipo = tipo; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    // Alias para plantillas si se accede como numeroCuenta
    public String getNumeroCuenta() { return numero; }
    public void setNumeroCuenta(String numero) { this.numero = numero; }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }

    public TipoDocumento getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(TipoDocumento tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    @Override
    public String toString() {
        return "CuentaBancaria{" +
                "entidad='" + getNombreEntidad() + '\'' +
                ", tipo=" + tipo +
                ", numero='" + getNumeroEnmascarado() + '\'' +
                ", titular='" + titular + '\'' +
                '}';
    }
}

