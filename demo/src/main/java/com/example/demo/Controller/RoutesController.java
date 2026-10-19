package com.example.demo.Controller;

import com.example.demo.Model.Categoria;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.services.CatalogoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class RoutesController {

    @Autowired
    private CatalogoService catalogoService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @GetMapping("/")
    public String index(Model model) {
        // Obtener los primeros 8 productos aprobados del catálogo
        List<ProductoCatalogo> productos = catalogoService.listarCatalogo()
                .stream()
                .limit(8)
                .collect(Collectors.toList());

        model.addAttribute("productos", productos);
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

    @GetMapping({ "/frutas", "/verduras", "/lacteos", "/cafe", "/granos", "/miel" })
    public String redirigirCategoria(jakarta.servlet.http.HttpServletRequest request) {
        String slug = request.getRequestURI();
        if (slug != null && slug.startsWith("/")) {
            slug = slug.substring(1);
        }
        String nombre = switch (slug) {
            case "frutas" -> "Frutas";
            case "verduras" -> "Verduras";
            case "lacteos" -> "L\u00E1cteos";
            case "cafe" -> "Caf\u00E9 y Cacao";
            case "granos" -> "Granos y Cereales";
            case "miel" -> "Miel";
            default -> null;
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
