package com.example.demo.services;

import com.example.demo.Model.Pedido;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.security.SecurityContextRefreshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class VendedorService {

    /**
     * Estados que representan una venta real para el vendedor.
     * PENDIENTE (pago sin terminar) y CANCELADO (pago rechazado) no deben aparecer en su panel.
     */
    public static final Set<String> ESTADOS_VENTA_VISIBLES = Set.of(
            "CONFIRMADO",
            "EN_PROCESO",
            "PROCESANDO",
            Pedido.LISTO_PARA_RECOGER,
            "ENVIADO",
            "ENTREGADO",
            "COMPLETADO",
            Pedido.EN_REVISION);

    /**
     * Transiciones que el vendedor puede realizar. ENVIADO y ENTREGADO son exclusivos
     * del transportista/sistema logístico.
     */
    private static final Map<String, String> SIGUIENTE_ESTADO = Map.of(
            "CONFIRMADO", "EN_PROCESO",
            "EN_PROCESO", Pedido.LISTO_PARA_RECOGER,
            "PROCESANDO", Pedido.LISTO_PARA_RECOGER);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private SecurityContextRefreshService securityContextRefreshService;

    @Autowired(required = false)
    private com.example.demo.repository.EnvioRepository envioRepository;

    @Autowired(required = false)
    private TransportistaService transportistaService;

    /**
     * Crea un perfil de vendedor para un usuario existente
     */
    public Usuario crearPerfilVendedor(String usuarioId, String razonSocial,
            String telefonoContacto, String direccionNegocio,
            String descripcionNegocio, String cuentaBancaria,
            String banco, String tipoProductos) {

        // Validar que el usuario existe
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Validar que el usuario no tiene ya un perfil de vendedor
        if (usuario.getPerfilVendedor() != null) {
            throw new IllegalArgumentException("Este usuario ya tiene un perfil de vendedor");
        }

        // Validaciones de campos obligatorios
        if (telefonoContacto == null || telefonoContacto.trim().isEmpty()) {
            throw new IllegalArgumentException("El teléfono de contacto es obligatorio");
        }

        // Crear el perfil embebido
        PerfilVendedor perfil = new PerfilVendedor();
        perfil.setRazonSocial(razonSocial);
        perfil.setTelefonoContacto(telefonoContacto);
        perfil.setDireccionNegocio(direccionNegocio);
        perfil.setDescripcionNegocio(descripcionNegocio);
        perfil.setCuentaBancaria(cuentaBancaria);
        perfil.setBanco(banco);
        perfil.setTipoProductos(tipoProductos);
        perfil.setVerificado(false);

        // Embeber el perfil y agregar el rol VENDEDOR (manteniendo otros roles como COMPRADOR)
        usuario.setPerfilVendedor(perfil);
        usuario.addRole(Role.VENDEDOR);

        Usuario guardado = usuarioRepository.save(usuario);
        // Reconstruir Authentication con el nuevo rol (evita 403 sin cerrar sesión)
        securityContextRefreshService.refrescarAutenticacion(guardado.getEmail());
        return guardado;
    }

    /**
     * Obtiene el perfil de vendedor de un usuario
     */
    public Optional<PerfilVendedor> obtenerPerfilPorUsuarioId(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getPerfilVendedor);
    }

    /**
     * Verifica si un usuario es vendedor
     */
    public boolean esVendedor(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(u -> u.hasRole(Role.VENDEDOR))
                .orElse(false);
    }

    /**
     * Actualiza el perfil de vendedor
     */
    public Usuario actualizarPerfil(String usuarioId, String razonSocial,
            String telefonoContacto, String direccionNegocio,
            String descripcionNegocio, String cuentaBancaria,
            String banco, String tipoProductos) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        PerfilVendedor perfil = usuario.getPerfilVendedor();
        if (perfil == null) {
            throw new IllegalArgumentException("Este usuario no tiene perfil de vendedor");
        }

        if (razonSocial != null)
            perfil.setRazonSocial(razonSocial);
        if (telefonoContacto != null)
            perfil.setTelefonoContacto(telefonoContacto);
        if (direccionNegocio != null)
            perfil.setDireccionNegocio(direccionNegocio);
        if (descripcionNegocio != null)
            perfil.setDescripcionNegocio(descripcionNegocio);
        if (cuentaBancaria != null)
            perfil.setCuentaBancaria(cuentaBancaria);
        if (banco != null)
            perfil.setBanco(banco);
        if (tipoProductos != null)
            perfil.setTipoProductos(tipoProductos);

        usuario.setPerfilVendedor(perfil);
        return usuarioRepository.save(usuario);
    }

    /**
     * Elimina el perfil de vendedor (el usuario deja de ser vendedor)
     */
    public void eliminarPerfilVendedor(String usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        usuario.setPerfilVendedor(null);
        if (usuario.getRoles() != null) {
            usuario.getRoles().remove(Role.VENDEDOR);
            // Asegurar que siempre tenga al menos el rol COMPRADOR
            if (usuario.getRoles().isEmpty()) {
                usuario.addRole(Role.COMPRADOR);
            }
        }
        Usuario guardado = usuarioRepository.save(usuario);
        // Reconstruir Authentication tras eliminar el rol VENDEDOR
        securityContextRefreshService.refrescarAutenticacion(guardado.getEmail());
    }

    /**
     * Obtiene todos los pedidos del vendedor, incluidos los que no son ventas reales.
     * Se conserva para tareas administrativas; las vistas deben usar {@link #obtenerVentasDelVendedor(String)}.
     */
    public List<Pedido> obtenerPedidosDelVendedor(String vendedorId) {
        return pedidoRepository.findByVendedor_Id(vendedorId);
    }

    /**
     * Ventas visibles en el panel del vendedor: solo pedidos pagados/confirmados.
     * Los pedidos PENDIENTE de un pago abandonado y los CANCELADO nunca se muestran como ventas.
     */
    public List<Pedido> obtenerVentasDelVendedor(String vendedorId) {
        if (vendedorId == null || vendedorId.isBlank()) {
            return List.of();
        }
        return pedidoRepository.findByVendedor_IdOrderByFechaPedidoDesc(vendedorId)
                .stream()
                .filter(this::esVentaVisible)
                .sorted(Comparator
                        .comparing(Pedido::getFechaPedido, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    /**
     * Obtiene los pedidos del vendedor filtrados por estado
     */
    public List<Pedido> obtenerPedidosDelVendedorPorEstado(String vendedorId, String estado) {
        return pedidoRepository.findByVendedor_IdAndEstado(vendedorId, estado);
    }

    /**
     * Busca una venta del vendedor validando la propiedad del pedido.
     * Evita que un vendedor consulte o modifique pedidos de otra tienda por ID.
     */
    public Optional<Pedido> obtenerVentaDelVendedor(String pedidoId, String vendedorId) {
        if (pedidoId == null || vendedorId == null) {
            return Optional.empty();
        }
        return pedidoRepository.findByIdAndVendedor_Id(pedidoId, vendedorId)
                .filter(this::esVentaVisible);
    }

    public boolean esVentaVisible(Pedido pedido) {
        if (pedido == null || pedido.getEstado() == null) {
            return false;
        }
        return ESTADOS_VENTA_VISIBLES.contains(normalizarEstado(pedido.getEstado()));
    }

    /**
     * Siguiente estado permitido para el vendedor. Un pedido listo, enviado, entregado
     * o en revisión no lo modifica el vendedor.
     */
    public Optional<String> obtenerSiguienteEstado(String estadoActual) {
        return Optional.ofNullable(SIGUIENTE_ESTADO.get(normalizarEstado(estadoActual)));
    }

    public String etiquetaAccionSiguiente(String estadoActual) {
        String siguiente = SIGUIENTE_ESTADO.get(normalizarEstado(estadoActual));
        if (siguiente == null) {
            return null;
        }
        return switch (siguiente) {
            case "EN_PROCESO" -> "Comenzar preparación";
            case Pedido.LISTO_PARA_RECOGER -> "Marcar listo para recoger";
            default -> "Actualizar estado";
        };
    }

    /**
     * Actualiza el estado de un pedido garantizando el ciclo de vida de única responsabilidad:
     * El vendedor ÚNICAMENTE puede avanzar: CONFIRMADO -> EN_PROCESO -> LISTO_PARA_RECOGER.
     * Los estados ENVIADO y ENTREGADO son exclusivos del transportista/sistema logístico.
     */
    public void actualizarEstadoPedido(String pedidoId, String nuevoEstado) {
        actualizarEstadoPedido(pedidoId, nuevoEstado, null);
    }

    public void actualizarEstadoPedido(String pedidoId, String nuevoEstado, String vendedorId) {
        if (pedidoId == null || nuevoEstado == null) {
            throw new IllegalArgumentException("El ID de pedido y el nuevo estado son obligatorios");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        if (vendedorId != null && pedido.getVendedor() != null && pedido.getVendedor().getId() != null) {
            if (!pedido.getVendedor().getId().equals(vendedorId)) {
                throw new SecurityException("No tienes permiso para modificar este pedido");
            }
        }

        String estadoActual = pedido.getEstado() != null ? pedido.getEstado().toUpperCase() : "PENDIENTE";
        String estadoDeseado = nuevoEstado.trim().toUpperCase();

        // Bloqueo explícito de estados reservados para la logística (carrier)
        if (Pedido.ENVIADO.equals(estadoDeseado) || Pedido.ENTREGADO.equals(estadoDeseado) || "COMPLETADO".equals(estadoDeseado)) {
            throw new IllegalStateException("El vendedor no puede marcar el pedido como enviado o entregado. Esta acción la realiza exclusivamente el transportista.");
        }

        // Regla estricta: CONFIRMADO -> EN_PROCESO -> LISTO_PARA_RECOGER
        if (Pedido.CONFIRMADO.equals(estadoActual)) {
            if (!Pedido.EN_PROCESO.equals(estadoDeseado) && !"PROCESANDO".equals(estadoDeseado)) {
                throw new IllegalStateException("Desde 'Confirmado' solo se puede avanzar a 'En proceso'. Estado intentado: " + nuevoEstado);
            }
        } else if (Pedido.EN_PROCESO.equals(estadoActual) || "PROCESANDO".equals(estadoActual)) {
            if (!Pedido.LISTO_PARA_RECOGER.equals(estadoDeseado)) {
                throw new IllegalStateException("Desde 'En proceso' solo se puede avanzar a 'Listo para recoger'. Estado intentado: " + nuevoEstado);
            }
        } else {
            throw new IllegalStateException("Transición no permitida para el vendedor desde el estado '" + estadoActual + "' hacia '" + nuevoEstado + "'. El flujo permitido es: CONFIRMADO -> EN_PROCESO -> LISTO_PARA_RECOGER.");
        }

        pedido.setEstado(estadoDeseado);
        pedido.setUpdatedAt(LocalDateTime.now());
        pedidoRepository.save(pedido);

        // Notificar al Comprador
        if (pedido.getComprador() != null && pedido.getComprador().getId() != null && notificacionService != null) {
            if (Pedido.EN_PROCESO.equals(estadoDeseado) || "PROCESANDO".equals(estadoDeseado)) {
                notificacionService.enviar(
                        pedido.getComprador().getId(),
                        "Pedido en Preparación",
                        "El vendedor ha comenzado a preparar los productos de tu pedido #" + pedido.getNumeroFactura() + ".",
                        "INFO"
                );
            } else if (Pedido.LISTO_PARA_RECOGER.equals(estadoDeseado)) {
                notificacionService.enviar(
                        pedido.getComprador().getId(),
                        "Pedido Listo para Recoger",
                        "Tu pedido #" + pedido.getNumeroFactura() + " está listo en finca para ser recogido por el transportista.",
                        "SUCCESS"
                );
            }
        }

        // Si pasó a LISTO_PARA_RECOGER y ya hay transportista asignado al envío, notificarle
        if (Pedido.LISTO_PARA_RECOGER.equals(estadoDeseado) && pedido.getCompraId() != null
                && envioRepository != null && notificacionService != null) {
            envioRepository.findByCompraId(pedido.getCompraId()).ifPresent(envio -> {
                if (envio.getTransportistaId() != null) {
                    String nombreTienda = pedido.getVendedor() != null && pedido.getVendedor().getNombre() != null
                            ? pedido.getVendedor().getNombre() : "el vendedor agrícola";
                    notificacionService.enviar(
                            envio.getTransportistaId(),
                            "Parada Lista para Recogida",
                            "El vendedor " + nombreTienda + " tiene listo el paquete (Pedido #" + pedido.getNumeroFactura() + ") para tu recolección.",
                            "INFO"
                    );
                }
            });
        }
    }

    /**
     * Cancela un pedido del vendedor y ajusta la ruta del transportista recalculando el flete.
     */
    public void cancelarPedido(String pedidoId, String motivo, String vendedorId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        if (vendedorId != null && pedido.getVendedor() != null && pedido.getVendedor().getId() != null) {
            if (!pedido.getVendedor().getId().equals(vendedorId)) {
                throw new SecurityException("No tienes permiso para cancelar este pedido");
            }
        }

        if (Pedido.ENVIADO.equalsIgnoreCase(pedido.getEstado()) || Pedido.ENTREGADO.equalsIgnoreCase(pedido.getEstado())) {
            throw new IllegalStateException("No se puede cancelar un pedido que ya fue enviado o entregado.");
        }

        pedido.setEstado(Pedido.CANCELADO);
        pedido.setUpdatedAt(LocalDateTime.now());
        pedidoRepository.save(pedido);

        if (transportistaService != null) {
            transportistaService.removerParadaPorPedidoCancelado(pedidoId);
        }

        if (pedido.getComprador() != null && pedido.getComprador().getId() != null && notificacionService != null) {
            notificacionService.enviar(
                    pedido.getComprador().getId(),
                    "Pedido Cancelado",
                    "Tu pedido #" + pedido.getNumeroFactura() + " ha sido cancelado. Motivo: " + (motivo != null ? motivo : "Cancelación registrada"),
                    "WARNING"
            );
        }
    }

    /**
     * Calcula el total de ventas confirmadas del vendedor.
     */
    public double calcularTotalVentas(String vendedorId) {
        return obtenerVentasDelVendedor(vendedorId).stream()
                .map(Pedido::getTotal)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    public long contarVentasPorEstados(String vendedorId, String... estados) {
        Set<String> buscados = Set.of(estados);
        return obtenerVentasDelVendedor(vendedorId).stream()
                .filter(p -> buscados.contains(normalizarEstado(p.getEstado())))
                .count();
    }

    private static String normalizarEstado(String estado) {
        return estado == null ? "" : estado.trim().toUpperCase();
    }

    static String etiquetaEstado(String estado) {
        return switch (normalizarEstado(estado)) {
            case "PENDIENTE" -> "Pendiente de pago";
            case "CONFIRMADO" -> "Confirmado";
            case "EN_PROCESO", "PROCESANDO" -> "En preparación";
            case Pedido.LISTO_PARA_RECOGER -> "Listo para recoger";
            case "ENVIADO" -> "Enviado";
            case "ENTREGADO", "COMPLETADO" -> "Entregado";
            case "CANCELADO" -> "Cancelado";
            case Pedido.EN_REVISION -> "En revisión";
            default -> estado;
        };
    }
}