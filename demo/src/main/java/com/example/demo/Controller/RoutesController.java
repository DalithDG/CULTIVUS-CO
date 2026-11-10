package com.example.demo.Controller;

import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.services.CatalogoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoutesController {

    @Autowired
    private CatalogoService catalogoService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null) {
            if (usuario.hasRole(Role.ADMIN)) {
                return "redirect:/admin/dashboard";
            }
            if (usuario.hasRole(Role.VENDEDOR)) {
                return "redirect:/vendedor/dashboard";
            }
            if (usuario.hasRole(Role.TRANSPORTISTA)) {
                return "redirect:/transportista/inicio";
            }
            return "redirect:/comprador/inicio";
        }

        // Tarjetas: las 8 ofertas públicas más recientes (el catálogo solo es lista de referencia)
        model.addAttribute("ofertas", catalogoService.listarOfertasRecientes(8));
        model.addAttribute("categorias", categoriaRepository.findAll());
        return "public/inicio-publico";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "auth/login";
    }

    @GetMapping("/registro")
    public String registro() {
        return "auth/registro";
    }

    @GetMapping("/register")
    public String register() {
        return "redirect:/registro";
    }

    @GetMapping({ "/frutas", "/verduras", "/lacteos", "/cafe", "/cafeYcacao", "/granos", "/miel",
                   "/carnes", "/bebidas", "/condimentos", "/panaderia", "/otros", "/gb" })
    public String redirigirCategoria(jakarta.servlet.http.HttpServletRequest request) {
        String slug = request.getRequestURI();
        if (slug != null && slug.startsWith("/")) {
            slug = slug.substring(1);
        }
        String nombre = switch (slug) {
            case "frutas"      -> "Frutas";
            case "verduras"    -> "Verduras";
            case "lacteos"     -> "L\u00E1cteos";
            case "cafe", "cafeYcacao" -> "Caf\u00E9 y Cacao";
            case "granos"      -> "Granos y Cereales";
            case "miel"        -> "Miel";
            case "carnes"      -> "Carnes y Aves";
            case "condimentos" -> "Condimentos";
            default            -> null;
        };
        if (nombre == null) {
            return "redirect:/category";
        }
        return categoriaRepository.findByNombre(nombre)
                .map(cat -> "redirect:/category?categoria=" + cat.getId())
                .orElse("redirect:/category");
    }

    @GetMapping("/registro-vendedor")
    public String registroVendedor() {
        return "redirect:/vendedor/registro";
    }
}
