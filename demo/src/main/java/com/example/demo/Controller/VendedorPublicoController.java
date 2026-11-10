package com.example.demo.Controller;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.ResenaVendedor;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.ResenaVendedorRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.VendedorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Perfil público del vendedor y reseñas de vendedor.
 * Las rutas se abren en SecurityConfig: GET /vendedor/{id}/perfil es público y
 * POST /vendedor/{id}/resena es solo para compradores.
 */
@Controller
@RequestMapping("/vendedor")
public class VendedorPublicoController {

    private static final int RESENAS_POR_PAGINA = 5;
    private static final int MAX_COMENTARIO = 500;

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private VendedorService vendedorService;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private OfertaRepository ofertaRepository;
    @Autowired
    private ResenaVendedorRepository resenaVendedorRepository;

    @GetMapping("/{id}/perfil")
    public String perfilVendedor(@PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            Model model, HttpSession session) {
        Usuario vendedor = usuarioRepository.findById(id).orElse(null);
        if (vendedor == null || !vendedorService.esVendedor(id)) {
            return "redirect:/";
        }

        // Más recientes primero
        List<ResenaVendedor> todas = resenaVendedorRepository.findByVendedorId(id).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ResenaVendedor::getFecha,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        double promedio = calcularPromedio(todas);

        int total = todas.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / RESENAS_POR_PAGINA));
        int p = Math.max(0, Math.min(page, totalPages - 1));
        List<ResenaVendedor> resenas = todas.stream()
                .skip((long) p * RESENAS_POR_PAGINA).limit(RESENAS_POR_PAGINA).toList();

        // Nombres de los compradores que dejaron reseña en la página actual
        Map<String, String> autoresResenas = new HashMap<>();
        for (ResenaVendedor r : resenas) {
            if (r.getUsuarioId() != null && !autoresResenas.containsKey(r.getUsuarioId())) {
                String nombreAutor = usuarioRepository.findById(r.getUsuarioId())
                        .map(Usuario::getNombre)
                        .filter(n -> n != null && !n.isBlank())
                        .orElse("Comprador verificado");
                autoresResenas.put(r.getUsuarioId(), nombreAutor);
            }
        }

        List<OfertaVendedor> ofertas = ofertaRepository.findByVendedor_IdAndDisponibleTrue(id).stream()
                .filter(o -> o != null && o.estaPublicada() && o.getPrecio() != null)
                .toList();

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        boolean esPropioPerfil = usuario != null && id.equals(usuario.getId());
        boolean puedeResenar = usuario != null && !esPropioPerfil
                && usuario.puedeComprar()
                && buscarPedidoEntregadoSinResena(usuario.getId(), id) != null;

        model.addAttribute("vendedor", vendedor);
        model.addAttribute("perfil", vendedor.getPerfilVendedor());
        model.addAttribute("ofertas", ofertas);
        model.addAttribute("resenas", resenas);
        model.addAttribute("autoresResenas", autoresResenas);
        model.addAttribute("promedio", promedio);
        model.addAttribute("totalResenas", total);
        model.addAttribute("currentPage", p);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("esPropioPerfil", esPropioPerfil);
        model.addAttribute("puedeResenar", puedeResenar);
        model.addAttribute("usuarioLogueado", esPropioPerfil ? null : usuario);
        return "vendedor/perfil-publico";
    }

    @PostMapping("/{id}/resena")
    public String crearResenaVendedor(@PathVariable String id,
            @RequestParam("calificacion") int calificacion,
            @RequestParam(value = "comentario", required = false) String comentario,
            HttpSession session, RedirectAttributes ra) {
        String redirectPerfil = "redirect:/vendedor/" + id + "/perfil";

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            ra.addFlashAttribute("error", "Debe iniciar sesión para dejar una reseña");
            return "redirect:/usuario/login";
        }
        if (id.equals(usuario.getId())) {
            ra.addFlashAttribute("error", "No puedes reseñar tu propia tienda.");
            return redirectPerfil;
        }
        if (calificacion < 1 || calificacion > 5) {
            ra.addFlashAttribute("error", "La calificación debe estar entre 1 y 5 estrellas");
            return redirectPerfil;
        }
        if (comentario != null) {
            comentario = comentario.trim();
            if (comentario.isEmpty()) {
                comentario = null;
            } else if (comentario.length() > MAX_COMENTARIO) {
                ra.addFlashAttribute("error", "El comentario no puede superar " + MAX_COMENTARIO + " caracteres.");
                return redirectPerfil;
            }
        }

        String pedidoValido = buscarPedidoEntregadoSinResena(usuario.getId(), id);
        if (pedidoValido == null) {
            ra.addFlashAttribute("error",
                    "Solo puedes reseñar a este vendedor si has recibido un pedido entregado de su tienda.");
            return redirectPerfil;
        }

        resenaVendedorRepository.save(new ResenaVendedor(id, usuario.getId(), pedidoValido, calificacion,
                comentario, LocalDate.now()));

        // Actualizar la calificación promedio copiada en las ofertas del vendedor
        double nuevoPromedio = calcularPromedio(resenaVendedorRepository.findByVendedorId(id));
        ofertaRepository.findByVendedor_Id(id).forEach(o -> {
            if (o.getVendedor() != null) {
                o.getVendedor().setCalificacionPromedio(nuevoPromedio);
                ofertaRepository.save(o);
            }
        });

        ra.addFlashAttribute("mensaje", "¡Gracias por tu reseña!");
        return redirectPerfil;
    }

    /**
     * Primer pedido ENTREGADO del comprador que pertenezca a este vendedor y aún no tenga reseña.
     * Cada sub-pedido guarda su vendedor; si falta, se intenta deducir por las ofertas de sus items.
     */
    private String buscarPedidoEntregadoSinResena(String compradorId, String vendedorId) {
        for (Pedido p : pedidoRepository.findByCompradorId(compradorId)) {
            if (p == null || !"ENTREGADO".equalsIgnoreCase(String.valueOf(p.getEstado()))) {
                continue;
            }
            if (!esDelVendedor(p, vendedorId)) {
                continue;
            }
            if (resenaVendedorRepository.existsByPedidoId(p.getId())) {
                continue;
            }
            return p.getId();
        }
        return null;
    }

    private boolean esDelVendedor(Pedido p, String vendedorId) {
        if (p.getVendedor() != null && p.getVendedor().getId() != null) {
            return vendedorId.equals(p.getVendedor().getId());
        }
        if (p.getItems() == null) {
            return false;
        }
        return p.getItems().stream()
                .filter(it -> it != null && it.getProductoId() != null)
                .anyMatch(it -> ofertaRepository.findById(it.getProductoId())
                        .map(o -> o.getVendedor() != null && vendedorId.equals(o.getVendedor().getId()))
                        .orElse(false));
    }

    private static double calcularPromedio(List<ResenaVendedor> resenas) {
        return resenas.stream()
                .filter(Objects::nonNull)
                .mapToInt(ResenaVendedor::getCalificacion)
                .average()
                .orElse(0.0);
    }
}
