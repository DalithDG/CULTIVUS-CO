package com.example.demo.services;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.MovimientoBilletera;
import com.example.demo.Model.SolicitudRetiro;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.CuentaBancaria;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.repository.SolicitudRetiroRepository;
import com.example.demo.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * US-24 / Fase 4: Servicio de negocio para retiros simulados de billetera.
 * Opera siempre con rol explícito (un usuario puede ser VENDEDOR y TRANSPORTISTA a la vez).
 * Garantiza consistencia sin transacciones multi-documento mediante:
 * - Operaciones condicionales atómicas sobre saldos en BilleteraAlmacen
 * - Transiciones de estado condicionadas a PENDIENTE con findAndModify
 * - Claves de idempotencia únicas por solicitud en MovimientoBilletera
 * - Compensación inmediata si falla la persistencia de la solicitud
 */
@Service
public class RetiroService {

    private static final Logger log = LoggerFactory.getLogger(RetiroService.class);

    public static final String CLAVE_RETIRO_MINIMO = "RETIRO_MINIMO_COP";
    public static final long RETIRO_MINIMO_DEFECTO_COP = 50_000L;

    private final BilleteraAlmacen billeteraAlmacen;
    private final SolicitudRetiroRepository solicitudRepository;
    private final UsuarioRepository usuarioRepository;
    private final AppConfigService configService;
    private final NotificacionService notificacionService;
    private final MongoTemplate mongoTemplate;

    private final Object transicionLock = new Object();

    @Autowired
    public RetiroService(BilleteraAlmacen billeteraAlmacen,
                         SolicitudRetiroRepository solicitudRepository,
                         UsuarioRepository usuarioRepository,
                         AppConfigService configService,
                         @Autowired(required = false) NotificacionService notificacionService,
                         @Autowired(required = false) MongoTemplate mongoTemplate) {
        this.billeteraAlmacen = billeteraAlmacen;
        this.solicitudRepository = solicitudRepository;
        this.usuarioRepository = usuarioRepository;
        this.configService = configService;
        this.notificacionService = notificacionService;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Crea explícitamente los índices de solicitudes_retiro:
     * 1. Único parcial (usuario_id, rol) cuando estado == PENDIENTE
     * 2. Único disperso en numero_comprobante
     */
    @PostConstruct
    void asegurarIndices() {
        if (mongoTemplate == null) {
            return;
        }
        try {
            Document keys = new Document("usuario_id", 1).append("rol", 1);
            CompoundIndexDefinition partialUnique = (CompoundIndexDefinition) new CompoundIndexDefinition(keys)
                    .named("idx_retiro_pendiente_unico")
                    .unique()
                    .partial(PartialIndexFilter.of(Criteria.where("estado").is(SolicitudRetiro.Estado.PENDIENTE.name())));
            mongoTemplate.indexOps(SolicitudRetiro.class).ensureIndex(partialUnique);

            Index comprobanteIdx = new Index()
                    .on("numero_comprobante", Sort.Direction.ASC)
                    .named("idx_retiro_comprobante_unico")
                    .unique()
                    .sparse();
            mongoTemplate.indexOps(SolicitudRetiro.class).ensureIndex(comprobanteIdx);
        } catch (Exception e) {
            log.warn("No se pudieron asegurar los índices de solicitudes_retiro: {}", e.getMessage());
        }
    }

    public long obtenerRetiroMinimoCop() {
        if (configService == null) {
            return RETIRO_MINIMO_DEFECTO_COP;
        }
        double val = configService.obtenerValorDouble(CLAVE_RETIRO_MINIMO, RETIRO_MINIMO_DEFECTO_COP);
        long cop = Math.round(val);
        return Math.max(1_000L, cop);
    }

    public long obtenerRetiroMinimoCentavos() {
        return obtenerRetiroMinimoCop() * 100L;
    }

    public boolean existeSolicitudPendiente(String usuarioId, Billetera.Rol rol) {
        if (usuarioId == null || rol == null) return false;
        return solicitudRepository.existsByUsuarioIdAndRolAndEstado(usuarioId, rol, SolicitudRetiro.Estado.PENDIENTE);
    }

    public Optional<SolicitudRetiro> obtenerSolicitudPendiente(String usuarioId, Billetera.Rol rol) {
        if (usuarioId == null || rol == null) return Optional.empty();
        return solicitudRepository.findByUsuarioIdAndRolAndEstado(usuarioId, rol, SolicitudRetiro.Estado.PENDIENTE);
    }

    public List<SolicitudRetiro> listarPorUsuarioYRol(String usuarioId, Billetera.Rol rol) {
        return solicitudRepository.findByUsuarioIdAndRolOrderByFechaSolicitudDesc(usuarioId, rol);
    }

    public Page<SolicitudRetiro> listarParaAdmin(SolicitudRetiro.Estado estado, Pageable pageable) {
        if (estado != null) {
            return solicitudRepository.findByEstadoOrderByFechaSolicitudDesc(estado, pageable);
        }
        return solicitudRepository.findAllByOrderByFechaSolicitudDesc(pageable);
    }

    public long contarPendientes() {
        return solicitudRepository.countByEstado(SolicitudRetiro.Estado.PENDIENTE);
    }

    /**
     * Parsea una cadena de monto en pesos verificando que sea un número entero positivo sin decimales.
     */
    public static long parsearMontoPesosEntero(String montoTexto) {
        if (montoTexto == null || montoTexto.trim().isEmpty()) {
            throw new IllegalArgumentException("Debes indicar el monto a retirar");
        }
        String limpio = montoTexto.trim();
        if (limpio.contains(".") || limpio.contains(",")) {
            throw new IllegalArgumentException("El monto debe ser un número entero en pesos colombianos (sin decimales)");
        }
        try {
            BigDecimal bd = new BigDecimal(limpio);
            if (bd.scale() > 0 || bd.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("El monto debe ser un número entero en pesos colombianos (sin decimales)");
            }
            long pesos = bd.longValueExact();
            if (pesos <= 0) {
                throw new IllegalArgumentException("El monto a retirar debe ser mayor a cero");
            }
            return pesos;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("El monto ingresado no es un número entero válido en pesos");
        }
    }

    /**
     * Sobrecarga que recibe el monto como texto desde un formulario HTTP.
     */
    public SolicitudRetiro solicitarRetiro(String usuarioId, Billetera.Rol rol, String montoPesosTexto, boolean retirarTodo) {
        Long montoPesos = retirarTodo ? null : parsearMontoPesosEntero(montoPesosTexto);
        return solicitarRetiro(usuarioId, rol, montoPesos, retirarTodo);
    }

    /**
     * Solicita un retiro simulado para el usuario y rol dados.
     */
    public SolicitudRetiro solicitarRetiro(String usuarioId, Billetera.Rol rol, Long montoPesos, boolean retirarTodo) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("El usuario es obligatorio");
        }
        if (rol == null) {
            throw new IllegalArgumentException("El rol de la billetera es obligatorio");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        CuentaBancaria cuentaPerfil = obtenerCuentaDePerfil(usuario, rol);
        if (cuentaPerfil == null || !cuentaPerfil.esCompleta()) {
            throw new IllegalStateException("Debes registrar una cuenta bancaria completa antes de solicitar un retiro");
        }

        Billetera billetera = billeteraAlmacen.obtenerOCrear(usuarioId, rol);
        long disponibleCentavos = billetera.getSaldoDisponibleCentavos();
        long minimoCentavos = obtenerRetiroMinimoCentavos();

        long montoCentavos;
        if (retirarTodo) {
            montoCentavos = disponibleCentavos;
            if (montoCentavos <= 0) {
                throw new IllegalArgumentException("No tienes saldo disponible para retirar");
            }
            if (montoCentavos % 100 != 0) {
                throw new IllegalArgumentException("El saldo disponible debe ser un monto entero en pesos");
            }
        } else {
            if (montoPesos == null || montoPesos <= 0) {
                throw new IllegalArgumentException("El monto a retirar debe ser un entero mayor a cero");
            }
            montoCentavos = Math.multiplyExact(montoPesos, 100L);
        }

        if (montoCentavos < minimoCentavos) {
            throw new IllegalArgumentException("El monto mínimo para retirar es de $"
                    + String.format(Locale.GERMANY, "%,d", obtenerRetiroMinimoCop()) + " COP");
        }

        if (montoCentavos > disponibleCentavos) {
            throw new IllegalArgumentException("El monto solicitado supera tu saldo disponible");
        }

        if (existeSolicitudPendiente(usuarioId, rol)) {
            throw new IllegalStateException("Ya tienes una solicitud de retiro pendiente de revisión");
        }

        // Operación atómica condicional en el almacén (solo descuenta si disponible >= montoCentavos)
        boolean reservado = billeteraAlmacen.reservarParaRetiro(billetera.getId(), montoCentavos);
        if (!reservado) {
            throw new IllegalStateException("Saldo disponible insuficiente para reservar el retiro");
        }

        SolicitudRetiro solicitud = new SolicitudRetiro(
                usuarioId,
                rol,
                usuario.getNombre(),
                usuario.getEmail(),
                montoCentavos,
                cuentaPerfil.copiar()
        );
        solicitud.setId(new ObjectId().toHexString());

        try {
            synchronized (transicionLock) {
                if (existeSolicitudPendiente(usuarioId, rol)) {
                    throw new DuplicateKeyException("Ya existe una solicitud pendiente para este usuario y rol");
                }
                if (mongoTemplate != null) {
                    mongoTemplate.insert(solicitud);
                } else {
                    solicitudRepository.save(solicitud);
                }
            }
        } catch (RuntimeException e) {
            // Compensación inmediata: devolver el saldo de EN_PROCESO_RETIRO a DISPONIBLE
            billeteraAlmacen.revertirRetiro(billetera.getId(), montoCentavos);
            if (e instanceof DuplicateKeyException) {
                throw new IllegalStateException("Ya tienes una solicitud de retiro pendiente de revisión");
            }
            throw e;
        }

        String descReserva = "Reserva para solicitud de retiro hacia " + solicitud.getCuentaDestino().getResumenEnmascarado();

        MovimientoBilletera movDisp = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.RETIRO_RESERVADO,
                Billetera.EstadoSaldo.DISPONIBLE,
                -montoCentavos,
                null,
                null,
                descReserva,
                "RETIRO_RESERVADO_DISP:" + solicitud.getId(),
                solicitud.getId()
        );
        billeteraAlmacen.insertarMovimiento(movDisp);

        MovimientoBilletera movProc = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.RETIRO_RESERVADO,
                Billetera.EstadoSaldo.EN_PROCESO_RETIRO,
                montoCentavos,
                null,
                null,
                descReserva,
                "RETIRO_RESERVADO_PROC:" + solicitud.getId(),
                solicitud.getId()
        );
        billeteraAlmacen.insertarMovimiento(movProc);

        log.info("Solicitud de retiro {} creada para usuario {} ({}) por {} centavos hacia {}",
                solicitud.getId(), usuarioId, rol, montoCentavos, solicitud.getCuentaDestino().getResumenEnmascarado());

        notificarSolicitudCreada(solicitud);
        return solicitud;
    }

    /**
     * Aprueba una solicitud de retiro pendiente de forma atómica.
     */
    public SolicitudRetiro aprobarRetiro(String solicitudId, String adminId, String adminNombre, String referenciaBancaria) {
        if (solicitudId == null || solicitudId.isBlank()) {
            throw new IllegalArgumentException("El identificador de la solicitud es obligatorio");
        }

        LocalDateTime ahora = LocalDateTime.now();
        String sufijo = solicitudId.trim().length() >= 6
                ? solicitudId.trim().substring(solicitudId.trim().length() - 6).toUpperCase(Locale.ROOT)
                : String.format("%6s", solicitudId.trim()).replace(' ', '0').toUpperCase(Locale.ROOT);
        String numeroComprobante = "RET-" + ahora.getYear() + "-" + sufijo;
        String refLimpia = (referenciaBancaria != null && !referenciaBancaria.trim().isEmpty())
                ? referenciaBancaria.trim()
                : null;

        SolicitudRetiro aprobada = transicionarEstadoPendiente(
                solicitudId.trim(),
                SolicitudRetiro.Estado.APROBADO,
                ahora,
                adminId,
                adminNombre,
                null,
                refLimpia,
                numeroComprobante
        );

        if (aprobada == null) {
            throw new IllegalStateException("La solicitud de retiro no existe o ya fue procesada por otro administrador");
        }

        Billetera billetera = billeteraAlmacen.obtenerOCrear(aprobada.getUsuarioId(), aprobada.getRol());
        long monto = aprobada.getMontoCentavos();

        boolean confirmado = billeteraAlmacen.confirmarRetiro(billetera.getId(), monto);
        if (!confirmado) {
            log.error("Fallo crítico al confirmar retiro {}: saldo en proceso insuficiente ({} centavos) en billetera {}",
                    solicitudId, monto, billetera.getId());
            throw new IllegalStateException("No se pudo confirmar el retiro en la billetera: saldo en proceso insuficiente");
        }

        String descPago = "Retiro aprobado (" + numeroComprobante + ") hacia "
                + (aprobada.getCuentaDestino() != null ? aprobada.getCuentaDestino().getResumenEnmascarado() : "cuenta registrada");

        MovimientoBilletera movProc = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.RETIRO_PAGADO,
                Billetera.EstadoSaldo.EN_PROCESO_RETIRO,
                -monto,
                null,
                null,
                descPago,
                "RETIRO_PAGADO_PROC:" + aprobada.getId(),
                aprobada.getId()
        );
        billeteraAlmacen.insertarMovimiento(movProc);

        MovimientoBilletera movRet = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.RETIRO_PAGADO,
                Billetera.EstadoSaldo.RETIRADO,
                monto,
                null,
                null,
                descPago,
                "RETIRO_PAGADO_RET:" + aprobada.getId(),
                aprobada.getId()
        );
        billeteraAlmacen.insertarMovimiento(movRet);

        log.info("Retiro {} aprobado por admin {} con comprobante {} ({} centavos)",
                aprobada.getId(), adminId, numeroComprobante, monto);

        notificarRetiroAprobado(aprobada);
        return aprobada;
    }

    /**
     * Rechaza una solicitud de retiro pendiente y revierte el saldo reservado a DISPONIBLE.
     */
    public SolicitudRetiro rechazarRetiro(String solicitudId, String adminId, String adminNombre, String motivoRechazo) {
        if (solicitudId == null || solicitudId.isBlank()) {
            throw new IllegalArgumentException("El identificador de la solicitud es obligatorio");
        }
        if (motivoRechazo == null || motivoRechazo.trim().isEmpty()) {
            throw new IllegalArgumentException("Debes indicar el motivo del rechazo");
        }

        String motivoLimpio = motivoRechazo.trim();
        LocalDateTime ahora = LocalDateTime.now();

        SolicitudRetiro rechazada = transicionarEstadoPendiente(
                solicitudId.trim(),
                SolicitudRetiro.Estado.RECHAZADO,
                ahora,
                adminId,
                adminNombre,
                motivoLimpio,
                null,
                null
        );

        if (rechazada == null) {
            throw new IllegalStateException("La solicitud de retiro no existe o ya fue procesada por otro administrador");
        }

        Billetera billetera = billeteraAlmacen.obtenerOCrear(rechazada.getUsuarioId(), rechazada.getRol());
        long monto = rechazada.getMontoCentavos();

        boolean revertido = billeteraAlmacen.revertirRetiro(billetera.getId(), monto);
        if (!revertido) {
            log.error("Fallo crítico al revertir retiro rechazado {}: saldo en proceso insuficiente ({} centavos) en billetera {}",
                    solicitudId, monto, billetera.getId());
            throw new IllegalStateException("No se pudo revertir el saldo reservado en la billetera");
        }

        String descReverso = "Reverso por retiro rechazado: " + motivoLimpio;

        MovimientoBilletera movProc = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.RETIRO_REVERTIDO,
                Billetera.EstadoSaldo.EN_PROCESO_RETIRO,
                -monto,
                null,
                null,
                descReverso,
                "RETIRO_REVERTIDO_PROC:" + rechazada.getId(),
                rechazada.getId()
        );
        billeteraAlmacen.insertarMovimiento(movProc);

        MovimientoBilletera movDisp = MovimientoBilletera.nuevo(
                billetera,
                MovimientoBilletera.Tipo.RETIRO_REVERTIDO,
                Billetera.EstadoSaldo.DISPONIBLE,
                monto,
                null,
                null,
                descReverso,
                "RETIRO_REVERTIDO_DISP:" + rechazada.getId(),
                rechazada.getId()
        );
        billeteraAlmacen.insertarMovimiento(movDisp);

        log.info("Retiro {} rechazado por admin {} ({} centavos devueltos a DISPONIBLE)",
                rechazada.getId(), adminId, monto);

        notificarRetiroRechazado(rechazada);
        return rechazada;
    }

    /**
     * Obtiene una solicitud aprobada verificando que pertenezca al usuario y rol solicitante.
     */
    public SolicitudRetiro obtenerComprobanteParaUsuario(String solicitudId, String usuarioId, Billetera.Rol rol) {
        if (solicitudId == null || usuarioId == null) {
            throw new IllegalArgumentException("Solicitud y usuario son obligatorios");
        }
        SolicitudRetiro s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("La solicitud de retiro no existe"));

        if (!usuarioId.equals(s.getUsuarioId()) || (rol != null && rol != s.getRol())) {
            throw new SecurityException("No tienes permiso para consultar este comprobante de retiro");
        }
        if (s.getEstado() != SolicitudRetiro.Estado.APROBADO) {
            throw new IllegalStateException("El comprobante solo está disponible para retiros aprobados");
        }
        return s;
    }

    /**
     * Obtiene cualquier comprobante aprobado para revisión del administrador.
     */
    public SolicitudRetiro obtenerComprobanteParaAdmin(String solicitudId) {
        if (solicitudId == null) {
            throw new IllegalArgumentException("El identificador de la solicitud es obligatorio");
        }
        SolicitudRetiro s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("La solicitud de retiro no existe"));
        if (s.getEstado() != SolicitudRetiro.Estado.APROBADO) {
            throw new IllegalStateException("El comprobante solo está disponible para retiros aprobados");
        }
        return s;
    }

    // ─────────────────────── Helpers internos ───────────────────────

    private CuentaBancaria obtenerCuentaDePerfil(Usuario usuario, Billetera.Rol rol) {
        if (rol == Billetera.Rol.VENDEDOR) {
            PerfilVendedor pv = usuario.getPerfilVendedor();
            if (pv == null) {
                throw new IllegalArgumentException("El usuario no tiene perfil de vendedor");
            }
            return pv.getCuentaRetiro();
        } else if (rol == Billetera.Rol.TRANSPORTISTA) {
            PerfilTransportista pt = usuario.getPerfilTransportista();
            if (pt == null) {
                throw new IllegalArgumentException("El usuario no tiene perfil de transportista");
            }
            return pt.getCuentaRetiro();
        }
        throw new IllegalArgumentException("Rol de billetera no soportado: " + rol);
    }

    private SolicitudRetiro transicionarEstadoPendiente(String solicitudId,
                                                        SolicitudRetiro.Estado nuevoEstado,
                                                        LocalDateTime fechaResolucion,
                                                        String adminId,
                                                        String adminNombre,
                                                        String motivoRechazo,
                                                        String referenciaBancaria,
                                                        String numeroComprobante) {
        if (mongoTemplate != null) {
            Query q = new Query(Criteria.where("_id").is(solicitudId)
                    .and("estado").is(SolicitudRetiro.Estado.PENDIENTE.name()));
            Update u = new Update()
                    .set("estado", nuevoEstado.name())
                    .set("fecha_resolucion", fechaResolucion)
                    .set("admin_id", adminId)
                    .set("admin_nombre", adminNombre);
            if (motivoRechazo != null) {
                u.set("motivo_rechazo", motivoRechazo);
            }
            if (referenciaBancaria != null) {
                u.set("referencia_bancaria", referenciaBancaria);
            }
            if (numeroComprobante != null) {
                u.set("numero_comprobante", numeroComprobante);
            }
            return mongoTemplate.findAndModify(q, u,
                    FindAndModifyOptions.options().returnNew(true),
                    SolicitudRetiro.class);
        }

        // Respaldo atómico en memoria para pruebas unitarias sin MongoTemplate
        synchronized (transicionLock) {
            Optional<SolicitudRetiro> opt = solicitudRepository.findById(solicitudId);
            if (opt.isEmpty() || opt.get().getEstado() != SolicitudRetiro.Estado.PENDIENTE) {
                return null;
            }
            SolicitudRetiro s = opt.get();
            s.setEstado(nuevoEstado);
            s.setFechaResolucion(fechaResolucion);
            s.setAdminId(adminId);
            s.setAdminNombre(adminNombre);
            s.setMotivoRechazo(motivoRechazo);
            s.setReferenciaBancaria(referenciaBancaria);
            s.setNumeroComprobante(numeroComprobante);
            return solicitudRepository.save(s);
        }
    }

    private void notificarSolicitudCreada(SolicitudRetiro s) {
        if (notificacionService == null) return;
        try {
            String cuentaEnmascarada = s.getCuentaDestino() != null
                    ? s.getCuentaDestino().getResumenEnmascarado()
                    : "tu cuenta registrada";
            String montoStr = String.format(Locale.GERMANY, "$%,d COP", s.getMontoPesos());

            notificacionService.enviar(
                    s.getUsuarioId(),
                    "Solicitud de retiro en revisión",
                    "Recibimos tu solicitud de retiro por " + montoStr + " hacia " + cuentaEnmascarada + ".",
                    "INFO"
            );

            notificacionService.enviarAAdmins(
                    "Nueva solicitud de retiro (" + s.getRol() + ")",
                    (s.getUsuarioNombre() != null ? s.getUsuarioNombre() : s.getUsuarioId())
                            + " solicitó un retiro por " + montoStr + " hacia " + cuentaEnmascarada + ".",
                    "WARNING"
            );
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de solicitud de retiro {}: {}", s.getId(), e.getMessage());
        }
    }

    private void notificarRetiroAprobado(SolicitudRetiro s) {
        if (notificacionService == null) return;
        try {
            String cuentaEnmascarada = s.getCuentaDestino() != null
                    ? s.getCuentaDestino().getResumenEnmascarado()
                    : "tu cuenta registrada";
            String montoStr = String.format(Locale.GERMANY, "$%,d COP", s.getMontoPesos());

            notificacionService.enviar(
                    s.getUsuarioId(),
                    "Retiro aprobado — " + s.getNumeroComprobante(),
                    "Tu retiro por " + montoStr + " hacia " + cuentaEnmascarada
                            + " fue aprobado. Comprobante: " + s.getNumeroComprobante() + ".",
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de aprobación de retiro {}: {}", s.getId(), e.getMessage());
        }
    }

    private void notificarRetiroRechazado(SolicitudRetiro s) {
        if (notificacionService == null) return;
        try {
            String cuentaEnmascarada = s.getCuentaDestino() != null
                    ? s.getCuentaDestino().getResumenEnmascarado()
                    : "tu cuenta registrada";
            String montoStr = String.format(Locale.GERMANY, "$%,d COP", s.getMontoPesos());

            notificacionService.enviar(
                    s.getUsuarioId(),
                    "Solicitud de retiro rechazada",
                    "Tu solicitud de retiro por " + montoStr + " hacia " + cuentaEnmascarada
                            + " fue rechazada. Motivo: " + s.getMotivoRechazo()
                            + ". El saldo ha regresado a tu saldo disponible.",
                    "ERROR"
            );
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de rechazo de retiro {}: {}", s.getId(), e.getMessage());
        }
    }
}

