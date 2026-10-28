package com.example.demo.services;

import com.example.demo.Model.ActorTransicion;
import com.example.demo.Model.CanceladoPor;
import com.example.demo.Model.EstadoPedido;
import com.example.demo.Model.ModalidadEntrega;
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

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoEstadoService pedidoEstadoService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private SecurityContextRefreshService securityContextRefreshService;

    @Autowired(required = false)
    private com.example.demo.repository.EnvioRepository envioRepository;

    @Autowired(required = false)
    private TransportistaService transportistaService;

    @Autowired(required = false)
    private PedidoCancelacionService pedidoCancelacionService;

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
        EstadoPedido ep = pedido.getEstadoEnum();
        if (ep == null || ep == EstadoPedido.PENDIENTE) {
            return false;
        }
        if (ep == EstadoPedido.CANCELADO) {
            // 2.5: CANCELADO solo si canceladoPor == VENDEDOR || canceladoPor == ADMIN. SISTEMA_PAGO oculto.
            return pedido.getCanceladoPor() == CanceladoPor.VENDEDOR
                    || pedido.getCanceladoPor() == CanceladoPor.ADMIN;
        }
        return true;
    }

    /**
     * Siguiente estado permitido para el vendedor considerando la modalidad de entrega.
     */
    public Optional<String> obtenerSiguienteEstado(Pedido pedido) {
        if (pedido == null) return Optional.empty();
        EstadoPedido ep = pedido.getEstadoEnum();
        if (ep == null) return Optional.empty();

        return switch (ep) {
            case CONFIRMADO -> Optional.of(EstadoPedido.EN_PROCESO.name());
            case EN_PROCESO -> Optional.of(EstadoPedido.LISTO_PARA_RECOGER.name());
            case LISTO_PARA_RECOGER -> {
                if (pedido.isRecogeEnTienda()) {
                    yield Optional.of(EstadoPedido.ENTREGADO.name());
                } else {
                    yield Optional.empty();
                }
            }
            default -> Optional.empty();
        };
    }

    public String etiquetaAccionSiguiente(Pedido pedido) {
        if (pedido == null) return null;
        EstadoPedido ep = pedido.getEstadoEnum();
        if (ep == null) return null;

        return switch (ep) {
            case CONFIRMADO -> "Comenzar preparación";
            case EN_PROCESO -> "Marcar listo para recoger";
            case LISTO_PARA_RECOGER -> {
                if (pedido.isRecogeEnTienda()) {
                    yield "Validar código y entregar";
                } else {
                    yield null;
                }
            }
            default -> null;
        };
    }

    public Optional<String> obtenerSiguienteEstado(String estadoActual) {
        EstadoPedido ep = EstadoPedido.interpretar(estadoActual).orElse(null);
        if (ep == EstadoPedido.CONFIRMADO) return Optional.of(EstadoPedido.EN_PROCESO.name());
        if (ep == EstadoPedido.EN_PROCESO) return Optional.of(EstadoPedido.LISTO_PARA_RECOGER.name());
        return Optional.empty();
    }

    public String etiquetaAccionSiguiente(String estadoActual) {
        EstadoPedido ep = EstadoPedido.interpretar(estadoActual).orElse(null);
        if (ep == EstadoPedido.CONFIRMADO) return "Comenzar preparación";
        if (ep == EstadoPedido.EN_PROCESO) return "Marcar listo para recoger";
        return null;
    }

    /**
     * 2.6: Actualiza el estado de un pedido del vendedor.
     * Requiere obligatoriamente vendedorId y valida que sea el dueño del pedido (falla cerrado).
     * Delega la transición a PedidoEstadoService para cumplir la máquina centralizada de estados.
     */
    public void actualizarEstadoPedido(String pedidoId, String nuevoEstado, String vendedorId) {
        if (pedidoId == null || nuevoEstado == null || vendedorId == null || vendedorId.isBlank()) {
            throw new IllegalArgumentException("El ID de pedido, el nuevo estado y el ID de vendedor son obligatorios");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        if (pedido.getVendedor() == null || pedido.getVendedor().getId() == null
                || !vendedorId.equals(pedido.getVendedor().getId())) {
            throw new SecurityException("No tienes permiso para modificar este pedido");
        }

        EstadoPedido estadoDeseado = EstadoPedido.interpretar(nuevoEstado)
                .orElseThrow(() -> new IllegalArgumentException("Estado no reconocido: " + nuevoEstado));

        // Bloqueo explícito de estados reservados para la logística (carrier)
        if (estadoDeseado == EstadoPedido.ENVIADO || estadoDeseado == EstadoPedido.ENTREGADO) {
            throw new IllegalStateException("El vendedor no puede marcar el pedido como enviado o entregado. Esta acción la realiza exclusivamente el transportista.");
        }

        // Regla estricta del vendedor: CONFIRMADO -> EN_PROCESO -> LISTO_PARA_RECOGER
        EstadoPedido estadoActual = pedido.getEstadoEnum();
        if (estadoActual == EstadoPedido.CONFIRMADO && estadoDeseado != EstadoPedido.EN_PROCESO) {
            throw new IllegalStateException("Desde 'Confirmado' solo se puede avanzar a 'En proceso'. Estado intentado: " + nuevoEstado);
        }
        if (estadoActual == EstadoPedido.EN_PROCESO && estadoDeseado != EstadoPedido.LISTO_PARA_RECOGER) {
            throw new IllegalStateException("Desde 'En proceso' solo se puede avanzar a 'Listo para recoger'. Estado intentado: " + nuevoEstado);
        }

        // Transición centralizada
        pedidoEstadoService.transicionar(
                pedido,
                estadoDeseado,
                ActorTransicion.VENDEDOR,
                "Actualización realizada por el vendedor"
        );

        if (estadoDeseado == EstadoPedido.LISTO_PARA_RECOGER) {
            pedido.setFechaListoParaRecoger(LocalDateTime.now());
            pedidoRepository.save(pedido);

            // 4.1: Si es a domicilio, verificar si todas las paradas quedaron listas para habilitar el reloj de expiración
            if (pedido.isDomicilio() && pedido.getCompraId() != null && transportistaService != null) {
                transportistaService.actualizarDisponibilidadEnvioSiListo(pedido.getCompraId());
            }

            // Si es a domicilio y ya hay transportista asignado, notificarle
            if (pedido.isDomicilio() && pedido.getCompraId() != null
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

        // Notificar al Comprador
        if (pedido.getComprador() != null && pedido.getComprador().getId() != null && notificacionService != null) {
            if (estadoDeseado == EstadoPedido.EN_PROCESO) {
                notificacionService.enviar(
                        pedido.getComprador().getId(),
                        "Pedido en Preparación",
                        "El vendedor ha comenzado a preparar los productos de tu pedido #" + pedido.getNumeroFactura() + ".",
                        "INFO"
                );
            } else if (estadoDeseado == EstadoPedido.LISTO_PARA_RECOGER) {
                notificacionService.enviar(
                        pedido.getComprador().getId(),
                        "Pedido Listo para Recoger",
                        "Tu pedido #" + pedido.getNumeroFactura() + " está listo en finca para ser recogido por el transportista.",
                        "SUCCESS"
                );
            }
        }
    }

    /**
     * Ya no aplica: todas las entregas son a domicilio gestionadas por logística.
     */
    @Deprecated
    public void validarCodigoRetiroYEntregar(String pedidoId, String codigoIngresado, String vendedorId) {
        throw new UnsupportedOperationException("La modalidad de retiro en tienda fue deshabilitada. Todas las entregas son gestionadas a domicilio por transportistas.");
    }

    /**
     * Cancela un pedido del vendedor y ajusta stock, billetera, reembolso y ruta de transportista.
     * Delega en la rutina centralizada de PedidoCancelacionService.
     */
    public void cancelarPedido(String pedidoId, String motivo, String vendedorId) {
        if (pedidoId == null || vendedorId == null || vendedorId.isBlank()) {
            throw new IllegalArgumentException("El ID de pedido y el ID de vendedor son obligatorios");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        if (pedido.getVendedor() == null || pedido.getVendedor().getId() == null
                || !vendedorId.equals(pedido.getVendedor().getId())) {
            throw new SecurityException("No tienes permiso para cancelar este pedido");
        }

        if (pedido.getEstadoEnum() == EstadoPedido.ENVIADO || pedido.getEstadoEnum() == EstadoPedido.ENTREGADO) {
            throw new IllegalStateException("No se puede cancelar un pedido que ya fue enviado o entregado.");
        }

        if (pedidoCancelacionService != null) {
            pedidoCancelacionService.cancelarPedido(pedido, motivo, ActorTransicion.VENDEDOR, CanceladoPor.VENDEDOR);
        } else {
            pedido.setCanceladoPor(CanceladoPor.VENDEDOR);
            pedido.setMotivoCancelacion(motivo != null ? motivo : "Cancelación solicitada por el vendedor");
            pedido.setFechaCancelacion(LocalDateTime.now());
            pedidoEstadoService.transicionar(
                    pedido,
                    EstadoPedido.CANCELADO,
                    ActorTransicion.VENDEDOR,
                    motivo != null ? motivo : "Cancelación por vendedor",
                    CanceladoPor.VENDEDOR
            );

            if (transportistaService != null) {
                transportistaService.removerParadaPorPedidoCancelado(pedidoId);
            }

            if (pedido.getComprador() != null && pedido.getComprador().getId() != null && notificacionService != null) {
                notificacionService.enviar(
                        pedido.getComprador().getId(),
                        "Pedido Cancelado",
                        "Tu pedido #" + pedido.getNumeroFactura() + " ha sido cancelado por el vendedor. Motivo: "
                                + (motivo != null ? motivo : "Cancelación registrada"),
                        "WARNING"
                );
            }
        }
    }

    /**
     * 2.3: Calcula el total de ventas del vendedor basándose en subtotalCentavos (productos sin envío).
     * Abarca únicamente estados CONFIRMADO..ENTREGADO, excluyendo EN_REVISION y CANCELADO.
     */
    public double calcularTotalVentas(String vendedorId) {
        return obtenerVentasDelVendedor(vendedorId).stream()
                .filter(p -> {
                    EstadoPedido ep = p.getEstadoEnum();
                    return ep != null && ep.esVentaConfirmada();
                })
                .mapToDouble(Pedido::getSubtotalProductos)
                .sum();
    }

    /**
     * Calcula el total de ventas ya entregadas al comprador.
     */
    public double calcularTotalVentasEntregadas(String vendedorId) {
        return obtenerVentasDelVendedor(vendedorId).stream()
                .filter(p -> p.getEstadoEnum() == EstadoPedido.ENTREGADO)
                .mapToDouble(Pedido::getSubtotalProductos)
                .sum();
    }

    /**
     * Calcula el total de ventas que están actualmente en curso (CONFIRMADO a ENVIADO).
     */
    public double calcularTotalVentasEnCurso(String vendedorId) {
        return obtenerVentasDelVendedor(vendedorId).stream()
                .filter(p -> {
                    EstadoPedido ep = p.getEstadoEnum();
                    return ep != null && ep.esVentaEnCurso();
                })
                .mapToDouble(Pedido::getSubtotalProductos)
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