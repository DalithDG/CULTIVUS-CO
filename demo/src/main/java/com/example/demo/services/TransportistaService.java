package com.example.demo.services;

import com.example.demo.Model.Compra;
import com.example.demo.Model.Envio;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.ParadaEnvio;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.Model.embebidos.SnapshotTransportista;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.EnvioRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.security.SecurityContextRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransportistaService {

    private static final Logger log = LoggerFactory.getLogger(TransportistaService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private EnvioRepository envioRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextRefreshService securityContextRefreshService;

    @Autowired(required = false)
    private BilleteraService billeteraService;

    @Autowired(required = false)
    private AppConfigService appConfigService;

    @Autowired(required = false)
    private PedidoEstadoService pedidoEstadoService;

    // ──────────────────────────── DTOs para la vista ────────────────────────────

    public static class RutaDisponibleDTO {
        private Envio envio;
        private Compra compra;
        private boolean enZona;
        private List<ParadaDetalleDTO> paradasDetalle = new ArrayList<>();

        public RutaDisponibleDTO() {}

        public RutaDisponibleDTO(Envio envio, Compra compra, boolean enZona, List<ParadaDetalleDTO> paradasDetalle) {
            this.envio = envio;
            this.compra = compra;
            this.enZona = enZona;
            this.paradasDetalle = paradasDetalle != null ? paradasDetalle : new ArrayList<>();
        }

        public Envio getEnvio() { return envio; }
        public void setEnvio(Envio envio) { this.envio = envio; }

        public Compra getCompra() { return compra; }
        public void setCompra(Compra compra) { this.compra = compra; }

        public boolean isEnZona() { return enZona; }
        public void setEnZona(boolean enZona) { this.enZona = enZona; }

        public List<ParadaDetalleDTO> getParadasDetalle() { return paradasDetalle; }
        public void setParadasDetalle(List<ParadaDetalleDTO> paradasDetalle) { this.paradasDetalle = paradasDetalle; }
    }

    public static class ParadaDetalleDTO {
        private ParadaEnvio parada;
        private String estadoPedido;
        private String estadoPedidoEtiqueta;
        private String estadoPedidoCss;

        public ParadaDetalleDTO() {}

        public ParadaDetalleDTO(ParadaEnvio parada, String estadoPedido, String estadoPedidoEtiqueta, String estadoPedidoCss) {
            this.parada = parada;
            this.estadoPedido = estadoPedido;
            this.estadoPedidoEtiqueta = estadoPedidoEtiqueta;
            this.estadoPedidoCss = estadoPedidoCss;
        }

        public ParadaEnvio getParada() { return parada; }
        public void setParada(ParadaEnvio parada) { this.parada = parada; }

        public String getEstadoPedido() { return estadoPedido; }
        public void setEstadoPedido(String estadoPedido) { this.estadoPedido = estadoPedido; }

        public String getEstadoPedidoEtiqueta() { return estadoPedidoEtiqueta; }
        public void setEstadoPedidoEtiqueta(String estadoPedidoEtiqueta) { this.estadoPedidoEtiqueta = estadoPedidoEtiqueta; }

        public String getEstadoPedidoCss() { return estadoPedidoCss; }
        public void setEstadoPedidoCss(String estadoPedidoCss) { this.estadoPedidoCss = estadoPedidoCss; }
    }

    public static class MiEnvioDetalleDTO {
        private Envio envio;
        private Compra compra;
        private boolean puedeLiberar;
        private List<ParadaDetalleDTO> paradasDetalle = new ArrayList<>();

        public MiEnvioDetalleDTO() {}

        public MiEnvioDetalleDTO(Envio envio, Compra compra, boolean puedeLiberar, List<ParadaDetalleDTO> paradasDetalle) {
            this.envio = envio;
            this.compra = compra;
            this.puedeLiberar = puedeLiberar;
            this.paradasDetalle = paradasDetalle != null ? paradasDetalle : new ArrayList<>();
        }

        public Envio getEnvio() { return envio; }
        public void setEnvio(Envio envio) { this.envio = envio; }

        public Compra getCompra() { return compra; }
        public void setCompra(Compra compra) { this.compra = compra; }

        public boolean isPuedeLiberar() { return puedeLiberar; }
        public void setPuedeLiberar(boolean puedeLiberar) { this.puedeLiberar = puedeLiberar; }

        public List<ParadaDetalleDTO> getParadasDetalle() { return paradasDetalle; }
        public void setParadasDetalle(List<ParadaDetalleDTO> paradasDetalle) { this.paradasDetalle = paradasDetalle; }

        public boolean isTodasRecogidas() {
            if (paradasDetalle == null || paradasDetalle.isEmpty()) return false;
            return paradasDetalle.stream()
                    .allMatch(pd -> pd.getParada() != null && pd.getParada().isRecogido());
        }
    }

    // ──────────────────────────── Métodos de Roles y Perfiles ────────────────────────────

    public boolean esTransportista(String usuarioId) {
        if (usuarioId == null) return false;
        return usuarioRepository.findById(usuarioId)
                .map(u -> u.hasRole(Role.TRANSPORTISTA))
                .orElse(false);
    }

    public Optional<PerfilTransportista> obtenerPerfilPorUsuarioId(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getPerfilTransportista);
    }

    /**
     * Valida y normaliza zonas de cobertura.
     * Si no se seleccionó ninguna, lanza IllegalArgumentException.
     * Si todasLasZonas es true, asegura que se guarden las 6 zonas fijas de Bolívar.
     */
    private void validarYConfigurarZonas(PerfilTransportista perfil, List<Integer> zonasCoberturaIds, boolean todasLasZonas) {
        if (!todasLasZonas && (zonasCoberturaIds == null || zonasCoberturaIds.isEmpty())) {
            throw new IllegalArgumentException("Debes seleccionar al menos una zona de cobertura");
        }

        List<Integer> idsFinales;
        if (todasLasZonas || (zonasCoberturaIds != null && zonasCoberturaIds.size() >= 6)) {
            perfil.setTodasLasZonas(true);
            idsFinales = ZonasLogistica.idsTodas();
            perfil.setZonasCoberturaIds(idsFinales);
            perfil.setZonasCobertura("Todas las zonas (Dique, Montes de María, Mojana, Depresión Momposina, Loba, Magdalena Medio)");
        } else {
            perfil.setTodasLasZonas(false);
            idsFinales = zonasCoberturaIds.stream()
                    .filter(ZonasLogistica::esIdValido)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            if (idsFinales.isEmpty()) {
                throw new IllegalArgumentException("Debes seleccionar al menos una zona de cobertura válida");
            }

            perfil.setZonasCoberturaIds(idsFinales);
            String legible = idsFinales.stream()
                    .map(ZonasLogistica::nombreDe)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            perfil.setZonasCobertura(legible);
        }
    }

    private void validarCamposPorTipo(String tipo, String placa, String licenciaConduccion, String nit, String razonSocial) {
        boolean esEmpresa = PerfilTransportista.TIPO_EMPRESA.equalsIgnoreCase(tipo);
        if (esEmpresa) {
            if (nit == null || nit.trim().isEmpty()) {
                throw new IllegalArgumentException("El NIT es obligatorio para empresas transportistas");
            }
            if (razonSocial == null || razonSocial.trim().isEmpty()) {
                throw new IllegalArgumentException("La Razón Social es obligatoria para empresas transportistas");
            }
        } else {
            if (placa == null || placa.trim().length() < 5) {
                throw new IllegalArgumentException("La placa del vehículo es obligatoria para transportistas independientes (ej. ABC-123)");
            }
            if (licenciaConduccion == null || licenciaConduccion.trim().isEmpty()) {
                throw new IllegalArgumentException("La licencia de conducción es obligatoria para transportistas independientes");
            }
        }
    }

    /**
     * Permite a un usuario logueado registrarse como transportista con zonas y tipo estructurados.
     */
    public Usuario crearPerfilTransportista(
            String usuarioId,
            String tipo,
            String nit,
            String razonSocial,
            String tipoVehiculo,
            String placa,
            Double capacidadCargaKg,
            String licenciaConduccion,
            String telefonoContacto,
            String ciudadBase,
            String departamentoBase,
            List<Integer> zonasCoberturaIds,
            boolean todasLasZonas,
            boolean refrigerado,
            String empresa,
            String banco,
            String cuentaBancaria) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (usuario.getPerfilTransportista() != null) {
            throw new IllegalArgumentException("Este usuario ya tiene un perfil de transportista");
        }

        String tipoFinal = PerfilTransportista.TIPO_EMPRESA.equalsIgnoreCase(tipo)
                ? PerfilTransportista.TIPO_EMPRESA
                : PerfilTransportista.TIPO_INDEPENDIENTE;

        validarCamposPorTipo(tipoFinal, placa, licenciaConduccion, nit, razonSocial);

        PerfilTransportista perfil = new PerfilTransportista();
        perfil.setTipo(tipoFinal);
        perfil.setNit(nit != null ? nit.trim() : null);
        perfil.setRazonSocial(razonSocial != null ? razonSocial.trim() : null);
        perfil.setTipoVehiculo(tipoVehiculo != null ? tipoVehiculo.trim() : "Camioneta");
        perfil.setPlaca(placa != null ? placa.trim().toUpperCase() : null);
        perfil.setCapacidadCargaKg(capacidadCargaKg != null ? capacidadCargaKg : 1000.0);
        perfil.setLicenciaConduccion(licenciaConduccion != null ? licenciaConduccion.trim() : null);
        perfil.setTelefonoContacto(telefonoContacto != null ? telefonoContacto.trim() : null);
        perfil.setCiudadBase(ciudadBase != null ? ciudadBase.trim() : "Cartagena");
        perfil.setDepartamentoBase(departamentoBase != null ? departamentoBase.trim() : "Bolívar");
        perfil.setRefrigerado(refrigerado);
        perfil.setEmpresa(empresa != null ? empresa.trim() : (esEmpresa(tipoFinal) ? razonSocial : "Independiente"));
        perfil.setBanco(banco);
        perfil.setCuentaBancaria(cuentaBancaria);
        perfil.setDisponible(true);
        perfil.setVerificado(false); // Requiere verificación del admin para aceptar compras

        validarYConfigurarZonas(perfil, zonasCoberturaIds, todasLasZonas);

        usuario.setPerfilTransportista(perfil);
        usuario.addRole(Role.TRANSPORTISTA);

        Usuario guardado = usuarioRepository.save(usuario);
        securityContextRefreshService.refrescarAutenticacion(guardado.getEmail());
        return guardado;
    }

    private boolean esEmpresa(String tipo) {
        return PerfilTransportista.TIPO_EMPRESA.equalsIgnoreCase(tipo);
    }

    /**
     * Sobrecarga sin banco/cuentaBancaria en el registro (US-24).
     */
    public Usuario crearPerfilTransportista(
            String usuarioId,
            String tipo,
            String nit,
            String razonSocial,
            String tipoVehiculo,
            String placa,
            Double capacidadCargaKg,
            String licenciaConduccion,
            String telefonoContacto,
            String ciudadBase,
            String departamentoBase,
            List<Integer> zonasCoberturaIds,
            boolean todasLasZonas,
            boolean refrigerado,
            String empresa) {
        return crearPerfilTransportista(usuarioId, tipo, nit, razonSocial, tipoVehiculo, placa,
                capacidadCargaKg, licenciaConduccion, telefonoContacto, ciudadBase, departamentoBase,
                zonasCoberturaIds, todasLasZonas, refrigerado, empresa, null, null);
    }

    /**
     * US-24 / Fase 2: Valida y guarda la cuenta bancaria de retiro del transportista,
     * sincronizando los campos legados banco y cuentaBancaria.
     */
    public Usuario guardarCuentaBancaria(String usuarioId, com.example.demo.Model.embebidos.CuentaBancaria cuenta) {
        if (cuenta == null) {
            throw new IllegalArgumentException("Los datos de la cuenta bancaria son obligatorios");
        }
        cuenta.validar();

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        PerfilTransportista perfil = usuario.getPerfilTransportista();
        if (perfil == null) {
            throw new IllegalArgumentException("Este usuario no tiene perfil de transportista");
        }

        perfil.setCuentaRetiro(cuenta);
        usuario.setPerfilTransportista(perfil);
        return usuarioRepository.save(usuario);
    }

    /**
     * Sobrecarga de compatibilidad anterior.
     */
    public Usuario crearPerfilTransportista(
            String usuarioId,
            String tipoVehiculo,
            String placa,
            Double capacidadCargaKg,
            String licenciaConduccion,
            String telefonoContacto,
            String ciudadBase,
            String departamentoBase,
            String zonasCobertura,
            boolean refrigerado,
            String empresa,
            String banco,
            String cuentaBancaria) {

        return crearPerfilTransportista(usuarioId, PerfilTransportista.TIPO_INDEPENDIENTE, null, null,
                tipoVehiculo, placa, capacidadCargaKg, licenciaConduccion, telefonoContacto, ciudadBase,
                departamentoBase, List.of(1, 2), false, refrigerado, empresa, banco, cuentaBancaria);
    }

    /**
     * Registra un nuevo usuario directamente como TRANSPORTISTA con zonas estructuradas.
     */
    public Usuario registrarNuevoTransportista(
            String nombre,
            String email,
            String contrasena,
            String tipo,
            String nit,
            String razonSocial,
            String tipoVehiculo,
            String placa,
            Double capacidadCargaKg,
            String licenciaConduccion,
            String telefonoContacto,
            String ciudadBase,
            String departamentoBase,
            List<Integer> zonasCoberturaIds,
            boolean todasLasZonas,
            boolean refrigerado,
            String empresa,
            String banco,
            String cuentaBancaria) {

        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El correo ya se encuentra registrado");
        }

        String tipoFinal = PerfilTransportista.TIPO_EMPRESA.equalsIgnoreCase(tipo)
                ? PerfilTransportista.TIPO_EMPRESA
                : PerfilTransportista.TIPO_INDEPENDIENTE;

        validarCamposPorTipo(tipoFinal, placa, licenciaConduccion, nit, razonSocial);

        Usuario nuevo = new Usuario();
        nuevo.setNombre(nombre != null ? nombre.trim() : (razonSocial != null ? razonSocial.trim() : "Transportista"));
        nuevo.setEmail(email.trim().toLowerCase());
        nuevo.setContrasena(passwordEncoder.encode(contrasena));
        nuevo.addRole(Role.TRANSPORTISTA);
        nuevo.addRole(Role.COMPRADOR);

        PerfilTransportista perfil = new PerfilTransportista();
        perfil.setTipo(tipoFinal);
        perfil.setNit(nit != null ? nit.trim() : null);
        perfil.setRazonSocial(razonSocial != null ? razonSocial.trim() : null);
        perfil.setTipoVehiculo(tipoVehiculo != null ? tipoVehiculo.trim() : "Camioneta");
        perfil.setPlaca(placa != null ? placa.trim().toUpperCase() : null);
        perfil.setCapacidadCargaKg(capacidadCargaKg != null ? capacidadCargaKg : 1000.0);
        perfil.setLicenciaConduccion(licenciaConduccion != null ? licenciaConduccion.trim() : null);
        perfil.setTelefonoContacto(telefonoContacto != null ? telefonoContacto.trim() : null);
        perfil.setCiudadBase(ciudadBase != null ? ciudadBase.trim() : "Cartagena");
        perfil.setDepartamentoBase(departamentoBase != null ? departamentoBase.trim() : "Bolívar");
        perfil.setRefrigerado(refrigerado);
        perfil.setEmpresa(empresa != null ? empresa.trim() : (esEmpresa(tipoFinal) ? razonSocial : "Independiente"));
        perfil.setBanco(banco);
        perfil.setCuentaBancaria(cuentaBancaria);
        perfil.setDisponible(true);
        perfil.setVerificado(false);

        validarYConfigurarZonas(perfil, zonasCoberturaIds, todasLasZonas);

        nuevo.setPerfilTransportista(perfil);
        return usuarioRepository.save(nuevo);
    }

    /**
     * Sobrecarga de compatibilidad anterior.
     */
    public Usuario registrarNuevoTransportista(
            String nombre,
            String email,
            String contrasena,
            String tipoVehiculo,
            String placa,
            Double capacidadCargaKg,
            String licenciaConduccion,
            String telefonoContacto,
            String ciudadBase,
            String departamentoBase,
            String zonasCobertura,
            boolean refrigerado,
            String empresa,
            String banco,
            String cuentaBancaria) {

        return registrarNuevoTransportista(nombre, email, contrasena, PerfilTransportista.TIPO_INDEPENDIENTE, null, null,
                tipoVehiculo, placa, capacidadCargaKg, licenciaConduccion, telefonoContacto, ciudadBase,
                departamentoBase, List.of(1, 2), false, refrigerado, empresa, banco, cuentaBancaria);
    }

    public Usuario actualizarDisponibilidad(String usuarioId, boolean disponible) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (usuario.getPerfilTransportista() != null) {
            usuario.getPerfilTransportista().setDisponible(disponible);
            return usuarioRepository.save(usuario);
        }
        return usuario;
    }

    /**
     * Panel del Admin: Verificación de transportista para habilitarlo a aceptar envíos.
     */
    public Usuario verificarTransportista(String usuarioId, boolean verificado) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (usuario.getPerfilTransportista() != null) {
            usuario.getPerfilTransportista().setVerificado(verificado);
            Usuario guardado = usuarioRepository.save(usuario);

            if (notificacionService != null) {
                String mensaje = verificado
                        ? "¡Felicitaciones! Tu perfil de transportista ha sido verificado. Ya puedes aceptar compras disponibles."
                        : "Tu perfil de transportista se encuentra en revisión.";
                notificacionService.enviar(usuarioId, "Verificación de Transportista", mensaje, verificado ? "SUCCESS" : "INFO");
            }

            return guardado;
        }
        return usuario;
    }

    // ──────────────────────────── FASE 1: Rutas Disponibles y Gestión de Envíos ────────────────────────────

    /**
     * Obtiene los envíos PENDIENTE_ASIGNACION presentados como tarjetas por compra con ruta completa.
     * Ordenados primero los que tienen al menos una recogida en la zona del transportista, y luego por fecha.
     */
    public List<RutaDisponibleDTO> obtenerRutasDisponibles(String transportistaUsuarioId) {
        PerfilTransportista perfil = usuarioRepository.findById(transportistaUsuarioId)
                .map(Usuario::getPerfilTransportista)
                .orElse(null);

        List<Envio> enviosPendientes = envioRepository.findByEstado(Envio.PENDIENTE_ASIGNACION);
        if (enviosPendientes.isEmpty()) {
            return Collections.emptyList();
        }

        // Cargar compras y pedidos para detalles
        Set<String> compraIds = enviosPendientes.stream().map(Envio::getCompraId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Compra> comprasMap = compraRepository.findAllById(compraIds).stream()
                .collect(Collectors.toMap(Compra::getId, c -> c));

        Set<String> todosPedidoIds = new HashSet<>();
        for (Envio e : enviosPendientes) {
            if (e.getParadas() != null) {
                for (ParadaEnvio p : e.getParadas()) {
                    if (p.getPedidoId() != null) todosPedidoIds.add(p.getPedidoId());
                }
            }
        }
        Map<String, Pedido> pedidosMap = pedidoRepository.findAllById(todosPedidoIds).stream()
                .collect(Collectors.toMap(Pedido::getId, p -> p));

        List<RutaDisponibleDTO> rutas = new ArrayList<>();
        for (Envio envio : enviosPendientes) {
            Compra compra = envio.getCompraId() != null ? comprasMap.get(envio.getCompraId()) : null;

            boolean enZona = false;
            if (perfil != null) {
                if (perfil.isTodasLasZonas()) {
                    enZona = true;
                } else if (envio.getParadas() != null) {
                    enZona = envio.getParadas().stream()
                            .anyMatch(parada -> parada.getZona() != null && perfil.operaEnZona(parada.getZona()));
                }
            }

            List<ParadaDetalleDTO> paradasDetalle = new ArrayList<>();
            if (envio.getParadas() != null) {
                for (ParadaEnvio p : envio.getParadas()) {
                    Pedido ped = p.getPedidoId() != null ? pedidosMap.get(p.getPedidoId()) : null;
                    String estado = ped != null ? ped.getEstado() : "CONFIRMADO";
                    String etiqueta = ped != null ? ped.getEstadoEtiqueta() : "Confirmado";
                    String css = ped != null ? ped.getEstadoCss() : "confirmado";
                    paradasDetalle.add(new ParadaDetalleDTO(p, estado, etiqueta, css));
                }
            }

            rutas.add(new RutaDisponibleDTO(envio, compra, enZona, paradasDetalle));
        }

        // Ordenar: primero las que están en zona del transportista, luego más recientes
        rutas.sort((r1, r2) -> {
            if (r1.isEnZona() != r2.isEnZona()) {
                return r1.isEnZona() ? -1 : 1;
            }
            LocalDateTime f1 = r1.getEnvio().getFechaCreacion() != null ? r1.getEnvio().getFechaCreacion() : LocalDateTime.MIN;
            LocalDateTime f2 = r2.getEnvio().getFechaCreacion() != null ? r2.getEnvio().getFechaCreacion() : LocalDateTime.MIN;
            return f2.compareTo(f1);
        });

        return rutas;
    }

    /**
     * Obtiene los envíos asignados al transportista logueado.
     */
    public List<MiEnvioDetalleDTO> obtenerMisEnviosDetallados(String transportistaUsuarioId) {
        List<Envio> misEnvios = envioRepository.findByTransportistaIdOrderByFechaAsignacionDesc(transportistaUsuarioId);
        if (misEnvios.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> compraIds = misEnvios.stream().map(Envio::getCompraId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Compra> comprasMap = compraRepository.findAllById(compraIds).stream()
                .collect(Collectors.toMap(Compra::getId, c -> c));

        Set<String> todosPedidoIds = new HashSet<>();
        for (Envio e : misEnvios) {
            if (e.getParadas() != null) {
                for (ParadaEnvio p : e.getParadas()) {
                    if (p.getPedidoId() != null) todosPedidoIds.add(p.getPedidoId());
                }
            }
        }
        Map<String, Pedido> pedidosMap = pedidoRepository.findAllById(todosPedidoIds).stream()
                .collect(Collectors.toMap(Pedido::getId, p -> p));

        List<MiEnvioDetalleDTO> resultado = new ArrayList<>();
        for (Envio envio : misEnvios) {
            Compra compra = envio.getCompraId() != null ? comprasMap.get(envio.getCompraId()) : null;
            boolean puedeLiberar = (envio.getParadas() == null || envio.getParadas().stream().noneMatch(ParadaEnvio::isRecogido))
                    && Envio.ASIGNADO.equals(envio.getEstado());

            List<ParadaDetalleDTO> paradasDetalle = new ArrayList<>();
            if (envio.getParadas() != null) {
                for (ParadaEnvio p : envio.getParadas()) {
                    Pedido ped = p.getPedidoId() != null ? pedidosMap.get(p.getPedidoId()) : null;
                    String estado = ped != null ? ped.getEstado() : "CONFIRMADO";
                    String etiqueta = ped != null ? ped.getEstadoEtiqueta() : "Confirmado";
                    String css = ped != null ? ped.getEstadoCss() : "confirmado";
                    paradasDetalle.add(new ParadaDetalleDTO(p, estado, etiqueta, css));
                }
            }

            resultado.add(new MiEnvioDetalleDTO(envio, compra, puedeLiberar, paradasDetalle));
        }

        return resultado;
    }

    /**
     * Aceptar Envío concurrentemente:
     * Valida rol TRANSPORTISTA, verificado y disponible.
     * Ejecuta MongoTemplate.findAndModify sobre { _id, estado: PENDIENTE_ASIGNACION }.
     * Si devuelve null, significa que ya fue tomada por otro transportista.
     * Al aceptar: notifica al comprador y a los vendedores de cada parada.
     */
    public Envio aceptarEnvio(String envioId, String transportistaUsuarioId) {
        if (envioId == null || envioId.isBlank()) {
            throw new IllegalArgumentException("ID de envío inválido");
        }

        Usuario transportista = usuarioRepository.findById(transportistaUsuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!transportista.hasRole(Role.TRANSPORTISTA)) {
            throw new IllegalStateException("Debes tener el rol de transportista para aceptar compras");
        }

        PerfilTransportista perfil = transportista.getPerfilTransportista();
        if (perfil == null) {
            throw new IllegalStateException("No tienes un perfil de transportista configurado");
        }

        if (!perfil.isVerificado()) {
            throw new IllegalStateException("Tu cuenta aún no ha sido verificada por el administrador. No puedes tomar compras aún.");
        }

        if (!perfil.isDisponible()) {
            throw new IllegalStateException("Tu estado actual es 'No Disponible'. Activa tu disponibilidad para aceptar compras.");
        }

        SnapshotTransportista snapshot = new SnapshotTransportista(
                transportista.getId(),
                transportista.getNombre(),
                perfil.getTelefonoContacto(),
                perfil.getTipo(),
                perfil.getPlaca(),
                perfil.getRazonSocial(),
                perfil.getTipoVehiculo()
        );

        Query query = new Query(Criteria.where("_id").is(envioId).and("estado").is(Envio.PENDIENTE_ASIGNACION));
        Update update = new Update()
                .set("estado", Envio.ASIGNADO)
                .set("transportistaId", transportista.getId())
                .set("transportista", snapshot)
                .set("fechaAsignacion", LocalDateTime.now())
                .set("updatedAt", LocalDateTime.now());

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Envio asignado = mongoTemplate.findAndModify(query, update, options, Envio.class);

        if (asignado == null) {
            throw new IllegalStateException("La compra ya fue tomada por otro transportista");
        }

        // Notificaciones posteriores a la aceptación exitosa
        try {
            // Notificar al comprador
            if (asignado.getCompraId() != null) {
                compraRepository.findById(asignado.getCompraId()).ifPresent(compra -> {
                    if (compra.getComprador() != null && compra.getComprador().getId() != null && notificacionService != null) {
                        String nombreTrans = transportista.getNombre();
                        String tipoTrans = perfil.getTipo() != null ? perfil.getTipo() : "Independiente";
                        String telTrans = perfil.getTelefonoContacto() != null ? perfil.getTelefonoContacto() : "Sin teléfono";
                        notificacionService.enviar(
                                compra.getComprador().getId(),
                                "Transportista Asignado a tu Compra",
                                "Tu envío ha sido tomado por " + nombreTrans + " (" + tipoTrans + "). Teléfono: " + telTrans,
                                "SUCCESS"
                        );
                    }
                });
            }

            // Notificar a cada vendedor de las paradas
            if (asignado.getParadas() != null && notificacionService != null) {
                Set<String> vendedoresNotificados = new HashSet<>();
                for (ParadaEnvio p : asignado.getParadas()) {
                    if (p.getVendedorId() != null && vendedoresNotificados.add(p.getVendedorId())) {
                        String numFactura = p.getPedidoId();
                        if (pedidoRepository != null && p.getPedidoId() != null) {
                            numFactura = pedidoRepository.findById(p.getPedidoId())
                                    .map(Pedido::getNumeroFactura)
                                    .orElse(p.getPedidoId());
                        }
                        notificacionService.enviar(
                                p.getVendedorId(),
                                "Transportista en Camino",
                                "El transportista " + transportista.getNombre() + " (" + perfil.getTelefonoContacto() + ") ha tomado la ruta para recoger tu pedido #" + numFactura,
                                "INFO"
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error al emitir notificaciones tras aceptar envío {}: {}", envioId, e.getMessage());
        }

        return asignado;
    }

    /**
     * 4.1: Comprueba si todas las paradas del envío están en LISTO_PARA_RECOGER (o posteriores).
     * Si es así y fechaDisponibleDesde aún es null, se establece en now().
     */
    public void actualizarDisponibilidadEnvioSiListo(String compraId) {
        if (compraId == null || envioRepository == null || pedidoRepository == null) return;
        envioRepository.findByCompraId(compraId).ifPresent(envio -> {
            if (envio.getFechaDisponibleDesde() != null) {
                return;
            }
            if (envio.getParadas() == null || envio.getParadas().isEmpty()) {
                return;
            }
            List<String> pedidoIds = envio.getParadas().stream()
                    .map(ParadaEnvio::getPedidoId)
                    .filter(Objects::nonNull)
                    .toList();
            if (pedidoIds.isEmpty()) return;

            List<Pedido> pedidos = pedidoRepository.findAllById(pedidoIds);
            boolean todosListos = pedidos.stream().allMatch(p -> {
                com.example.demo.Model.EstadoPedido ep = p.getEstadoEnum();
                return ep == com.example.demo.Model.EstadoPedido.LISTO_PARA_RECOGER
                        || ep == com.example.demo.Model.EstadoPedido.ENVIADO
                        || ep == com.example.demo.Model.EstadoPedido.ENTREGADO
                        || ep == com.example.demo.Model.EstadoPedido.CANCELADO;
            });

            if (todosListos) {
                envio.setFechaDisponibleDesde(LocalDateTime.now());
                envio.setUpdatedAt(LocalDateTime.now());
                envioRepository.save(envio);
                log.info("Envío {} habilitado con fechaDisponibleDesde={} (todas las paradas listas para recoger)",
                        envio.getId(), envio.getFechaDisponibleDesde());
            }
        });
    }

    /**
     * 4.3: Liberar Envío de manera atómica con findAndModify.
     * Solo si el estado es ASIGNADO, transportista coincide y ninguna parada ha sido recogida.
     * Pasa a PENDIENTE_ASIGNACION, desasigna transportista y conserva fechaDisponibleDesde.
     */
    public Envio liberarEnvio(String envioId, String transportistaUsuarioId) {
        if (envioId == null || envioId.isBlank()) {
            throw new IllegalArgumentException("ID de envío inválido");
        }

        Envio envio = envioRepository.findById(envioId)
                .orElseThrow(() -> new IllegalArgumentException("Envío no encontrado"));

        if (!transportistaUsuarioId.equals(envio.getTransportistaId())) {
            throw new SecurityException("Solo el transportista asignado puede liberar este envío.");
        }

        if (envio.getParadas() != null) {
            boolean algunaRecogida = envio.getParadas().stream().anyMatch(ParadaEnvio::isRecogido);
            if (algunaRecogida) {
                throw new IllegalStateException("No se puede liberar un envío que ya tiene paradas recogidas.");
            }
        }

        if (mongoTemplate != null) {
            Query query = new Query(Criteria.where("_id").is(envioId)
                    .and("estado").is(Envio.ASIGNADO)
                    .and("transportistaId").is(transportistaUsuarioId)
                    .and("paradas.recogido").ne(true));

            Update update = new Update()
                    .set("estado", Envio.PENDIENTE_ASIGNACION)
                    .unset("transportistaId")
                    .unset("transportista")
                    .unset("fechaAsignacion")
                    .set("updatedAt", LocalDateTime.now());

            Envio liberado = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Envio.class);
            if (liberado != null) {
                return liberado;
            }
        }

        envio.setEstado(Envio.PENDIENTE_ASIGNACION);
        envio.setTransportistaId(null);
        envio.setTransportista(null);
        envio.setFechaAsignacion(null);
        envio.setUpdatedAt(LocalDateTime.now());

        return envioRepository.save(envio);
    }

    // ──────────────────────────── FASE 2: Ciclo Operativo de Paradas y Entregas ────────────────────────────

    /**
     * 4.6: Marcar Parada Recogida (Carrier):
     * Idempotente si la parada ya fue marcada como recogida.
     * Valida que el transportista logueado sea el asignado al envío.
     * Valida que el sub-pedido se encuentre estrictamente en LISTO_PARA_RECOGER.
     * Marca parada.recogido = true, actualiza el pedido a ENVIADO vía PedidoEstadoService y notifica al comprador.
     * Si todas las paradas fueron recogidas, avanza el estado del envío a EN_ENTREGA, sino a EN_RECOGIDA.
     */
    public Envio marcarParadaRecogida(String envioId, String pedidoId, String transportistaUsuarioId) {
        if (envioId == null || pedidoId == null) {
            throw new IllegalArgumentException("ID de envío y pedido son obligatorios");
        }

        Envio envio = envioRepository.findById(envioId)
                .orElseThrow(() -> new IllegalArgumentException("Envío no encontrado"));

        if (envio.getTransportistaId() == null || !envio.getTransportistaId().equals(transportistaUsuarioId)) {
            throw new SecurityException("Solo el transportista asignado puede marcar paradas de este envío.");
        }

        if (Envio.ENTREGADO.equals(envio.getEstado()) || Envio.CANCELADO.equals(envio.getEstado())) {
            throw new IllegalStateException("El envío se encuentra en estado " + envio.getEstado() + " y no permite recolecciones.");
        }

        ParadaEnvio parada = envio.getParadas().stream()
                .filter(p -> pedidoId.equals(p.getPedidoId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Parada no encontrada en este envío para el pedido: " + pedidoId));

        if (parada.isRecogido()) {
            log.info("Parada para pedido {} ya fue recogida previamente (idempotente)", pedidoId);
            return envio;
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        if (!Pedido.LISTO_PARA_RECOGER.equalsIgnoreCase(pedido.getEstado())) {
            throw new IllegalStateException("El pedido no está listo para recoger por parte del vendedor. Estado actual: " + pedido.getEstado());
        }

        // Marcar parada como recogida
        parada.setRecogido(true);
        parada.setFechaRecogida(LocalDateTime.now());

        // Actualizar estado del sub-pedido a ENVIADO (escrito exclusivamente por logística)
        if (pedidoEstadoService != null) {
            pedidoEstadoService.transicionar(
                    pedido,
                    com.example.demo.Model.EstadoPedido.ENVIADO,
                    com.example.demo.Model.ActorTransicion.TRANSPORTISTA,
                    "Recogido en finca por transportista"
            );
        } else {
            pedido.setEstado(Pedido.ENVIADO);
            pedido.setUpdatedAt(LocalDateTime.now());
            pedidoRepository.save(pedido);
        }

        // Actualizar estado del envío
        boolean todasRecogidas = envio.getParadas().stream().allMatch(ParadaEnvio::isRecogido);
        if (todasRecogidas) {
            envio.setEstado(Envio.EN_ENTREGA);
        } else {
            envio.setEstado(Envio.EN_RECOGIDA);
        }
        envio.setUpdatedAt(LocalDateTime.now());

        Envio guardado = envioRepository.save(envio);

        // Notificar al comprador
        if (pedido.getComprador() != null && pedido.getComprador().getId() != null && notificacionService != null) {
            String tienda = parada.getNombreVendedor() != null ? parada.getNombreVendedor() : "el vendedor agrícola";
            notificacionService.enviar(
                    pedido.getComprador().getId(),
                    "Pedido Recogido y en Camino",
                    "El transportista ha recogido tus productos de " + tienda + " (Pedido #" + pedido.getNumeroFactura() + ") y están en camino.",
                    "INFO"
            );
        }

        return guardado;
    }

    /**
     * 4.5: Reportar incidencia en parada (finca cerrada, vendedor no entregó, etc.).
     * Motivo obligatorio. Notifica a vendedor y admin. Bloquea la entrega final del envío.
     */
    public Envio reportarProblemaParada(String envioId, String pedidoId, String motivo, String transportistaUsuarioId) {
        if (envioId == null || pedidoId == null) {
            throw new IllegalArgumentException("ID de envío y de pedido son obligatorios");
        }
        if (motivo == null || motivo.trim().isBlank()) {
            throw new IllegalArgumentException("El motivo de la incidencia es obligatorio");
        }

        Envio envio = envioRepository.findById(envioId)
                .orElseThrow(() -> new IllegalArgumentException("Envío no encontrado"));

        if (envio.getTransportistaId() == null || !envio.getTransportistaId().equals(transportistaUsuarioId)) {
            throw new SecurityException("Solo el transportista asignado puede reportar incidencias en este envío.");
        }

        if (Envio.ENTREGADO.equals(envio.getEstado()) || Envio.CANCELADO.equals(envio.getEstado())) {
            throw new IllegalStateException("No se pueden reportar incidencias en envíos finalizados (" + envio.getEstado() + ").");
        }

        ParadaEnvio parada = envio.getParadas().stream()
                .filter(p -> pedidoId.equals(p.getPedidoId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Parada no encontrada para el pedido: " + pedidoId));

        parada.setIncidenciaReportada(true);
        parada.setMotivoIncidencia(motivo.trim());
        parada.setFechaIncidencia(LocalDateTime.now());
        envio.setUpdatedAt(LocalDateTime.now());

        Envio guardado = envioRepository.save(envio);

        // Notificar al vendedor de la parada
        if (parada.getVendedorId() != null && notificacionService != null) {
            notificacionService.enviar(
                    parada.getVendedorId(),
                    "Incidencia Reportada en Parada",
                    "El transportista reportó un problema al recolectar tu pedido: " + motivo.trim(),
                    "DANGER"
            );
        }

        // Notificar a administradores
        if (usuarioRepository != null && notificacionService != null) {
            List<Usuario> admins = usuarioRepository.findByRolesContaining(Role.ADMIN);
            for (Usuario admin : admins) {
                notificacionService.enviar(
                        admin.getId(),
                        "Incidencia de Recolección",
                        "El transportista reportó una incidencia en la parada del pedido #" + pedidoId + " (Envío #" + envioId + "): " + motivo.trim(),
                        "WARNING"
                );
            }
        }

        return guardado;
    }

    /**
     * 4.7: Marcar Entrega Final (Carrier):
     * Idempotente si el envío ya fue marcado como ENTREGADO.
     * Valida que el transportista logueado sea el asignado al envío.
     * Valida que TODAS las paradas hayan sido recogidas (recogido == true) y sin incidencias pendientes.
     * Pasa el Envio a ENTREGADO y todos los sub-pedidos a ENTREGADO vía PedidoEstadoService.
     * Abona flete al transportista y notifica a comprador y vendedores.
     */
    public Envio marcarEnvioEntregado(String envioId, String transportistaUsuarioId) {
        if (envioId == null || envioId.isBlank()) {
            throw new IllegalArgumentException("ID de envío obligatorio");
        }

        Envio envio = envioRepository.findById(envioId)
                .orElseThrow(() -> new IllegalArgumentException("Envío no encontrado"));

        if (envio.getTransportistaId() == null || !envio.getTransportistaId().equals(transportistaUsuarioId)) {
            throw new SecurityException("Solo el transportista asignado puede marcar la entrega de este envío.");
        }

        if (Envio.ENTREGADO.equals(envio.getEstado())) {
            log.info("Envío {} ya se encuentra en estado ENTREGADO (idempotente)", envioId);
            return envio;
        }

        if (envio.getParadas() == null || envio.getParadas().isEmpty() || !envio.getParadas().stream().allMatch(ParadaEnvio::isRecogido)) {
            throw new IllegalStateException("No se puede marcar la entrega final: faltan paradas por recolectar.");
        }

        if (envio.getParadas().stream().anyMatch(ParadaEnvio::isIncidenciaReportada)) {
            throw new IllegalStateException("No se puede marcar la entrega final: existen incidencias reportadas en una o más paradas.");
        }

        envio.setEstado(Envio.ENTREGADO);
        envio.setFechaEntrega(LocalDateTime.now());
        envio.setUpdatedAt(LocalDateTime.now());
        Envio guardado = envioRepository.save(envio);

        // Marcar todos los sub-pedidos a ENTREGADO
        Set<String> compradoresNotificados = new HashSet<>();
        for (ParadaEnvio parada : envio.getParadas()) {
            if (parada.getPedidoId() != null) {
                pedidoRepository.findById(parada.getPedidoId()).ifPresent(sub -> {
                    if (!Pedido.CANCELADO.equalsIgnoreCase(sub.getEstado())) {
                        if (pedidoEstadoService != null) {
                            pedidoEstadoService.transicionar(
                                    sub,
                                    com.example.demo.Model.EstadoPedido.ENTREGADO,
                                    com.example.demo.Model.ActorTransicion.TRANSPORTISTA,
                                    "Entregado en destino por transportista"
                            );
                        } else {
                            sub.setEstado(Pedido.ENTREGADO);
                            sub.setFechaEntrega(LocalDateTime.now());
                            sub.setUpdatedAt(LocalDateTime.now());
                            pedidoRepository.save(sub);
                        }

                        // Notificar al vendedor de este sub-pedido
                        if (sub.getVendedor() != null && sub.getVendedor().getId() != null && notificacionService != null) {
                            notificacionService.enviar(
                                    sub.getVendedor().getId(),
                                    "Pedido Entregado",
                                    "Tu pedido #" + sub.getNumeroFactura() + " ha sido entregado exitosamente al comprador.",
                                    "SUCCESS"
                            );
                        }

                        // Notificar al comprador
                        if (sub.getComprador() != null && sub.getComprador().getId() != null
                                && notificacionService != null && compradoresNotificados.add(sub.getComprador().getId())) {
                            notificacionService.enviar(
                                    sub.getComprador().getId(),
                                    "¡Compra Entregada!",
                                    "Tu pedido ha sido entregado en la dirección de destino. ¡Gracias por apoyar al campo!",
                                    "SUCCESS"
                            );
                        }
                    }
                });
            }
        }

        // Abonar flete al transportista (idempotente; si falla no revierte la entrega)
        try {
            if (billeteraService != null) {
                billeteraService.registrarFleteEntregado(guardado);
            }
        } catch (Exception e) {
            log.warn("No se pudo abonar el flete al transportista para el envío {}: {}", envioId, e.getMessage());
        }

        return guardado;
    }

    /**
     * Remueve una parada de la ruta si un sub-pedido es cancelado.
     * Recalcula la ganancia total del flete para el transportista.
     * Si no quedan paradas, marca el Envio como CANCELADO.
     */
    public Optional<Envio> removerParadaPorPedidoCancelado(String pedidoId) {
        if (pedidoId == null || pedidoId.isBlank()) {
            return Optional.empty();
        }

        // 3.3: Query eficiente por parada en lugar de findAll en memoria
        Optional<Envio> envioOpt = (envioRepository != null)
                ? envioRepository.findByParadaPedidoId(pedidoId)
                : Optional.empty();

        if (envioOpt.isEmpty() && envioRepository != null) {
            envioOpt = envioRepository.findByParadas_PedidoId(pedidoId);
        }

        if (envioOpt.isEmpty() && envioRepository != null) {
            // Fallback
            envioOpt = envioRepository.findAll().stream()
                    .filter(e -> e.getParadas() != null && e.getParadas().stream().anyMatch(p -> pedidoId.equals(p.getPedidoId())))
                    .findFirst();
        }

        if (envioOpt.isEmpty()) {
            return Optional.empty();
        }

        Envio envio = envioOpt.get();

        // 3.3: No actuar sobre envíos ya entregados o cancelados
        if (Envio.ENTREGADO.equals(envio.getEstado()) || Envio.CANCELADO.equals(envio.getEstado())) {
            log.info("Envío {} está en estado terminal ({}), no se modifica su ruta tras cancelación de sub-pedido {}",
                    envio.getId(), envio.getEstado(), pedidoId);
            return Optional.of(envio);
        }

        if (envio.getParadas() != null) {
            boolean removida = envio.getParadas().removeIf(p -> pedidoId.equals(p.getPedidoId()));
            if (removida) {
                // 3.4: Recalcular flete: suma de tarifas de paradas
                long nuevoTotal = envio.getParadas().stream()
                        .mapToLong(p -> p.getTarifaCentavos() != null ? p.getTarifaCentavos() : 0L)
                        .sum();
                envio.setCostoTotalCentavos(nuevoTotal);

                // Advertencia si difiere del costo de envío registrado en la compra
                if (envio.getCompraId() != null && compraRepository != null) {
                    compraRepository.findById(envio.getCompraId()).ifPresent(compra -> {
                        if (compra.getEnvioCentavos() != null && compra.getEnvioCentavos() != nuevoTotal) {
                            log.warn("Flete de Envío {} ({}) difiere de compra.envioCentavos ({}) tras cancelación de parada {}",
                                    envio.getId(), nuevoTotal, compra.getEnvioCentavos(), pedidoId);
                        }
                    });
                }

                // 3.3: Si queda sin paradas -> CANCELADO
                if (envio.getParadas().isEmpty()) {
                    envio.setEstado(Envio.CANCELADO);
                } else if (envio.getParadas().stream().allMatch(ParadaEnvio::isRecogido)) {
                    // Si todas las paradas restantes ya fueron recogidas -> EN_ENTREGA
                    envio.setEstado(Envio.EN_ENTREGA);
                }

                envio.setUpdatedAt(LocalDateTime.now());
                Envio guardado = envioRepository.save(envio);

                // Si tenía transportista asignado, avisarle del ajuste de ruta y flete
                if (guardado.getTransportistaId() != null && notificacionService != null) {
                    notificacionService.enviar(
                            guardado.getTransportistaId(),
                            "Ruta Actualizada (Cancelación)",
                            "Una parada ha sido cancelada en tu ruta. El nuevo flete asignado es: " + guardado.getCostoTotalFormateado(),
                            "WARNING"
                    );
                }

                return Optional.of(guardado);
            }
        }

        return Optional.empty();
    }

    /**
     * 4.1 y 4.4: Verifica envíos en PENDIENTE_ASIGNACION que lleven más de N horas sin transportista.
     * Mide el reloj de expiración desde fechaDisponibleDesde (cuando todas las paradas pasaron a LISTO_PARA_RECOGER).
     * Si no tiene fechaDisponibleDesde y tiene paradas, no expira (aún no está listo en fincas).
     * Pasa a SIN_TRANSPORTISTA y alerta a comprador, vendedores y administradores.
     */
    public int verificarEnviosSinTransportista() {
        final int horasMax = (appConfigService != null)
                ? appConfigService.obtenerValorInt("HORAS_MAX_SIN_TRANSPORTISTA", 24)
                : 24;
        LocalDateTime limite = LocalDateTime.now().minusHours(horasMax);

        List<Envio> pendientes = envioRepository.findByEstado(Envio.PENDIENTE_ASIGNACION);
        int expirados = 0;

        for (Envio envio : pendientes) {
            LocalDateTime fechaBase = envio.getFechaDisponibleDesde();
            // Fallback a fechaCreacion únicamente si no hay paradas (compatibilidad con tests legados)
            if (fechaBase == null && (envio.getParadas() == null || envio.getParadas().isEmpty())) {
                fechaBase = envio.getFechaCreacion();
            }

            if (fechaBase != null && fechaBase.isBefore(limite)) {
                envio.setEstado(Envio.SIN_TRANSPORTISTA);
                envio.setUpdatedAt(LocalDateTime.now());
                envioRepository.save(envio);
                expirados++;

                log.warn("Envío {} expiró sin transportista (> {} horas desde {}). Marcado como SIN_TRANSPORTISTA.",
                        envio.getId(), horasMax, fechaBase);

                if (envio.getCompraId() != null && compraRepository != null) {
                    compraRepository.findById(envio.getCompraId()).ifPresent(compra -> {
                        if (compra.getComprador() != null && compra.getComprador().getId() != null && notificacionService != null) {
                            notificacionService.enviar(
                                    compra.getComprador().getId(),
                                    "Envío Sin Transportista Asignado",
                                    "No fue posible asignar un transportista para tu compra dentro del tiempo límite (" + horasMax + "h). El equipo de soporte se comunicará contigo para el reembolso manual o reasignación.",
                                    "WARNING"
                            );
                        }
                    });
                }

                // 4.4: Notificar a los vendedores de cada parada
                if (envio.getParadas() != null && notificacionService != null) {
                    Set<String> vendedoresNotificados = new HashSet<>();
                    for (ParadaEnvio p : envio.getParadas()) {
                        if (p.getVendedorId() != null && vendedoresNotificados.add(p.getVendedorId())) {
                            notificacionService.enviar(
                                    p.getVendedorId(),
                                    "Envío sin transportista",
                                    "El envío para tu venta #" + p.getPedidoId() + " no consiguió transportista en " + horasMax + " horas. Soporte atenderá la reasignación.",
                                    "WARNING"
                            );
                        }
                    }
                }

                if (usuarioRepository != null && notificacionService != null) {
                    List<Usuario> admins = usuarioRepository.findByRolesContaining(Role.ADMIN);
                    for (Usuario admin : admins) {
                        notificacionService.enviar(
                                admin.getId(),
                                "Alerta: Envío Sin Transportista",
                                "El envío #" + envio.getId() + " (Compra #" + envio.getCompraId() + ") no consiguió transportista en " + horasMax + " horas. Requiere gestión manual de reembolso o reasignación.",
                                "WARNING"
                        );
                    }
                }
            }
        }

        return expirados;
    }

    /**
     * 4.2: Timeout de asignado.
     * Si un transportista tomó el envío (ASIGNADO), pero pasan HORAS_MAX_ASIGNADO_SIN_RECOGIDA (24h)
     * sin que haya recogido ninguna parada, regresa a PENDIENTE_ASIGNACION, desasigna el transportista
     * y notifica al transportista y a los vendedores.
     */
    public int verificarEnviosAsignadosExpirados() {
        final int horasMax = (appConfigService != null)
                ? appConfigService.obtenerValorInt("HORAS_MAX_ASIGNADO_SIN_RECOGIDA", 24)
                : 24;
        LocalDateTime limite = LocalDateTime.now().minusHours(horasMax);

        List<Envio> asignados = envioRepository.findByEstado(Envio.ASIGNADO);
        int revertidos = 0;

        for (Envio envio : asignados) {
            LocalDateTime fechaAsig = envio.getFechaAsignacion();
            if (fechaAsig != null && fechaAsig.isBefore(limite)) {
                boolean algunaRecogida = envio.getParadas() != null && envio.getParadas().stream().anyMatch(ParadaEnvio::isRecogido);
                if (!algunaRecogida) {
                    String transportistaIdViejo = envio.getTransportistaId();

                    envio.setEstado(Envio.PENDIENTE_ASIGNACION);
                    envio.setTransportistaId(null);
                    envio.setTransportista(null);
                    envio.setFechaAsignacion(null);
                    envio.setUpdatedAt(LocalDateTime.now());
                    envioRepository.save(envio);
                    revertidos++;

                    log.warn("Envío {} superó {} horas en ASIGNADO sin iniciar recolecciones. Regresado a PENDIENTE_ASIGNACION.",
                            envio.getId(), horasMax);

                    if (transportistaIdViejo != null && notificacionService != null) {
                        notificacionService.enviar(
                                transportistaIdViejo,
                                "Servicio de Envío Retirado por Inactividad",
                                "El envío #" + envio.getId() + " fue retirado de tu cuenta por superar " + horasMax + " horas sin iniciar recolecciones.",
                                "WARNING"
                        );
                    }

                    if (envio.getParadas() != null && notificacionService != null) {
                        Set<String> vendedoresNotificados = new HashSet<>();
                        for (ParadaEnvio p : envio.getParadas()) {
                            if (p.getVendedorId() != null && vendedoresNotificados.add(p.getVendedorId())) {
                                notificacionService.enviar(
                                        p.getVendedorId(),
                                        "Envío Disponible para Nuevos Transportistas",
                                        "El transportista asignado no se presentó a tiempo (" + horasMax + "h). El envío vuelve a estar disponible para la red.",
                                        "INFO"
                                );
                            }
                        }
                    }
                }
            }
        }

        return revertidos;
    }

    /**
     * 4.4: Acción administrativa para reasignar un envío en SIN_TRANSPORTISTA.
     * Devuelve el envío a PENDIENTE_ASIGNACION con 24h nuevas (fechaDisponibleDesde = now()).
     */
    public Envio reasignarEnvioSinTransportista(String envioId, String adminId) {
        if (envioId == null || envioId.isBlank()) {
            throw new IllegalArgumentException("ID de envío requerido");
        }

        Envio envio = envioRepository.findById(envioId)
                .orElseThrow(() -> new IllegalArgumentException("Envío no encontrado"));

        if (!Envio.SIN_TRANSPORTISTA.equals(envio.getEstado())) {
            throw new IllegalStateException("Solo se pueden reasignar envíos en estado SIN_TRANSPORTISTA.");
        }

        envio.setEstado(Envio.PENDIENTE_ASIGNACION);
        envio.setTransportistaId(null);
        envio.setTransportista(null);
        envio.setFechaAsignacion(null);
        envio.setFechaDisponibleDesde(LocalDateTime.now());
        envio.setUpdatedAt(LocalDateTime.now());

        Envio guardado = envioRepository.save(envio);

        if (guardado.getParadas() != null && notificacionService != null) {
            Set<String> vendedoresNotificados = new HashSet<>();
            for (ParadaEnvio p : guardado.getParadas()) {
                if (p.getVendedorId() != null && vendedoresNotificados.add(p.getVendedorId())) {
                    notificacionService.enviar(
                            p.getVendedorId(),
                            "Envío Reasignado",
                            "Un administrador ha vuelto a publicar la ruta de tu pedido #" + p.getPedidoId() + " en la bolsa de cargas.",
                            "INFO"
                    );
                }
            }
        }

        return guardado;
    }

    /**
     * 4.8: Reconciliador de envíos faltantes.
     * Busca compras PAGADAS o COMPLETADAS con modalidad DOMICILIO que no tengan Envio registrado.
     * Reconstruye el documento Envio, calcula las paradas y actualiza fechaDisponibleDesde si aplica.
     */
    public int reconciliarEnviosFaltantes() {
        if (compraRepository == null || envioRepository == null || pedidoRepository == null) {
            return 0;
        }

        List<Compra> compras = compraRepository.findAll();
        int reconciliados = 0;

        for (Compra compra : compras) {
            String estado = compra.getEstado();
            if (!Compra.PAGADA.equals(estado) && !Compra.COMPLETADA.equals(estado)) {
                continue;
            }


            // Si ya existe el envío para esta compra, omitir
            if (envioRepository.findByCompraId(compra.getId()).isPresent()) {
                continue;
            }

            List<Pedido> subPedidos = pedidoRepository.findByCompraId(compra.getId());
            if (subPedidos.isEmpty()) {
                continue;
            }

            // Construir paradas
            List<ParadaEnvio> paradas = new ArrayList<>();
            com.example.demo.Model.embebidos.DireccionPedido puntoEntrega = null;

            for (Pedido ped : subPedidos) {
                if (puntoEntrega == null && ped.getDireccionEntrega() != null) {
                    puntoEntrega = ped.getDireccionEntrega();
                }

                String vendedorId = ped.getVendedor() != null ? ped.getVendedor().getId() : null;
                String nombreVendedor = ped.getVendedor() != null ? ped.getVendedor().getNombre() : "Vendedor";
                String direccionRecogida = ped.getCiudadOrigenEnvio() != null ? ped.getCiudadOrigenEnvio() : "Dirección finca";
                String ciudadRecogida = ped.getCiudadOrigenEnvio() != null ? ped.getCiudadOrigenEnvio() : "Cartagena";
                Integer zona = ped.getZonaOrigenEnvio() != null ? ped.getZonaOrigenEnvio() : 1;
                Long tarifaCentavos = ped.getTarifaEnvioCentavos() != null ? ped.getTarifaEnvioCentavos() : 0L;

                ParadaEnvio parada = new ParadaEnvio(
                        ped.getId(),
                        vendedorId,
                        nombreVendedor,
                        direccionRecogida,
                        ciudadRecogida,
                        zona,
                        tarifaCentavos
                );

                if (ped.getItems() != null) {
                    List<String> itemsResumen = ped.getItems().stream()
                            .map(i -> {
                                String nom = i.getNombreSnapshot() != null ? i.getNombreSnapshot() : "Producto";
                                String cant = i.getCantidad() != null ? String.valueOf(i.getCantidad()) : "1";
                                return cant + "x " + nom;
                            })
                            .collect(Collectors.toList());
                    parada.setItemsResumen(itemsResumen);
                }

                paradas.add(parada);
            }

            Long costoTotal = compra.getEnvioCentavos() != null ? compra.getEnvioCentavos() : 0L;
            Envio nuevoEnvio = new Envio(compra.getId(), paradas, puntoEntrega, costoTotal);
            nuevoEnvio.setEstado(Envio.PENDIENTE_ASIGNACION);

            // Si todas las paradas ya están listas para recoger
            boolean todosListos = subPedidos.stream().allMatch(p -> {
                com.example.demo.Model.EstadoPedido ep = p.getEstadoEnum();
                return ep == com.example.demo.Model.EstadoPedido.LISTO_PARA_RECOGER
                        || ep == com.example.demo.Model.EstadoPedido.ENVIADO
                        || ep == com.example.demo.Model.EstadoPedido.ENTREGADO;
            });
            if (todosListos) {
                nuevoEnvio.setFechaDisponibleDesde(LocalDateTime.now());
            } else {
                nuevoEnvio.setFechaDisponibleDesde(null);
            }

            envioRepository.save(nuevoEnvio);
            reconciliados++;
            log.info("Reconciliado envío para compraId {} con {} paradas y costo {} centavos",
                    compra.getId(), paradas.size(), costoTotal);
        }

        return reconciliados;
    }

    /**
     * Método histórico para retrocompatibilidad con controladores existentes.
     */
    public List<Pedido> obtenerPedidosDisponiblesParaEnvio() {
        return pedidoRepository.findAll().stream()
                .filter(p -> "CONFIRMADO".equalsIgnoreCase(p.getEstado()) || "ENVIADO".equalsIgnoreCase(p.getEstado()))
                .sorted((p1, p2) -> p2.getFechaPedido().compareTo(p1.getFechaPedido()))
                .collect(Collectors.toList());
    }
}
