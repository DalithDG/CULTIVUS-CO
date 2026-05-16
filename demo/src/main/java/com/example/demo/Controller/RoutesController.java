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
        // Obtener los productos aprobados del catálogo (limitados a 8 para el inicio)
        List<ProductoCatalogo> productos = catalogoService.listarCatalogo()
                .stream()
                .limit(8)
                .collect(Collectors.toList());

        model.addAttribute("productos", productos);
        model.addAttribute("categorias", categoriaRepository.findAll());
        return "inicio-publico";
    }

    @GetMapping("/product-detall")
    public String productoDetall() {
        return "product-detall";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "login";
    }

    @GetMapping("/registro")
    public String registro() {
        return "registro";
    }

    @GetMapping("/register")
    public String register() {
        return "redirect:/registro";
    }

    @GetMapping("/frutas")
    public String verFrutas() {
        return categoriaRepository.findByNombre("Frutas")
                .map(cat -> "redirect:/category?categoria=" + cat.getId())
                .orElse("redirect:/category");
    }

    @GetMapping("/verduras")
    public String verVerduras() {
        return categoriaRepository.findByNombre("Verduras")
                .map(cat -> "redirect:/category?categoria=" + cat.getId())
                .orElse("redirect:/category");
    }

    @GetMapping("/lacteos")
    public String verLacteos() {
        return categoriaRepository.findByNombre("Lácteos")
                .map(cat -> "redirect:/category?categoria=" + cat.getId())
                .orElse("redirect:/category");
    }

    @GetMapping("/cafe")
    public String verCafe() {
        return categoriaRepository.findByNombre("Café y Cacao")
                .map(cat -> "redirect:/category?categoria=" + cat.getId())
                .orElse("redirect:/category");
    }

    @GetMapping("/granos")
    public String verGranos() {
        return categoriaRepository.findByNombre("Granos y Cereales")
                .map(cat -> "redirect:/category?categoria=" + cat.getId())
                .orElse("redirect:/category");
    }

    @GetMapping("/miel")
    public String verMiel() {
        return categoriaRepository.findByNombre("Miel")
                .map(cat -> "redirect:/category?categoria=" + cat.getId())
                .orElse("redirect:/category");
    }

    @GetMapping("/registro-vendedor")
    public String registroVendedor() {
        return "redirect:/vendedor/registro";
    }
}