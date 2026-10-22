package com.example.demo.Controller;

import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Páginas públicas informativas: "Nosotros / Productores" y "Ofertas".
 * Son accesibles para visitantes; el header se resuelve por rol en fragments/header.
 */
@Controller
public class PublicoController {

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /** Resumen público de un productor, derivado de sus ofertas disponibles. */
    public static class ProductorResumen {
        private final String id;
        private final String nombre;
        private final String descripcion;
        private final boolean verificado;
        private int totalOfertas;

        public ProductorResumen(String id, String nombre, String descripcion, boolean verificado) {
            this.id = id;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.verificado = verificado;
        }

        public String getId() { return id; }
        public String getNombre() { return nombre; }
        public String getDescripcion() { return descripcion; }
        public boolean isVerificado() { return verificado; }
        public int getTotalOfertas() { return totalOfertas; }
    }

    /** Una sola página con "Sobre Nosotros" y "Productores" (el select hace scroll entre secciones). */
    @GetMapping({"/nosotros", "/productores"})
    public String nosotros(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "8") int size,
            Model model) {
        Map<String, ProductorResumen> productores = new LinkedHashMap<>();

        // 1. Cargar todos los vendedores registrados
        List<Usuario> vendedores = usuarioRepository.findByRolesContaining(Role.VENDEDOR);
        for (Usuario u : vendedores) {
            String nombre = (u.getPerfilVendedor() != null && u.getPerfilVendedor().getRazonSocial() != null && !u.getPerfilVendedor().getRazonSocial().isBlank())
                    ? u.getPerfilVendedor().getRazonSocial()
                    : u.getNombre();
            String desc = (u.getPerfilVendedor() != null && u.getPerfilVendedor().getDescripcionNegocio() != null)
                    ? u.getPerfilVendedor().getDescripcionNegocio()
                    : "Productor local de Cultivus.";
            boolean verificado = u.getPerfilVendedor() != null && u.getPerfilVendedor().isVerificado();

            productores.put(u.getId(), new ProductorResumen(u.getId(), nombre, desc, verificado));
        }

        // 2. Contar ofertas activas para cada productor
        for (OfertaVendedor o : ofertaRepository.findAll()) {
            if (!o.isDisponible() || o.getVendedor() == null || o.getVendedor().getId() == null) {
                continue;
            }
            ProductorResumen p = productores.computeIfAbsent(o.getVendedor().getId(),
                    k -> new ProductorResumen(
                            o.getVendedor().getId(),
                            o.getVendedor().getRazonSocial() != null && !o.getVendedor().getRazonSocial().isBlank()
                                    ? o.getVendedor().getRazonSocial()
                                    : o.getVendedor().getNombre(),
                            o.getVendedor().getDescripcionNegocio(),
                            o.getVendedor().isVerificado()));
            p.totalOfertas++;
        }
        List<ProductorResumen> lista = new ArrayList<>(productores.values());
        lista.sort(Comparator.comparingInt(ProductorResumen::getTotalOfertas).reversed());

        // ── Paginación segura ──
        if (size < 1) {
            size = 8;
        }
        if (size > 48) {
            size = 48;
        }
        int totalItems = lista.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        if (totalPages == 0) {
            totalPages = 1;
        }
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        int from = page * size;
        int to = Math.min(from + size, totalItems);
        List<ProductorResumen> pagina = from >= to ? new ArrayList<>() : new ArrayList<>(lista.subList(from, to));

        model.addAttribute("productores", pagina);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("totalPages", totalPages);
        return "public/nosotros";
    }

    /** Listado público de ofertas disponibles, las más recientes primero. */
    @GetMapping("/ofertas")
    public String ofertas(Model model) {
        List<OfertaVendedor> ofertas = ofertaRepository.findAll().stream()
                .filter(o -> o.isDisponible() && o.getPrecio() != null)
                .sorted(Comparator.comparing(
                        (OfertaVendedor o) -> o.getCreatedAt() != null ? o.getCreatedAt() : LocalDateTime.MIN)
                        .reversed())
                .collect(Collectors.toList());
        model.addAttribute("ofertas", ofertas);
        return "public/ofertas";
    }

    /** Endpoint REST para consultar la lista de productores registrados (formato JSON). */
    @GetMapping("/api/productores")
    @ResponseBody
    public List<ProductorResumen> apiProductores() {
        Map<String, ProductorResumen> productores = new LinkedHashMap<>();

        List<Usuario> vendedores = usuarioRepository.findByRolesContaining(Role.VENDEDOR);
        for (Usuario u : vendedores) {
            String nombre = (u.getPerfilVendedor() != null && u.getPerfilVendedor().getRazonSocial() != null && !u.getPerfilVendedor().getRazonSocial().isBlank())
                    ? u.getPerfilVendedor().getRazonSocial()
                    : u.getNombre();
            String desc = (u.getPerfilVendedor() != null && u.getPerfilVendedor().getDescripcionNegocio() != null)
                    ? u.getPerfilVendedor().getDescripcionNegocio()
                    : "Productor local de Cultivus.";
            boolean verificado = u.getPerfilVendedor() != null && u.getPerfilVendedor().isVerificado();

            productores.put(u.getId(), new ProductorResumen(u.getId(), nombre, desc, verificado));
        }

        for (OfertaVendedor o : ofertaRepository.findAll()) {
            if (o.isDisponible() && o.getVendedor() != null && o.getVendedor().getId() != null) {
                ProductorResumen p = productores.get(o.getVendedor().getId());
                if (p != null) {
                    p.totalOfertas++;
                }
            }
        }

        List<ProductorResumen> lista = new ArrayList<>(productores.values());
        lista.sort(Comparator.comparingInt(ProductorResumen::getTotalOfertas).reversed());
        return lista;
    }
}

