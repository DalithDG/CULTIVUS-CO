package com.example.demo.Controller;

import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.Billetera;
import com.example.demo.Model.Envio;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.services.TransportistaService;
import com.example.demo.services.UbicacionService;
import com.example.demo.services.ZonasLogistica;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/transportista")
public class TransportistaController {

    @Autowired
    private TransportistaService transportistaService;

    @Autowired
    private UbicacionService ubicacionService;

    @Autowired
    private com.example.demo.repository.UsuarioRepository usuarioRepository;

    @Autowired
    private com.example.demo.repository.EnvioRepository envioRepository;

    @Autowired(required = false)
    private com.example.demo.services.BilleteraService billeteraService;

    @Autowired(required = false)
    private com.example.demo.services.RetiroService retiroService;

    /**
     * Pantalla de Login dedicada para transportistas.
     */
    @GetMapping("/login")
    public String mostrarLogin(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.hasRole(Role.TRANSPORTISTA)) {
            return "redirect:/transportista/inicio";
        }
        return "transportista/login-transportista";
    }

    /**
     * Formulario de Registro para transportistas (usuarios nuevos o existentes).
     */
    @GetMapping("/registro")
    public String mostrarRegistro(HttpSession session, Model model) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.hasRole(Role.TRANSPORTISTA)) {
            return "redirect:/transportista/inicio";
        }

        model.addAttribute("usuario", usuario != null ? usuario : new Usuario());
        model.addAttribute("departamentos", ubicacionService.obtenerTodos());
        model.addAttribute("zonasCatalogo", ZonasLogistica.ZONAS);
        return "transportista/registro-transportista";
    }

    /**
     * Endpoint API para la ventana modal "¿No conoces tus zonas?".
     */
    @GetMapping("/api/zonas-municipios")
    @ResponseBody
    public ResponseEntity<?> obtenerCatalogoZonasMunicipios() {
        return ResponseEntity.ok(ZonasLogistica.ZONAS);
    }

    /**
     * Procesa el formulario de registro de transportista (Independiente o Empresa).
     */
    @PostMapping("/guardar")
    public String guardarTransportista(
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "contrasena", required = false) String contrasena,
            @RequestParam(value = "tipo", defaultValue = "INDEPENDIENTE") String tipo,
            @RequestParam(value = "nit", required = false) String nit,
            @RequestParam(value = "razonSocial", required = false) String razonSocial,
            @RequestParam(value = "tipoVehiculo", required = false) String tipoVehiculo,
            @RequestParam(value = "placa", required = false) String placa,
            @RequestParam(value = "capacidadCargaKg", required = false, defaultValue = "1000") Double capacidadCargaKg,
            @RequestParam(value = "licenciaConduccion", required = false) String licenciaConduccion,
            @RequestParam("telefonoContacto") String telefonoContacto,
            @RequestParam(value = "ciudadBase", required = false) String ciudadBase,
            @RequestParam(value = "departamentoBase", required = false) String departamentoBase,
            @RequestParam(value = "zonasCoberturaIds", required = false) List<Integer> zonasCoberturaIds,
            @RequestParam(value = "todasLasZonas", defaultValue = "false") boolean todasLasZonas,
            @RequestParam(value = "refrigerado", defaultValue = "false") boolean refrigerado,
            @RequestParam(value = "empresa", required = false) String empresa,
            @RequestParam(value = "banco", required = false) String banco,
            @RequestParam(value = "cuentaBancaria", required = false) String cuentaBancaria,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            boolean esEmpresa = PerfilTransportista.TIPO_EMPRESA.equalsIgnoreCase(tipo);

            // Validaciones según el tipo
            if (esEmpresa) {
                if (nit == null || nit.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "El NIT es obligatorio para empresas");
                    return "redirect:/transportista/registro";
                }
                if (razonSocial == null || razonSocial.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "La razón social es obligatoria para empresas");
                    return "redirect:/transportista/registro";
                }
            } else {
                if (placa == null || placa.trim().length() < 5) {
                    redirectAttributes.addFlashAttribute("error", "La placa del vehículo es obligatoria para independientes (ej. ABC-123)");
                    return "redirect:/transportista/registro";
                }
                if (licenciaConduccion == null || licenciaConduccion.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "La licencia de conducción es obligatoria para independientes");
                    return "redirect:/transportista/registro";
                }
            }

            if (!todasLasZonas && (zonasCoberturaIds == null || zonasCoberturaIds.isEmpty())) {
                redirectAttributes.addFlashAttribute("error", "Debes seleccionar al menos una zona de cobertura");
                return "redirect:/transportista/registro";
            }

            if (telefonoContacto == null || telefonoContacto.trim().length() < 7) {
                redirectAttributes.addFlashAttribute("error", "El teléfono de contacto es obligatorio y debe tener al menos 7 dígitos");
                return "redirect:/transportista/registro";
            }

            Usuario usuarioSesion = (Usuario) session.getAttribute("usuarioLogueado");

            if (usuarioSesion != null) {
                // Usuario autenticado que solicita convertirse en transportista
                Usuario actualizado = transportistaService.crearPerfilTransportista(
                        usuarioSesion.getId(),
                        tipo,
                        nit,
                        razonSocial,
                        tipoVehiculo,
                        placa,
                        capacidadCargaKg,
                        licenciaConduccion,
                        telefonoContacto,
                        ciudadBase,
                        departamentoBase,
                        zonasCoberturaIds,
                        todasLasZonas,
                        refrigerado,
                        empresa,
                        null,
                        null
                );
                session.setAttribute("usuarioLogueado", actualizado);
                redirectAttributes.addFlashAttribute("mensaje", "¡Bienvenido a la red logística de Cultivus! Tu solicitud fue registrada. Tu perfil está pendiente de verificación por el administrador.");
                return "redirect:/transportista/inicio";
            } else {
                // Registro de nuevo usuario directamente como transportista
                if (nombre == null || nombre.trim().isEmpty() || email == null || email.trim().isEmpty() || contrasena == null || contrasena.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Nombre, correo y contraseña son obligatorios");
                    return "redirect:/transportista/registro";
                }

                transportistaService.registrarNuevoTransportista(
                        nombre,
                        email,
                        contrasena,
                        tipo,
                        nit,
                        razonSocial,
                        tipoVehiculo,
                        placa,
                        capacidadCargaKg,
                        licenciaConduccion,
                        telefonoContacto,
                        ciudadBase,
                        departamentoBase,
                        zonasCoberturaIds,
                        todasLasZonas,
                        refrigerado,
                        empresa,
                        null,
                        null
                );

                redirectAttributes.addFlashAttribute("mensaje", "Registro exitoso. Inicia sesión con tus credenciales.");
                return "redirect:/transportista/login";
            }

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/transportista/registro";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error inesperado al registrar transportista: " + e.getMessage());
            return "redirect:/transportista/registro";
        }
    }

    /**
     * Dashboard / Vista MAIN del Transportista.
     * Muestra las "Rutas disponibles" ordenadas por zona y "Mis envíos".
     */
    @GetMapping({"/inicio", "/dashboard", "/main"})
    public String mostrarDashboard(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Inicia sesión para ingresar al portal logístico");
            return "redirect:/transportista/login";
        }

        // Recargar datos actualizados de la BD para reflejar verificación del admin en tiempo real
        Usuario usuarioActualizado = usuarioRepository.findById(usuario.getId()).orElse(usuario);
        usuario = usuarioActualizado;
        session.setAttribute("usuarioLogueado", usuario);

        if (!usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes tener perfil de transportista para acceder");
            return "redirect:/transportista/registro";
        }

        PerfilTransportista perfil = usuario.getPerfilTransportista();
        List<TransportistaService.RutaDisponibleDTO> rutasDisponibles = transportistaService.obtenerRutasDisponibles(usuario.getId());
        List<TransportistaService.MiEnvioDetalleDTO> todosMisEnvios = transportistaService.obtenerMisEnviosDetallados(usuario.getId());

        List<TransportistaService.MiEnvioDetalleDTO> enviosActivos = todosMisEnvios.stream()
                .filter(me -> me.getEnvio() != null
                        && !Envio.ENTREGADO.equalsIgnoreCase(me.getEnvio().getEstado())
                        && !Envio.CANCELADO.equalsIgnoreCase(me.getEnvio().getEstado()))
                .collect(java.util.stream.Collectors.toList());

        List<TransportistaService.MiEnvioDetalleDTO> historialEnvios = todosMisEnvios.stream()
                .filter(me -> me.getEnvio() != null && Envio.ENTREGADO.equalsIgnoreCase(me.getEnvio().getEstado()))
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("usuario", usuario);
        model.addAttribute("perfil", perfil);
        model.addAttribute("rutas", rutasDisponibles);
        model.addAttribute("totalRutasDisponibles", rutasDisponibles.size());
        model.addAttribute("misEnvios", enviosActivos);
        model.addAttribute("totalMisEnvios", enviosActivos.size());
        model.addAttribute("historialEnvios", historialEnvios);
        model.addAttribute("totalHistorialEnvios", historialEnvios.size());

        return "transportista/inicio-transportista";
    }

    /**
     * Billetera del transportista con saldos, cuenta bancaria, solicitud de retiro e historial.
     */
    @GetMapping("/billetera")
    public String mostrarBilletera(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Inicia sesión para ingresar al portal logístico");
            return "redirect:/transportista/login";
        }
        if (!usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permisos de transportista");
            return "redirect:/transportista/login";
        }

        if (usuarioRepository != null && usuario.getId() != null) {
            usuario = usuarioRepository.findById(usuario.getId()).orElse(usuario);
            session.setAttribute("usuarioLogueado", usuario);
        }

        Billetera billetera = null;
        java.util.List<com.example.demo.Model.MovimientoBilletera> movimientos = java.util.Collections.emptyList();
        if (billeteraService != null) {
            billetera = billeteraService.obtenerOCrearBilleteraTransportista(usuario.getId());
            movimientos = billeteraService.listarMovimientosTransportista(usuario.getId());
        }

        // Fletes en ruta (informativo): suma del costoTotalCentavos de envíos asignados no entregados
        long fletesEnRuta = 0L;
        if (envioRepository != null) {
            for (Envio e : envioRepository.findByTransportistaId(usuario.getId())) {
                if (!Envio.ENTREGADO.equalsIgnoreCase(e.getEstado()) && !Envio.CANCELADO.equalsIgnoreCase(e.getEstado())) {
                    fletesEnRuta += (e.getCostoTotalCentavos() != null ? e.getCostoTotalCentavos() : 0L);
                }
            }
        }

        PerfilTransportista perfil = usuario.getPerfilTransportista();
        com.example.demo.Model.embebidos.CuentaBancaria cuentaRetiro = perfil != null ? perfil.getCuentaRetiro() : null;

        long retiroMinimoCop = com.example.demo.services.RetiroService.RETIRO_MINIMO_DEFECTO_COP;
        boolean tieneRetiroPendiente = false;
        java.util.List<com.example.demo.Model.SolicitudRetiro> solicitudesRetiro = java.util.List.of();

        if (retiroService != null) {
            try {
                retiroMinimoCop = retiroService.obtenerRetiroMinimoCop();
                tieneRetiroPendiente = retiroService.existeSolicitudPendiente(usuario.getId(), Billetera.Rol.TRANSPORTISTA);
                solicitudesRetiro = retiroService.listarPorUsuarioYRol(usuario.getId(), Billetera.Rol.TRANSPORTISTA);
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
        model.addAttribute("movimientos", movimientos);
        model.addAttribute("fletesEnRuta", fletesEnRuta);
        model.addAttribute("cuentaRetiro", cuentaRetiro);
        model.addAttribute("retiroMinimoCop", retiroMinimoCop);
        model.addAttribute("tieneRetiroPendiente", tieneRetiroPendiente);
        model.addAttribute("solicitudesRetiro", solicitudesRetiro);
        model.addAttribute("saldoDisponiblePesos", saldoDisponiblePesos);
        model.addAttribute("alcanzaMinimoRetiro", alcanzaMinimoRetiro);
        model.addAttribute("puedeSolicitarRetiro", puedeSolicitarRetiro);
        return "transportista/billetera-transportista";
    }

    /**
     * US-24 / Fase 5: Solicita un retiro simulado desde el saldo disponible del transportista.
     */
    @PostMapping("/retiros/solicitar")
    public String solicitarRetiro(
            @RequestParam(value = "montoPesos", required = false) String montoPesosTexto,
            @RequestParam(value = "retirarTodo", defaultValue = "false") boolean retirarTodo,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        if (retiroService == null) {
            redirectAttributes.addFlashAttribute("error", "El servicio de retiros no está disponible en este momento");
            return "redirect:/transportista/billetera";
        }

        try {
            com.example.demo.Model.SolicitudRetiro solicitud = retiroService.solicitarRetiro(
                    usuario.getId(),
                    Billetera.Rol.TRANSPORTISTA,
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

        return "redirect:/transportista/billetera";
    }

    /**
     * US-24 / Fase 5 y 7: Muestra el comprobante imprimible de un retiro aprobado del transportista.
     */
    @GetMapping("/retiros/{id}/comprobante")
    public String verComprobanteRetiro(
            @PathVariable("id") String solicitudId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        try {
            com.example.demo.Model.SolicitudRetiro solicitud = retiroService.obtenerComprobanteParaUsuario(
                    solicitudId, usuario.getId(), Billetera.Rol.TRANSPORTISTA);
            model.addAttribute("solicitud", solicitud);
            model.addAttribute("volverUrl", "/transportista/billetera");
            return "comprobante-retiro";
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/transportista/billetera";
        }
    }

    /**
     * Aceptar compra / ruta disponible.
     * Utiliza findAndModify con control de concurrencia.
     */
    @PostMapping("/aceptar/{envioId}")
    public String aceptarEnvio(@PathVariable String envioId, HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        try {
            transportistaService.aceptarEnvio(envioId, usuario.getId());
            redirectAttributes.addFlashAttribute("mensaje", "¡Compra aceptada exitosamente! Ya se encuentra en 'Mis Envíos'. Se ha notificado al comprador y a los vendedores.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al aceptar la compra: " + e.getMessage());
        }

        return "redirect:/transportista/inicio";
    }

    /**
     * Liberar ruta asignada.
     * Solo el dueño y si ninguna parada fue recogida.
     */
    @PostMapping("/liberar/{envioId}")
    public String liberarEnvio(@PathVariable String envioId, HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        try {
            transportistaService.liberarEnvio(envioId, usuario.getId());
            redirectAttributes.addFlashAttribute("mensaje", "El envío ha sido liberado exitosamente y vuelve a estar disponible en Rutas Disponibles.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/transportista/inicio";
    }

    /**
     * Alternar estado de disponibilidad (Disponible / En ruta).
     */
    @PostMapping("/disponibilidad")
    public String alternarDisponibilidad(
            @RequestParam("disponible") boolean disponible,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.hasRole(Role.TRANSPORTISTA)) {
            Usuario actualizado = transportistaService.actualizarDisponibilidad(usuario.getId(), disponible);
            session.setAttribute("usuarioLogueado", actualizado);
            redirectAttributes.addFlashAttribute("mensaje", disponible ? "Ahora estás DISPONIBLE para recibir asignación de envíos." : "Estado cambiado a NO DISPONIBLE.");
        }
        return "redirect:/transportista/inicio";
    }

    /**
     * Marcar una parada individual como recogida por el transportista.
     * Requiere que el sub-pedido esté en LISTO_PARA_RECOGER.
     */
    @PostMapping("/parada/{envioId}/{pedidoId}/recoger")
    public String marcarParadaRecogida(
            @PathVariable String envioId,
            @PathVariable String pedidoId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        try {
            transportistaService.marcarParadaRecogida(envioId, pedidoId, usuario.getId());
            redirectAttributes.addFlashAttribute("mensaje", "¡Parada marcada como recogida! El pedido ha pasado a estado ENVIADO y el comprador ha sido notificado.");
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar la recogida: " + e.getMessage());
        }

        return "redirect:/transportista/inicio";
    }

    /**
     * Marcar entrega final al comprador.
     * Requiere que TODAS las paradas de recogida hayan sido completadas.
     */
    @PostMapping("/envio/{envioId}/entregar")
    public String marcarEnvioEntregado(
            @PathVariable String envioId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        try {
            transportistaService.marcarEnvioEntregado(envioId, usuario.getId());
            redirectAttributes.addFlashAttribute("mensaje", "¡Excelente trabajo! Has completado la entrega de la compra. Todos los pedidos han sido marcados como ENTREGADOS.");
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar la entrega: " + e.getMessage());
        }

        return "redirect:/transportista/inicio";
    }

    /**
     * 4.5: Reportar incidencia en una parada.
     */
    @PostMapping("/parada/{envioId}/{pedidoId}/incidencia")
    public String reportarProblemaParada(
            @PathVariable String envioId,
            @PathVariable String pedidoId,
            @RequestParam("motivo") String motivo,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        try {
            transportistaService.reportarProblemaParada(envioId, pedidoId, motivo, usuario.getId());
            redirectAttributes.addFlashAttribute("mensaje", "Incidencia registrada. Se ha notificado al vendedor y al equipo administrativo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/transportista/inicio";
    }

    /**
     * Vista de perfil dedicada del Transportista.
     */
    @GetMapping("/perfil")
    public String mostrarPerfilTransportista(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }
        if (usuarioRepository != null && usuario.getId() != null) {
            usuario = usuarioRepository.findById(usuario.getId()).orElse(usuario);
            session.setAttribute("usuarioLogueado", usuario);
        }
        model.addAttribute("usuario", usuario);
        model.addAttribute("departamentos", ubicacionService != null ? ubicacionService.obtenerTodos() : java.util.List.of());
        return "transportista/perfil-transportista";
    }

    /**
     * US-24 / Fase 2: Pantalla "Mi cuenta bancaria" del transportista.
     */
    @GetMapping("/cuenta-bancaria")
    public String mostrarCuentaBancaria(HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        if (usuarioRepository != null && usuario.getId() != null) {
            usuario = usuarioRepository.findById(usuario.getId()).orElse(usuario);
            session.setAttribute("usuarioLogueado", usuario);
        }

        PerfilTransportista perfil = usuario.getPerfilTransportista();
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

        return "transportista/cuenta-bancaria-transportista";
    }

    /**
     * US-24 / Fase 2: Guarda la cuenta bancaria del transportista y vuelve a la billetera.
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
        if (usuario == null || !usuario.hasRole(Role.TRANSPORTISTA)) {
            redirectAttributes.addFlashAttribute("error", "Debes iniciar sesión como transportista");
            return "redirect:/transportista/login";
        }

        try {
            com.example.demo.Model.Banco banco = parseBanco(bancoStr);
            com.example.demo.Model.TipoCuenta tipo = parseTipoCuenta(tipoStr);
            com.example.demo.Model.TipoDocumento tipoDoc = parseTipoDocumento(tipoDocumentoStr);

            com.example.demo.Model.embebidos.CuentaBancaria nueva = new com.example.demo.Model.embebidos.CuentaBancaria(
                    banco, bancoOtroNombre, tipo, numero, titular, tipoDoc, numeroDocumento);

            Usuario actualizado = transportistaService.guardarCuentaBancaria(usuario.getId(), nueva);
            session.setAttribute("usuarioLogueado", actualizado);

            redirectAttributes.addFlashAttribute("mensaje",
                    "Cuenta bancaria guardada exitosamente: " + nueva.getResumenEnmascarado());
            return "redirect:/transportista/billetera";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("bancoSeleccionado", bancoStr);
            redirectAttributes.addFlashAttribute("bancoOtroNombre", bancoOtroNombre);
            redirectAttributes.addFlashAttribute("tipoSeleccionado", tipoStr);
            redirectAttributes.addFlashAttribute("titular", titular);
            redirectAttributes.addFlashAttribute("tipoDocumentoSeleccionado", tipoDocumentoStr);
            redirectAttributes.addFlashAttribute("numeroDocumento", numeroDocumento);
            return "redirect:/transportista/cuenta-bancaria";
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
}
