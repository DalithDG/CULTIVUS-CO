package com.example.demo.Controller;

import com.example.demo.Model.Categoria;
import com.example.demo.Model.Producto;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.CategoriaProducto;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.UnidadMedida;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.repository.ProductoRepository;
import com.example.demo.services.ProductoService;
import com.example.demo.services.VendedorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/vendedor")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private VendedorService vendedorService;

    // Unidades de medida fijas — ya no tienen colección propia
    private static final List<UnidadMedida> UNIDADES_MEDIDA = Arrays.asList(
            new UnidadMedida("Kilogramos", "kg"),
            new UnidadMedida("Gramos", "g"),
            new UnidadMedida("Libras", "lb"),
            new UnidadMedida("Litros", "L"),
            new UnidadMedida("Mililitros", "ml"),
            new UnidadMedida("Unidades", "un"),
            new UnidadMedida("Docena", "doc")
    );

    /**
     * Muestra el formulario para agregar un nuevo producto
     */
    @GetMapping("/productos/nuevo")
    public String mostrarFormularioProducto(HttpSession session, Model model,
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

        // Cargar categorías y crearlas si no existen
        List<Categoria> categorias = categoriaRepository.findAll();
        if (categorias.isEmpty()) {
            crearCategoriasPorDefecto();
            categorias = categoriaRepository.findAll();
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("categorias", categorias);
        model.addAttribute("unidadesMedida", UNIDADES_MEDIDA);
        model.addAttribute("producto", new Producto());
        return "agregar-producto";
    }

    /**
     * Procesa el formulario de creación de producto
     */
    @PostMapping("/productos/guardar")
    public String guardarProducto(
            @RequestParam("nombre") String nombre,
            @RequestParam("precio") Double precio,
            @RequestParam("stock") int stock,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("imagenUrl") String imagenUrl,
            @RequestParam("peso") Double peso,
            @RequestParam("categoriaId") String categoriaId,
            @RequestParam("unidadMedidaId") String unidadMedidaAbreviatura,
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

            // Obtener categoría y crear objeto embebido
            Categoria categoria = categoriaRepository.findById(categoriaId)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
            CategoriaProducto categoriaEmbebida = new CategoriaProducto(
                    categoria.getId(), categoria.getNombre());

            // Obtener unidad de medida de la lista fija
            UnidadMedida unidadMedida = UNIDADES_MEDIDA.stream()
                    .filter(u -> u.getAbreviatura().equals(unidadMedidaAbreviatura))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unidad de medida no encontrada"));

            // Crear datos del vendedor embebidos
            DatosVendedor datosVendedor = new DatosVendedor(
                    usuario.getId(),
                    usuario.getNombre(),
                    usuario.getPerfilVendedor() != null &&
                    usuario.getPerfilVendedor().isVerificado()
            );

            // Crear el producto
            Producto producto = new Producto();
            producto.setNombre(nombre);
            producto.setPrecio(precio);
            producto.setStock(stock);
            producto.setDescripcion(descripcion);
            producto.setImagenUrl(imagenUrl);
            producto.setPeso(peso);
            producto.setCategoria(categoriaEmbebida);
            producto.setUnidadMedida(unidadMedida);
            producto.setVendedor(datosVendedor);

            productoService.crearProducto(producto);

            redirectAttributes.addFlashAttribute("mensaje", "Producto creado exitosamente");
            return "redirect:/vendedor/Miproductos";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/productos/nuevo";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al crear el producto: " + e.getMessage());
            return "redirect:/vendedor/productos/nuevo";
        }
    }

    /**
     * Muestra todos los productos del vendedor
     */
    @GetMapping("/Miproductos")
    public String mostrarMisProductos(HttpSession session, Model model,
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

        List<Producto> productos = productoRepository.findByVendedorId(usuario.getId());

        model.addAttribute("usuario", usuario);
        model.addAttribute("productos", productos);
        return "mis-productos";
    }

    /**
     * Muestra el formulario para editar un producto
     */
    @GetMapping("/productos/editar/{id}")
    public String mostrarFormularioEdicion(@PathVariable String id,
            HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }
        if (!vendedorService.esVendedor(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error", "Debe ser vendedor para editar productos");
            return "redirect:/vendedor/registro";
        }

        Producto producto = productoRepository.findById(id).orElse(null);
        if (producto == null) {
            redirectAttributes.addFlashAttribute("error", "Producto no encontrado");
            return "redirect:/vendedor/Miproductos";
        }

        // Verificar que el producto pertenece al vendedor
        if (!producto.getVendedor().getId().equals(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permiso para editar este producto");
            return "redirect:/vendedor/Miproductos";
        }

        List<Categoria> categorias = categoriaRepository.findAll();
        if (categorias.isEmpty()) {
            crearCategoriasPorDefecto();
            categorias = categoriaRepository.findAll();
        }

        model.addAttribute("producto", producto);
        model.addAttribute("categorias", categorias);
        model.addAttribute("unidadesMedida", UNIDADES_MEDIDA);
        model.addAttribute("usuario", usuario);
        return "editar-producto";
    }

    /**
     * Procesa la actualización de un producto
     */
    @PostMapping("/productos/actualizar/{id}")
    public String actualizarProducto(@PathVariable String id,
            @RequestParam("nombre") String nombre,
            @RequestParam("precio") Double precio,
            @RequestParam("stock") int stock,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("peso") Double peso,
            @RequestParam("imagenUrl") String imagenUrl,
            @RequestParam("categoriaId") String categoriaId,
            @RequestParam("unidadMedidaId") String unidadMedidaAbreviatura,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }

        Producto producto = productoRepository.findById(id).orElse(null);
        if (producto == null) {
            redirectAttributes.addFlashAttribute("error", "Producto no encontrado");
            return "redirect:/vendedor/Miproductos";
        }
        if (!producto.getVendedor().getId().equals(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permiso para editar este producto");
            return "redirect:/vendedor/Miproductos";
        }

        try {
            producto.setNombre(nombre);
            producto.setPrecio(precio);
            producto.setStock(stock);
            producto.setDescripcion(descripcion);
            producto.setPeso(peso);
            producto.setImagenUrl(imagenUrl);

            // Actualizar categoría embebida
            categoriaRepository.findById(categoriaId).ifPresent(cat ->
                    producto.setCategoria(new CategoriaProducto(cat.getId(), cat.getNombre()))
            );

            // Actualizar unidad de medida embebida
            UNIDADES_MEDIDA.stream()
                    .filter(u -> u.getAbreviatura().equals(unidadMedidaAbreviatura))
                    .findFirst()
                    .ifPresent(producto::setUnidadMedida);

            productoService.actualizarProducto(producto);

            redirectAttributes.addFlashAttribute("mensaje", "Producto actualizado exitosamente");
            return "redirect:/vendedor/Miproductos";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al actualizar el producto: " + e.getMessage());
            return "redirect:/vendedor/productos/editar/" + id;
        }
    }

    /**
     * Elimina un producto
     */
    @PostMapping("/productos/eliminar/{id}")
    public String eliminarProducto(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
                return "redirect:/usuario/login";
            }

            Producto producto = productoRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

            // Verificar que el producto pertenece al vendedor
            if (!producto.getVendedor().getId().equals(usuario.getId())) {
                redirectAttributes.addFlashAttribute("error",
                        "No tiene permiso para eliminar este producto");
                return "redirect:/vendedor/Miproductos";
            }

            productoRepository.delete(producto);
            redirectAttributes.addFlashAttribute("mensaje", "Producto eliminado exitosamente");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar el producto");
        }
        return "redirect:/vendedor/Miproductos";
    }

    // ── Métodos auxiliares ────────────────────────────────────────────────────

    private void crearCategoriasPorDefecto() {
        String[] categorias = {
            "Frutas", "Verduras", "Lácteos", "Carnes y Aves",
            "Granos y Cereales", "Bebidas", "Panadería", "Condimentos", "Otros"
        };
        for (String nombre : categorias) {
            if (!categoriaRepository.existsByNombre(nombre)) {
                Categoria cat = new Categoria();
                cat.setNombre(nombre);
                cat.setDescripcion("Categoría de " + nombre);
                categoriaRepository.save(cat);
            }
        }
    }
}