package com.example.demo.Controller;

import com.example.demo.Model.Producto;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class RoutesController {

    @Autowired
    private ProductoRepository productoRepository;

    @GetMapping("/")
    public String index(Model model) {
        // Obtener los últimos 8 productos disponibles
        List<Producto> productos = productoRepository.findByDisponibleTrue()
                .stream()
                .limit(8)
                .collect(Collectors.toList());

        model.addAttribute("productos", productos);
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
    public String verFrutas(Model model) {
        List<Producto> productos = productoRepository.findByDisponibleTrue()
                .stream()
                .filter(p -> p.getCategoria() != null &&
                        "Frutas".equalsIgnoreCase(p.getCategoria().getNombre()))
                .collect(Collectors.toList());
        model.addAttribute("productos", productos);
        return "frutas";
    }

    @GetMapping("/verduras")
    public String verVerduras(Model model) {
        List<Producto> productos = productoRepository.findByDisponibleTrue()
                .stream()
                .filter(p -> p.getCategoria() != null &&
                        "Verduras".equalsIgnoreCase(p.getCategoria().getNombre()))
                .collect(Collectors.toList());
        model.addAttribute("productos", productos);
        return "verduras";
    }

    @GetMapping("/lacteos")
    public String verLacteos(Model model) {
        List<Producto> productos = productoRepository.findByDisponibleTrue()
                .stream()
                .filter(p -> p.getCategoria() != null &&
                        ("Lácteos".equalsIgnoreCase(p.getCategoria().getNombre()) ||
                         "Lacteos".equalsIgnoreCase(p.getCategoria().getNombre())))
                .collect(Collectors.toList());
        model.addAttribute("productos", productos);
        return "lacteos";
    }

    @GetMapping("/cafe")
    public String verCafe(Model model) {
        List<Producto> productos = productoRepository.findByDisponibleTrue()
                .stream()
                .filter(p -> p.getCategoria() != null &&
                        ("Café y Cacao".equalsIgnoreCase(p.getCategoria().getNombre()) ||
                         "Cafe".equalsIgnoreCase(p.getCategoria().getNombre()) ||
                         "Cacao".equalsIgnoreCase(p.getCategoria().getNombre())))
                .collect(Collectors.toList());
        model.addAttribute("productos", productos);
        return "cafeYcacao";
    }

    @GetMapping("/granos")
    public String verGranos(Model model) {
        List<Producto> productos = productoRepository.findByDisponibleTrue()
                .stream()
                .filter(p -> p.getCategoria() != null &&
                        ("Granos y Cereales".equalsIgnoreCase(p.getCategoria().getNombre()) ||
                                "Granos".equalsIgnoreCase(p.getCategoria().getNombre())))
                .collect(Collectors.toList());
        model.addAttribute("productos", productos);
        return "granos"; // nombre del HTML
    }

    @GetMapping("/miel")
    public String verMiel(Model model) {
        List<Producto> productos = productoRepository.findByDisponibleTrue()
                .stream()
                .filter(p -> p.getCategoria() != null &&
                        "Miel".equalsIgnoreCase(p.getCategoria().getNombre()))
                .collect(Collectors.toList());
        model.addAttribute("productos", productos);
        return "miel";
    }

    @GetMapping("/registro-vendedor")
    public String registroVendedor() {
        return "redirect:/vendedor/registro";
    }
}