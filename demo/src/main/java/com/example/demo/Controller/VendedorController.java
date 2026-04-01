package com.example.demo.Controller;

import com.example.demo.Model.Pedido;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.VendedorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/vendedor")
public class VendedorController {

    @Autowired
    private VendedorService vendedorService;

    @Autowired
    private UsuarioRepository usuarioRepository;

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
        return "registro-vendedor";
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
                    banco);

            // Actualizar sesión con usuario que ya tiene el perfil embebido
            session.setAttribute("usuarioLogueado", usuarioActualizado);

            redirectAttributes.addFlashAttribute("mensaje",
                    "¡Felicidades! Ahora eres vendedor en Cultivus");
            return "redirect:/vendedor/inicio";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/registro";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al crear el perfil de vendedor");
            return "redirect:/vendedor/registro";
        }
    }

    /**
     * Página de inicio para vendedores
     */
    @GetMapping("/inicio")
    public String mostrarInicioVendedor(HttpSession session, Model model,
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
        return "inicio-vendedor";
    }

    /**
     * Dashboard del vendedor
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

        // El perfil ya viene embebido en el usuario
        model.addAttribute("perfilVendedor", usuario.getPerfilVendedor());
        model.addAttribute("usuario", usuario);
        return "Miproductos";
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
        return "perfil-vendedor";
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
        return "editar-perfil-vendedor";
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
                    banco);

            session.setAttribute("usuarioLogueado", usuarioActualizado);

            redirectAttributes.addFlashAttribute("mensaje", "Perfil actualizado exitosamente");
            return "redirect:/vendedor/perfil";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el perfil");
            return "redirect:/vendedor/editar";
        }
    }

    /**
     * Ventas del vendedor
     */
    @GetMapping("/ventas")
    public String mostrarVentas(HttpSession session, Model model,
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

        List<Pedido> pedidos = vendedorService.obtenerPedidosDelVendedor(usuario.getId());
        double totalVentas = vendedorService.calcularTotalVentas(usuario.getId());

        long pendientes = pedidos.stream()
                .filter(p -> "PENDIENTE".equalsIgnoreCase(p.getEstado())).count();
        long enProceso = pedidos.stream()
                .filter(p -> "ENVIADO".equalsIgnoreCase(p.getEstado())).count();
        long completados = pedidos.stream()
                .filter(p -> "ENTREGADO".equalsIgnoreCase(p.getEstado())).count();

        model.addAttribute("usuario", usuario);
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("totalVentas", totalVentas);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("enProceso", enProceso);
        model.addAttribute("completados", completados);

        return "ventas-vendedor";
    }

    /**
     * Detalle de un pedido específico
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

        // El pedido completo tiene los items embebidos
        Pedido pedido = vendedorService.obtenerPedidosDelVendedor(usuario.getId())
                .stream()
                .filter(p -> p.getId().equals(pedidoId))
                .findFirst()
                .orElse(null);

        if (pedido == null) {
            redirectAttributes.addFlashAttribute("error", "No tienes productos en este pedido");
            return "redirect:/vendedor/ventas";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("pedido", pedido);
        model.addAttribute("items", pedido.getItems());
        return "detalle-pedido-vendedor";
    }

    /**
     * Actualiza el estado de un pedido
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

            vendedorService.actualizarEstadoPedido(pedidoId, nuevoEstado);

            redirectAttributes.addFlashAttribute("mensaje", "Estado actualizado exitosamente");
            return "redirect:/vendedor/ventas/pedido/" + pedidoId;

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/ventas";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el estado");
            return "redirect:/vendedor/ventas";
        }
    }
}