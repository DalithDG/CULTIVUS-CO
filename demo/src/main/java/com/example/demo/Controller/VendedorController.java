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

    @Autowired(required = false)
    private com.example.demo.repository.EnvioRepository envioRepository;

    @Autowired(required = false)
    private com.example.demo.services.UbicacionService ubicacionService;

    @Autowired(required = false)
    private com.example.demo.services.RetiroService retiroService;

    /**
     * US-22 / US-24: saldos por estado, retiros y movimientos de la billetera del vendedor.
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

        if (usuarioRepository != null && usuario.getId() != null) {
            usuario = usuarioRepository.findById(usuario.getId()).orElse(usuario);
            session.setAttribute("usuarioLogueado", usuario);
        }

        Billetera billetera = billeteraService != null
                ? billeteraService.obtenerOCrearBilleteraVendedor(usuario.getId())
                : null;
        PerfilVendedor perfil = usuario.getPerfilVendedor();
        com.example.demo.Model.embebidos.CuentaBancaria cuentaRetiro = perfil != null ? perfil.getCuentaRetiro() : null;

        long retiroMinimoCop = com.example.demo.services.RetiroService.RETIRO_MINIMO_DEFECTO_COP;
        boolean tieneRetiroPendiente = false;
        java.util.List<com.example.demo.Model.SolicitudRetiro> solicitudesRetiro = java.util.List.of();

        if (retiroService != null) {
            try {
                retiroMinimoCop = retiroService.obtenerRetiroMinimoCop();
                tieneRetiroPendiente = retiroService.existeSolicitudPendiente(usuario.getId(), Billetera.Rol.VENDEDOR);
                solicitudesRetiro = retiroService.listarPorUsuarioYRol(usuario.getId(), Billetera.Rol.VENDEDOR);
            } catch (Exception ignored) {
                // En entornos de prueba sin Mongo de retiros activo se usan valores por defecto
            }
        }

        long saldoDisponibleCentavos = billetera != null ? billetera.getSaldoDisponibleCentavos() : 0L;
        long saldoDisponiblePesos = saldoDisponibleCentavos / 100L;
        boolean alcanzaMinimoRetiro = saldoDisponibleCentavos >= (retiroMinimoCop * 100L);
        boolean puedeSolicitarRetiro = cuentaRetiro != null
                && cuentaRetiro.esCompleta()
                && !tieneRetiroPendiente
                && alcanzaMinimoRetiro;

        model.addAttribute("usuario", usuario);
        model.addAttribute("billetera", billetera);
        model.addAttribute("movimientos", billeteraService != null
                ? billeteraService.listarMovimientosVendedor(usuario.getId())
                : java.util.List.of());
        model.addAttribute("cuentaRetiro", cuentaRetiro);
        model.addAttribute("retiroMinimoCop", retiroMinimoCop);
        model.addAttribute("tieneRetiroPendiente", tieneRetiroPendiente);
        model.addAttribute("solicitudesRetiro", solicitudesRetiro);
        model.addAttribute("saldoDisponiblePesos", saldoDisponiblePesos);
        model.addAttribute("alcanzaMinimoRetiro", alcanzaMinimoRetiro);
        model.addAttribute("puedeSolicitarRetiro", puedeSolicitarRetiro);
        return "vendedor/billetera-vendedor";
    }

    /**
     * US-24 / Fase 5: Solicita un retiro simulado desde el saldo disponible del vendedor.
     */
    @PostMapping("/retiros/solicitar")
    public String solicitarRetiro(
            @RequestParam(value = "montoPesos", required = false) String montoPesosTexto,
            @RequestParam(value = "retirarTodo", defaultValue = "false") boolean retirarTodo,
            HttpSession session,
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

        if (retiroService == null) {
            redirectAttributes.addFlashAttribute("error", "El servicio de retiros no está disponible en este momento");
            return "redirect:/vendedor/billetera";
        }

        try {
            com.example.demo.Model.SolicitudRetiro solicitud = retiroService.solicitarRetiro(
                    usuario.getId(),
                    Billetera.Rol.VENDEDOR,
                    montoPesosTexto,
                    retirarTodo);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Solicitud de retiro registrada por $"
                            + String.format(java.util.Locale.GERMANY, "%,d", solicitud.getMontoPesos())
                            + " COP hacia " + solicitud.getCuentaDestino().getResumenEnmascarado()
                            + ". Queda pendiente de aprobación del administrador.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo procesar tu solicitud de retiro: " + e.getMessage());
        }

        return "redirect:/vendedor/billetera";
    }

    /**
     * US-24 / Fase 5 y 7: Muestra el comprobante imprimible de un retiro aprobado del vendedor.
     */
    @GetMapping("/retiros/{id}/comprobante")
    public String verComprobanteRetiro(
            @PathVariable("id") String solicitudId,
            HttpSession session,
            Model model,
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

        try {
            com.example.demo.Model.SolicitudRetiro solicitud = retiroService.obtenerComprobanteParaUsuario(
                    solicitudId, usuario.getId(), Billetera.Rol.VENDEDOR);
            model.addAttribute("solicitud", solicitud);
            model.addAttribute("volverUrl", "/vendedor/billetera");
            return "comprobante-retiro";
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/billetera";
        }
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

            // US-24: La cuenta bancaria no se pide ni valida en el registro (los parámetros opcionales se ignoran)
            Usuario usuarioActualizado = vendedorService.crearPerfilVendedor(
                    usuario.getId(),
                    razonSocial,
                    telefonoContacto,
                    direccionNegocio,
                    descripcionNegocio,
                    null,
                    null,
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
        long listosParaRecoger = contarEstados(ventas, Pedido.LISTO_PARA_RECOGER);
        long enCamino = contarEstados(ventas, "ENVIADO");
        long entregados = contarEstados(ventas, "ENTREGADO", "COMPLETADO");
        long enRevision = contarEstados(ventas, Pedido.EN_REVISION);
        long cancelados = contarEstados(ventas, "CANCELADO");
        List<Pedido> pedidosAtencion = ventas.stream()
                .filter(p -> {
                    String estado = p.getEstado() == null ? "" : p.getEstado().trim().toUpperCase();
                    return "CONFIRMADO".equals(estado)
                            || "EN_PROCESO".equals(estado)
                            || "PROCESANDO".equals(estado)
                            || Pedido.LISTO_PARA_RECOGER.equals(estado);
                })
                .limit(4)
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
        model.addAttribute("listosParaRecoger", listosParaRecoger);
        model.addAttribute("enCamino", enCamino);
        model.addAttribute("entregados", entregados);
        model.addAttribute("enRevision", enRevision);
        model.addAttribute("cancelados", cancelados);
        model.addAttribute("pedidosAtencion", pedidosAtencion);
        model.addAttribute("tareasPendientes", porPreparar + enProceso + listosParaRecoger);
        model.addAttribute("ofertasActivas", ofertasActivas);
        model.addAttribute("stockBajo", stockBajo);
        model.addAttribute("agotadas", agotadas);
        model.addAttribute("ultimosPedidos", ventas.stream().limit(5).toList());
        model.addAttribute("billetera", billetera);
        model.addAttribute("totalPedidosComprador", pedidoRepository.countByCompradorId(usuario.getId()));

        return "vendedor/dashboard-vendedor";
    }

    /**
     * Perfil del vendedor (vista integral con datos de tienda + cuenta personal)
     */
    @GetMapping("/perfil")
    public String mostrarPerfil(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }

        if (usuarioRepository != null && usuario.getId() != null) {
            usuario = usuarioRepository.findById(usuario.getId()).orElse(usuario);
            session.setAttribute("usuarioLogueado", usuario);
        }

        PerfilVendedor perfil = usuario.getPerfilVendedor();
        if (perfil == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes un perfil de vendedor");
            return "redirect:/vendedor/registro";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("perfilVendedor", perfil);
        model.addAttribute("departamentos", ubicacionService != null ? ubicacionService.obtenerTodos() : java.util.List.of());
        return "vendedor/perfil-vendedor";
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
     * US-24 / Fase 2: Pantalla "Mi cuenta bancaria" del vendedor.
     * Muestra la cuenta actual enmascarada y preselecciona el banco desde el texto legado si solo hay ese dato.
     */
    @GetMapping("/cuenta-bancaria")
    public String mostrarCuentaBancaria(HttpSession session, Model model,
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

        if (usuarioRepository != null && usuario.getId() != null) {
            usuario = usuarioRepository.findById(usuario.getId()).orElse(usuario);
            session.setAttribute("usuarioLogueado", usuario);
        }

        PerfilVendedor perfil = usuario.getPerfilVendedor();
        com.example.demo.Model.embebidos.CuentaBancaria cuentaActual = perfil != null ? perfil.getCuentaRetiro() : null;

        model.addAttribute("usuario", usuario);
        model.addAttribute("bancos", com.example.demo.Model.Banco.values());
        model.addAttribute("tiposCuenta", com.example.demo.Model.TipoCuenta.values());
        model.addAttribute("tiposDocumento", com.example.demo.Model.TipoDocumento.values());
        model.addAttribute("cuentaActual", cuentaActual);
        model.addAttribute("cuentaLegadaEnmascarada", perfil != null ? perfil.getCuentaBancariaEnmascarada() : null);
        model.addAttribute("bancoLegadoTexto", perfil != null ? perfil.getBanco() : null);

        if (!model.containsAttribute("bancoSeleccionado")) {
            if (cuentaActual != null && cuentaActual.getBanco() != null) {
                model.addAttribute("bancoSeleccionado", cuentaActual.getBanco().name());
                model.addAttribute("bancoOtroNombre", cuentaActual.getBancoOtroNombre());
                model.addAttribute("tipoSeleccionado", cuentaActual.getTipo() != null ? cuentaActual.getTipo().name() : null);
                model.addAttribute("titular", cuentaActual.getTitular());
                model.addAttribute("tipoDocumentoSeleccionado", cuentaActual.getTipoDocumento() != null ? cuentaActual.getTipoDocumento().name() : null);
                model.addAttribute("numeroDocumento", cuentaActual.getNumeroDocumento());
            } else if (perfil != null && perfil.getBanco() != null && !perfil.getBanco().isBlank()) {
                com.example.demo.Model.Banco.ResultadoMapeo mapeo = com.example.demo.Model.Banco.mapearTextoLibre(perfil.getBanco());
                if (mapeo != null) {
                    model.addAttribute("bancoSeleccionado", mapeo.getBanco().name());
                    model.addAttribute("bancoOtroNombre", mapeo.getBancoOtroNombre());
                }
                model.addAttribute("titular", usuario.getNombre());
            } else {
                model.addAttribute("titular", usuario.getNombre());
            }
        }

        return "vendedor/cuenta-bancaria-vendedor";
    }

    /**
     * US-24 / Fase 2: Guarda la cuenta bancaria del vendedor y vuelve a la billetera.
     */
    @PostMapping("/cuenta-bancaria")
    public String guardarCuentaBancaria(
            @RequestParam(value = "banco", required = false) String bancoStr,
            @RequestParam(value = "bancoOtroNombre", required = false) String bancoOtroNombre,
            @RequestParam(value = "tipo", required = false) String tipoStr,
            @RequestParam(value = "numero", required = false) String numero,
            @RequestParam(value = "titular", required = false) String titular,
            @RequestParam(value = "tipoDocumento", required = false) String tipoDocumentoStr,
            @RequestParam(value = "numeroDocumento", required = false) String numeroDocumento,
            HttpSession session,
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

        try {
            com.example.demo.Model.Banco banco = parseBanco(bancoStr);
            com.example.demo.Model.TipoCuenta tipo = parseTipoCuenta(tipoStr);
            com.example.demo.Model.TipoDocumento tipoDoc = parseTipoDocumento(tipoDocumentoStr);

            com.example.demo.Model.embebidos.CuentaBancaria nueva = new com.example.demo.Model.embebidos.CuentaBancaria(
                    banco, bancoOtroNombre, tipo, numero, titular, tipoDoc, numeroDocumento);

            Usuario actualizado = vendedorService.guardarCuentaBancaria(usuario.getId(), nueva);
            session.setAttribute("usuarioLogueado", actualizado);

            redirectAttributes.addFlashAttribute("mensaje",
                    "Cuenta bancaria guardada exitosamente: " + nueva.getResumenEnmascarado());
            return "redirect:/vendedor/billetera";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("bancoSeleccionado", bancoStr);
            redirectAttributes.addFlashAttribute("bancoOtroNombre", bancoOtroNombre);
            redirectAttributes.addFlashAttribute("tipoSeleccionado", tipoStr);
            redirectAttributes.addFlashAttribute("titular", titular);
            redirectAttributes.addFlashAttribute("tipoDocumentoSeleccionado", tipoDocumentoStr);
            redirectAttributes.addFlashAttribute("numeroDocumento", numeroDocumento);
            return "redirect:/vendedor/cuenta-bancaria";
        }
    }

    private com.example.demo.Model.Banco parseBanco(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return com.example.demo.Model.Banco.valueOf(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private com.example.demo.Model.TipoCuenta parseTipoCuenta(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return com.example.demo.Model.TipoCuenta.valueOf(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private com.example.demo.Model.TipoDocumento parseTipoDocumento(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return com.example.demo.Model.TipoDocumento.valueOf(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
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
        long porPreparar = contarEstados(todasLasVentas, "CONFIRMADO");
        long enProceso = contarEstados(todasLasVentas, "EN_PROCESO", "PROCESANDO");
        long listosParaRecoger = contarEstados(todasLasVentas, Pedido.LISTO_PARA_RECOGER);
        long enCamino = contarEstados(todasLasVentas, "ENVIADO");
        long entregados = contarEstados(todasLasVentas, "ENTREGADO", "COMPLETADO");
        long enRevision = contarEstados(todasLasVentas, Pedido.EN_REVISION);
        long cancelados = contarEstados(todasLasVentas, "CANCELADO");

        model.addAttribute("usuario", usuario);
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("totalVentas", vendedorService.calcularTotalVentas(usuario.getId()));
        model.addAttribute("totalVentasCount", todasLasVentas.size());
        model.addAttribute("porPreparar", porPreparar);
        model.addAttribute("enProceso", enProceso);
        model.addAttribute("listosParaRecoger", listosParaRecoger);
        model.addAttribute("enCamino", enCamino);
        model.addAttribute("entregados", entregados);
        model.addAttribute("enRevision", enRevision);
        model.addAttribute("cancelados", cancelados);
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

        String siguienteEstado = vendedorService.obtenerSiguienteEstado(pedido).orElse(null);
        String accionSiguiente = vendedorService.etiquetaAccionSiguiente(pedido);

        com.example.demo.Model.Envio envio = null;
        if (pedido.getCompraId() != null && envioRepository != null) {
            envio = envioRepository.findByCompraId(pedido.getCompraId()).orElse(null);
        }

        com.example.demo.Model.EstadoPedido ep = pedido.getEstadoEnum();
        boolean puedeCancelar = ep == com.example.demo.Model.EstadoPedido.CONFIRMADO
                || ep == com.example.demo.Model.EstadoPedido.EN_PROCESO
                || ep == com.example.demo.Model.EstadoPedido.LISTO_PARA_RECOGER;

        model.addAttribute("usuario", usuario);
        model.addAttribute("pedido", pedido);
        model.addAttribute("detalles", pedido.getItems());
        model.addAttribute("compradorEmail", compradorEmail);
        model.addAttribute("envio", envio);
        model.addAttribute("siguienteEstado", siguienteEstado);
        model.addAttribute("accionSiguiente", accionSiguiente);
        model.addAttribute("puedeActualizar", siguienteEstado != null);
        model.addAttribute("puedeCancelar", puedeCancelar);
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

    /**
     * 3.1: Cancela un pedido del vendedor requiriendo motivo obligatorio.
     */
    @PostMapping("/ventas/pedido/{pedidoId}/cancelar")
    public String cancelarPedido(
            @PathVariable String pedidoId,
            @RequestParam("motivo") String motivo,
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

            if (motivo == null || motivo.trim().isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Debe ingresar un motivo para cancelar el pedido");
                return "redirect:/vendedor/ventas/pedido/" + pedidoId;
            }

            vendedorService.cancelarPedido(pedidoId, motivo.trim(), usuario.getId());
            redirectAttributes.addFlashAttribute("mensaje", "El pedido ha sido cancelado exitosamente.");
            return "redirect:/vendedor/ventas/pedido/" + pedidoId;

        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/ventas/pedido/" + pedidoId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al cancelar el pedido: " + e.getMessage());
            return "redirect:/vendedor/ventas/pedido/" + pedidoId;
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
            case "proceso" -> "EN_PROCESO".equals(estado) || "PROCESANDO".equals(estado);
            case "listo", "listos" -> Pedido.LISTO_PARA_RECOGER.equals(estado);
            case "enviado" -> "ENVIADO".equals(estado);
            case "entregado" -> "ENTREGADO".equals(estado) || "COMPLETADO".equals(estado);
            case "revision" -> Pedido.EN_REVISION.equals(estado);
            case "cancelado", "cancelados" -> "CANCELADO".equals(estado);
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
