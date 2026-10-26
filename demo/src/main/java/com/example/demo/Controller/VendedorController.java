package com.example.demo.Controller;

import com.example.demo.Model.Billetera;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.BilleteraService;
import com.example.demo.services.OfertaService;
import com.example.demo.services.VendedorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/vendedor")
public class VendedorController {

    @Autowired
    private VendedorService vendedorService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private OfertaService ofertaService;

    @Autowired(required = false)
    private BilleteraService billeteraService;

    /**
     * US-22: saldos por estado y movimientos de la billetera del vendedor.
     * Solo lectura: no hay endpoints para editar ni borrar movimientos.
     */
    @GetMapping("/billetera")
    public String mostrarBilletera(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }
        if (!vendedorService.esVendedor(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error", "Debe registrarse como vendedor primero");
            return "redirect:/vendedor/registro";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("billetera", billeteraService != null
                ? billeteraService.obtenerOCrearBilleteraVendedor(usuario.getId())
                : null);
        model.addAttribute("movimientos", billeteraService != null
                ? billeteraService.listarMovimientosVendedor(usuario.getId())
                : java.util.List.of());
        return "vendedor/billetera-vendedor";
    }

    /**
     * Muestra el formulario para convertirse en vendedor
     */
    @GetMapping("/registro")
    public String mostrarFormularioVendedor(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }

        // Verificar si ya es vendedor
        if (vendedorService.esVendedor(usuario.getId())) {
            redirectAttributes.addFlashAttribute("info", "Ya tienes un perfil de vendedor");
            return "redirect:/vendedor/perfil";
        }

        model.addAttribute("usuario", usuario);
        return "vendedor/registro-vendedor";
    }

    /**
     * Procesa el formulario de registro de vendedor
     */
    @PostMapping("/guardar")
    public String guardarPerfilVendedor(
            @RequestParam("razonSocial") String razonSocial,
            @RequestParam("telefonoContacto") String telefonoContacto,
            @RequestParam("direccionNegocio") String direccionNegocio,
            @RequestParam(value = "descripcionNegocio", required = false) String descripcionNegocio,
            @RequestParam(value = "cuentaBancaria", required = false) String cuentaBancaria,
            @RequestParam(value = "banco", required = false) String banco,
            @RequestParam("tipoProductos") String tipoProductos,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
                return "redirect:/usuario/login";
            }

            // Validaciones
            if (razonSocial == null || razonSocial.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "La razón social es obligatoria");
                return "redirect:/vendedor/registro";
            }
            if (!telefonoContacto.matches("\\d{8,}")) {
                redirectAttributes.addFlashAttribute("error",
                        "El teléfono debe tener al menos 8 dígitos y solo números");
                return "redirect:/vendedor/registro";
            }
            if (direccionNegocio == null || direccionNegocio.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "La dirección del negocio es obligatoria");
                return "redirect:/vendedor/registro";
            }
            if (cuentaBancaria != null && !cuentaBancaria.isBlank()
                    && cuentaBancaria.length() < 8) {
                redirectAttributes.addFlashAttribute("error",
                        "La cuenta bancaria debe tener mínimo 8 dígitos");
                return "redirect:/vendedor/registro";
            }
            if (cuentaBancaria != null && !cuentaBancaria.isBlank()) {
                if (banco == null || banco.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("error",
                            "Debe indicar el banco de la cuenta");
                    return "redirect:/vendedor/registro";
                }
            }

            // Crear perfil — retorna el Usuario actualizado con perfil embebido
            Usuario usuarioActualizado = vendedorService.crearPerfilVendedor(
                    usuario.getId(),
                    razonSocial,
                    telefonoContacto,
                    direccionNegocio,
                    descripcionNegocio,
                    cuentaBancaria,
                    banco,
                    tipoProductos);

            // Actualizar sesión con usuario que ya tiene el perfil embebido
            session.setAttribute("usuarioLogueado", usuarioActualizado);

            // Persistir la Authentication actualizada (con rol VENDEDOR) en la sesión
            session.setAttribute("SPRING_SECURITY_CONTEXT",
                    org.springframework.security.core.context.SecurityContextHolder.getContext());

            redirectAttributes.addFlashAttribute("mensaje",
                    "Tu solicitud ha sido enviada. Tu perfil de vendedor está ahora en proceso de validación por un administrador.");
            return "redirect:/vendedor/dashboard";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/registro";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al crear el perfil de vendedor");
            return "redirect:/vendedor/registro";
        }
    }

    /**
     * Redirige inicio al panel principal del vendedor (Dashboard)
     */
    @GetMapping({ "", "/", "/inicio" })
    public String mostrarInicioVendedor(HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }
        if (!vendedorService.esVendedor(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error", "Debe registrarse como vendedor primero");
            return "redirect:/vendedor/registro";
        }

        return "redirect:/vendedor/dashboard";
    }

    /**
     * Panel principal del vendedor: resumen de ventas, pedidos y estado del
     * inventario.
     */
    @GetMapping("/dashboard")
    public String mostrarDashboard(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }
        if (!vendedorService.esVendedor(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error", "Debe registrarse como vendedor primero");
            return "redirect:/vendedor/registro";
        }

        List<OfertaVendedor> ofertas = ofertaService.listarOfertasVendedor(usuario.getId());
        List<Pedido> ventas = vendedorService.obtenerVentasDelVendedor(usuario.getId());
        Billetera billetera = billeteraService != null
                ? billeteraService.obtenerOCrearBilleteraVendedor(usuario.getId())
                : null;

        long porPreparar = contarEstados(ventas, "CONFIRMADO");
        long enProceso = contarEstados(ventas, "EN_PROCESO", "PROCESANDO");
        long enCamino = contarEstados(ventas, "ENVIADO");
        long entregados = contarEstados(ventas, "ENTREGADO", "COMPLETADO");
        long enRevision = contarEstados(ventas, Pedido.EN_REVISION);
        List<Pedido> pedidosAtencion = ventas.stream()
                .filter(p -> {
                    String estado = p.getEstado() == null ? "" : p.getEstado().trim().toUpperCase();
                    return "CONFIRMADO".equals(estado)
                            || "EN_PROCESO".equals(estado)
                            || "PROCESANDO".equals(estado);
                })
                .limit(3)
                .toList();
        long ofertasActivas = ofertas.stream().filter(OfertaVendedor::isDisponible).count();
        long stockBajo = ofertas.stream()
                .filter(o -> o.getStock() != null && o.getStock() > 0 && o.getStock() <= 5)
                .count();
        long agotadas = ofertas.stream()
                .filter(o -> o.getStock() == null || o.getStock() <= 0)
                .count();

        model.addAttribute("usuario", usuario);
        model.addAttribute("perfilVendedor", usuario.getPerfilVendedor());
        model.addAttribute("ofertas", ofertas);
        model.addAttribute("totalVentas", vendedorService.calcularTotalVentas(usuario.getId()));
        model.addAttribute("totalPedidosVenta", ventas.size());
        model.addAttribute("porPreparar", porPreparar);
        model.addAttribute("enProceso", enProceso);
        model.addAttribute("enCamino", enCamino);
        model.addAttribute("entregados", entregados);
        model.addAttribute("enRevision", enRevision);
        model.addAttribute("pedidosAtencion", pedidosAtencion);
        model.addAttribute("tareasPendientes", porPreparar + enProceso);
        model.addAttribute("ofertasActivas", ofertasActivas);
        model.addAttribute("stockBajo", stockBajo);
        model.addAttribute("agotadas", agotadas);
        model.addAttribute("ultimosPedidos", ventas.stream().limit(5).toList());
        model.addAttribute("billetera", billetera);
        model.addAttribute("totalPedidosComprador", pedidoRepository.countByCompradorId(usuario.getId()));

        return "vendedor/dashboard-vendedor";
    }

    /**
     * Perfil del vendedor
     */
    @GetMapping("/perfil")
    public String mostrarPerfil(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }

        PerfilVendedor perfil = usuario.getPerfilVendedor();
        if (perfil == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes un perfil de vendedor");
            return "redirect:/vendedor/registro";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("perfilVendedor", perfil);
        return "vendedor/editar-perfil-vendedor";
    }

    /**
     * Formulario para editar el perfil de vendedor
     */
    @GetMapping("/editar")
    public String mostrarFormularioEditar(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }

        PerfilVendedor perfil = usuario.getPerfilVendedor();
        if (perfil == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes un perfil de vendedor");
            return "redirect:/vendedor/registro";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("perfilVendedor", perfil);
        return "vendedor/editar-perfil-vendedor";
    }

    /**
     * Actualiza el perfil de vendedor
     */
    @PostMapping("/actualizar")
    public String actualizarPerfil(
            @RequestParam("razonSocial") String razonSocial,
            @RequestParam("telefonoContacto") String telefonoContacto,
            @RequestParam("direccionNegocio") String direccionNegocio,
            @RequestParam(value = "descripcionNegocio", required = false) String descripcionNegocio,
            @RequestParam(value = "cuentaBancaria", required = false) String cuentaBancaria,
            @RequestParam(value = "banco", required = false) String banco,
            @RequestParam(value = "tipoProductos", required = false) String tipoProductos,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
                return "redirect:/usuario/login";
            }

            // Retorna el usuario actualizado con el perfil embebido modificado
            Usuario usuarioActualizado = vendedorService.actualizarPerfil(
                    usuario.getId(),
                    razonSocial,
                    telefonoContacto,
                    direccionNegocio,
                    descripcionNegocio,
                    cuentaBancaria,
                    banco,
                    tipoProductos);

            session.setAttribute("usuarioLogueado", usuarioActualizado);

            redirectAttributes.addFlashAttribute("mensaje", "Perfil actualizado exitosamente");
            return "redirect:/vendedor/perfil";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el perfil");
            return "redirect:/vendedor/editar";
        }
    }

    /**
     * Ventas del vendedor. Solo se muestran pedidos pagados/confirmados.
     */
    @GetMapping("/ventas")
    public String mostrarVentas(
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "q", required = false) String busqueda,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }
        if (!vendedorService.esVendedor(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error", "Debe registrarse como vendedor primero");
            return "redirect:/vendedor/registro";
        }

        // Solo ventas reales (pagadas/confirmadas); se excluyen PENDIENTE y CANCELADO
        List<Pedido> todasLasVentas = vendedorService.obtenerVentasDelVendedor(usuario.getId());

        String filtro = (estado == null || estado.isBlank()) ? "todas" : estado.trim().toLowerCase();
        String texto = (busqueda == null) ? "" : busqueda.trim().toLowerCase();

        List<Pedido> pedidos = todasLasVentas.stream()
                .filter(p -> coincideFiltroEstado(p, filtro))
                .filter(p -> coincideBusqueda(p, texto))
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("usuario", usuario);
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("totalVentas", vendedorService.calcularTotalVentas(usuario.getId()));
        model.addAttribute("totalVentasCount", todasLasVentas.size());
        model.addAttribute("porPreparar", contarEstados(todasLasVentas, "CONFIRMADO"));
        model.addAttribute("enProceso", contarEstados(todasLasVentas, "EN_PROCESO", "PROCESANDO", Pedido.LISTO_PARA_RECOGER));
        model.addAttribute("enCamino", contarEstados(todasLasVentas, "ENVIADO"));
        model.addAttribute("entregados", contarEstados(todasLasVentas, "ENTREGADO", "COMPLETADO"));
        model.addAttribute("enRevision", contarEstados(todasLasVentas, Pedido.EN_REVISION));
        model.addAttribute("filtroActivo", filtro);
        model.addAttribute("busqueda", texto);

        return "vendedor/ventas-vendedor";
    }

    /**
     * Detalle de una venta del vendedor autenticado.
     */
    @GetMapping("/ventas/pedido/{pedidoId}")
    public String mostrarDetallePedido(@PathVariable String pedidoId,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }
        if (!vendedorService.esVendedor(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error", "Debe registrarse como vendedor primero");
            return "redirect:/vendedor/registro";
        }

        Pedido pedido = vendedorService.obtenerVentaDelVendedor(pedidoId, usuario.getId())
                .orElse(null);

        if (pedido == null) {
            redirectAttributes.addFlashAttribute("error",
                    "La venta no existe, no está pagada o no pertenece a tu tienda");
            return "redirect:/vendedor/ventas";
        }

        String compradorEmail = null;
        if (pedido.getComprador() != null && pedido.getComprador().getId() != null) {
            compradorEmail = usuarioRepository.findById(pedido.getComprador().getId())
                    .map(Usuario::getEmail)
                    .orElse(null);
        }

        String siguienteEstado = vendedorService.obtenerSiguienteEstado(pedido.getEstado()).orElse(null);

        model.addAttribute("usuario", usuario);
        model.addAttribute("pedido", pedido);
        model.addAttribute("detalles", pedido.getItems());
        model.addAttribute("compradorEmail", compradorEmail);
        model.addAttribute("siguienteEstado", siguienteEstado);
        model.addAttribute("accionSiguiente", vendedorService.etiquetaAccionSiguiente(pedido.getEstado()));
        model.addAttribute("puedeActualizar", siguienteEstado != null);
        return "vendedor/detalle-pedido-vendedor";
    }

    /**
     * Actualiza el estado de una venta. El servicio valida propiedad y transiciones
     * permitidas.
     */
    @PostMapping("/ventas/pedido/{pedidoId}/actualizar-estado")
    public String actualizarEstadoPedido(@PathVariable String pedidoId,
            @RequestParam("estado") String nuevoEstado,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
                return "redirect:/usuario/login";
            }
            if (!vendedorService.esVendedor(usuario.getId())) {
                redirectAttributes.addFlashAttribute("error", "Debe registrarse como vendedor primero");
                return "redirect:/vendedor/registro";
            }

            vendedorService.actualizarEstadoPedido(pedidoId, nuevoEstado, usuario.getId());

            redirectAttributes.addFlashAttribute("mensaje", "Estado actualizado correctamente");
            return "redirect:/vendedor/ventas/pedido/" + pedidoId;

        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/ventas/pedido/" + pedidoId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el estado");
            return "redirect:/vendedor/ventas";
        }
    }

    private long contarEstados(List<Pedido> pedidos, String... estados) {
        java.util.Set<String> buscados = java.util.Set.of(estados);
        return pedidos.stream()
                .filter(p -> p.getEstado() != null
                        && buscados.contains(p.getEstado().trim().toUpperCase()))
                .count();
    }

    private boolean coincideFiltroEstado(Pedido pedido, String filtro) {
        String estado = pedido.getEstado() == null ? "" : pedido.getEstado().trim().toUpperCase();
        return switch (filtro) {
            case "preparar" -> "CONFIRMADO".equals(estado);
            case "proceso" -> "EN_PROCESO".equals(estado) || "PROCESANDO".equals(estado)
                    || Pedido.LISTO_PARA_RECOGER.equals(estado);
            case "enviado" -> "ENVIADO".equals(estado);
            case "entregado" -> "ENTREGADO".equals(estado) || "COMPLETADO".equals(estado);
            case "revision" -> Pedido.EN_REVISION.equals(estado);
            default -> true;
        };
    }

    private boolean coincideBusqueda(Pedido pedido, String texto) {
        if (texto == null || texto.isBlank()) {
            return true;
        }
        String factura = pedido.getNumeroFactura() == null ? "" : pedido.getNumeroFactura().toLowerCase();
        String cliente = pedido.getComprador() != null && pedido.getComprador().getNombre() != null
                ? pedido.getComprador().getNombre().toLowerCase()
                : "";
        return factura.contains(texto) || cliente.contains(texto)
                || (pedido.getId() != null && pedido.getId().toLowerCase().contains(texto));
    }
}
