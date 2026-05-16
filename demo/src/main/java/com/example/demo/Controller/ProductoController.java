package com.example.demo.Controller;

import com.example.demo.Model.Categoria;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.CategoriaProducto;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.UnidadMedida;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.services.CatalogoService;
import com.example.demo.services.OfertaService;
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
    private CatalogoService catalogoService;

    @Autowired
    private OfertaService ofertaService;

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private VendedorService vendedorService;

    // Unidades de medida fijas — ya no tienen colección propia
    private static final List<UnidadMedida> UNIDADES_MEDIDA = Arrays.asList(
            new UnidadMedida("Kilogramos", "kg", "Peso"),
            new UnidadMedida("Gramos", "g", "Peso"),
            new UnidadMedida("Libras", "lb", "Peso"),
            new UnidadMedida("Litros", "L", "Volumen"),
            new UnidadMedida("Mililitros", "ml", "Volumen"),
            new UnidadMedida("Unidades", "un", "Unidad"),
            new UnidadMedida("Docena", "doc", "Unidad"),
            new UnidadMedida("Manojo", "manojo", "Unidad"),
            new UnidadMedida("Bandeja", "bandeja", "Unidad")
    );

    /**
     * Muestra el formulario para crear una nueva oferta.
     * El vendedor selecciona un producto del catálogo y define su precio/stock.
     */
    @GetMapping("/productos/nuevo")
    public String mostrarFormularioOferta(HttpSession session, Model model,
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

        // Cargar productos del catálogo aprobados (para el dropdown)
        List<ProductoCatalogo> catalogoAprobado = catalogoService.listarAprobados();

        // Cargar categorías (para sugerir nuevo producto)
        List<Categoria> categorias = categoriaRepository.findAll();
        if (categorias.isEmpty()) {
            crearCategoriasPorDefecto();
            categorias = categoriaRepository.findAll();
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("catalogoProductos", catalogoAprobado);
        model.addAttribute("categorias", categorias);
        model.addAttribute("unidadesMedida", UNIDADES_MEDIDA);
        return "agregar-producto";
    }

    /**
     * Procesa la creación de una oferta para un producto existente del catálogo.
     */
    @PostMapping("/productos/guardar")
    public String guardarOferta(
            @RequestParam("productoCatalogoId") String productoCatalogoId,
            @RequestParam("precio") Double precio,
            @RequestParam("stock") Double stock,
            @RequestParam(value = "peso", required = false) Double peso,
            @RequestParam(value = "pesoPromedioUnidad", required = false) Double pesoPromedioUnidad,
            @RequestParam(value = "descripcionPaquete", required = false) String descripcionPaquete,
            @RequestParam(value = "imagenUrl", required = false) String imagenUrl,
            @RequestParam(value = "descripcionVendedor", required = false) String descripcionVendedor,
            @RequestParam(value = "compraMinima", defaultValue = "1") Double compraMinima,
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

            // Obtener producto del catálogo
            ProductoCatalogo catalogo = catalogoService.obtenerPorId(productoCatalogoId);
            if (catalogo == null) {
                throw new IllegalArgumentException("Producto del catálogo no encontrado");
            }

            // Si no se proporcionó imagen, usar la del catálogo
            if (imagenUrl == null || imagenUrl.trim().isEmpty()) {
                imagenUrl = catalogo.getImagenUrl();
            }

            // Crear datos del vendedor embebidos (snapshot enriquecido)
            DatosVendedor datosVendedor = new DatosVendedor(
                    usuario.getId(),
                    usuario.getNombre(),
                    usuario.getPerfilVendedor() != null &&
                    usuario.getPerfilVendedor().isVerificado(),
                    usuario.getPerfilVendedor() != null ? usuario.getPerfilVendedor().getRazonSocial() : null,
                    usuario.getPerfilVendedor() != null ? usuario.getPerfilVendedor().getTelefonoContacto() : null,
                    usuario.getPerfilVendedor() != null ? usuario.getPerfilVendedor().getDescripcionNegocio() : null
            );

            // Si el peso es null (no viene en el form de oferta simple), calculamos un default
            if (peso == null || peso <= 0) {
                if ("PESO".equalsIgnoreCase(catalogo.getTipoVenta())) {
                    peso = 1.0; // 1 unidad de stock = 1 kg/lb/etc
                } else {
                    // Si es por unidad, usamos el peso promedio si existe, sino 1.0
                    peso = (pesoPromedioUnidad != null && pesoPromedioUnidad > 0) ? pesoPromedioUnidad : 1.0;
                }
            }

            // Crear la oferta
            OfertaVendedor oferta = new OfertaVendedor(
                    productoCatalogoId,
                    datosVendedor,
                    precio,
                    stock,
                    peso,
                    pesoPromedioUnidad,
                    descripcionPaquete,
                    compraMinima > 0 ? compraMinima : 1.0,
                    imagenUrl,
                    descripcionVendedor,
                    catalogo.getNombre()
            );

            ofertaService.crearOferta(oferta);

            redirectAttributes.addFlashAttribute("mensaje",
                    "¡Felicidades! Tu oferta para '" + catalogo.getNombre() + "' ha sido publicada exitosamente.");
            return "redirect:/vendedor/Miproductos";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/productos/nuevo";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al crear la oferta: " + e.getMessage());
            return "redirect:/vendedor/productos/nuevo";
        }
    }

    /**
     * Sugiere un nuevo producto para el catálogo (pendiente de aprobación del admin).
     */
    @PostMapping("/productos/sugerir")
    public String sugerirProducto(
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("imagenUrl") String imagenUrl,
            @RequestParam("categoriaId") String categoriaId,
            @RequestParam("tipoVenta") String tipoVenta,
            @RequestParam("unidadMedida") String unidadMedida,
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

            // Obtener categoría
            Categoria categoria = categoriaRepository.findById(categoriaId)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
            CategoriaProducto categoriaEmbebida = new CategoriaProducto(
                    categoria.getId(), categoria.getNombre());

            // Crear producto del catálogo (pendiente de aprobación)
            ProductoCatalogo producto = new ProductoCatalogo(nombre, descripcion, imagenUrl,
                    categoriaEmbebida, tipoVenta, unidadMedida);

            catalogoService.sugerirProducto(producto, usuario.getId());

            redirectAttributes.addFlashAttribute("mensaje",
                    "Tu sugerencia de producto '" + nombre + "' ha sido enviada. " +
                    "Un administrador la revisará pronto. Una vez aprobada, podrás crear tu oferta.");
            return "redirect:/vendedor/productos/nuevo";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/productos/nuevo";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al sugerir el producto: " + e.getMessage());
            return "redirect:/vendedor/productos/nuevo";
        }
    }

    /**
     * Muestra todas las ofertas del vendedor (antes era "Mis Productos")
     */
    @GetMapping("/Miproductos")
    public String mostrarMisOfertas(HttpSession session, Model model,
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

        // Obtener ofertas del vendedor
        List<OfertaVendedor> ofertas = ofertaService.listarOfertasVendedor(usuario.getId());

        model.addAttribute("usuario", usuario);
        model.addAttribute("ofertas", ofertas);
        return "mis-productos";
    }

    /**
     * Muestra el formulario para editar una oferta
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
            redirectAttributes.addFlashAttribute("error", "Debe ser vendedor para editar ofertas");
            return "redirect:/vendedor/registro";
        }

        OfertaVendedor oferta = ofertaService.buscarPorId(id);
        if (oferta == null) {
            redirectAttributes.addFlashAttribute("error", "Oferta no encontrada");
            return "redirect:/vendedor/Miproductos";
        }

        // Verificar que la oferta pertenece al vendedor
        if (!oferta.getVendedor().getId().equals(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permiso para editar esta oferta");
            return "redirect:/vendedor/Miproductos";
        }

        ProductoCatalogo productoCatalogo = catalogoService.obtenerPorId(oferta.getProductoCatalogoId());

        model.addAttribute("oferta", oferta);
        model.addAttribute("productoCatalogo", productoCatalogo);
        model.addAttribute("usuario", usuario);
        return "editar-producto";
    }

    /**
     * Procesa la actualización de una oferta
     */
    @PostMapping("/productos/actualizar/{id}")
    public String actualizarOferta(@PathVariable String id,
            @RequestParam("precio") Double precio,
            @RequestParam("stock") Double stock,
            @RequestParam(value = "peso", required = false) Double peso,
            @RequestParam(value = "pesoPromedioUnidad", required = false) Double pesoPromedioUnidad,
            @RequestParam(value = "descripcionPaquete", required = false) String descripcionPaquete,
            @RequestParam(value = "imagenUrl", required = false) String imagenUrl,
            @RequestParam(value = "descripcionVendedor", required = false) String descripcionVendedor,
            @RequestParam(value = "compraMinima", defaultValue = "1") Double compraMinima,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }

        OfertaVendedor oferta = ofertaService.buscarPorId(id);
        if (oferta == null) {
            redirectAttributes.addFlashAttribute("error", "Oferta no encontrada");
            return "redirect:/vendedor/Miproductos";
        }
        if (!oferta.getVendedor().getId().equals(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permiso para editar esta oferta");
            return "redirect:/vendedor/Miproductos";
        }

        try {
            oferta.setPrecio(precio);
            oferta.setStock(stock);
            
            // Manejo de peso en actualización
            if (peso == null || peso <= 0) {
                ProductoCatalogo catalogo = catalogoService.obtenerPorId(oferta.getProductoCatalogoId());
                if (catalogo != null) {
                    if ("PESO".equalsIgnoreCase(catalogo.getTipoVenta())) {
                        peso = 1.0;
                    } else {
                        peso = (pesoPromedioUnidad != null && pesoPromedioUnidad > 0) ? pesoPromedioUnidad : 1.0;
                    }
                } else {
                    peso = 1.0;
                }
            }
            
            oferta.setPeso(peso);
            oferta.setPesoPromedioUnidad(pesoPromedioUnidad);
            oferta.setDescripcionPaquete(descripcionPaquete);
            oferta.setCompraMinima(compraMinima > 0 ? compraMinima : 1.0);

            if (imagenUrl != null && !imagenUrl.trim().isEmpty()) {
                oferta.setImagenUrl(imagenUrl);
            }
            if (descripcionVendedor != null) {
                oferta.setDescripcionVendedor(descripcionVendedor);
            }

            ofertaService.actualizarOferta(oferta);

            redirectAttributes.addFlashAttribute("mensaje", "Oferta actualizada exitosamente");
            return "redirect:/vendedor/Miproductos";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al actualizar la oferta: " + e.getMessage());
            return "redirect:/vendedor/productos/editar/" + id;
        }
    }

    /**
     * Elimina una oferta
     */
    @PostMapping("/productos/eliminar/{id}")
    public String eliminarOferta(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
            if (usuario == null) {
                redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
                return "redirect:/usuario/login";
            }

            OfertaVendedor oferta = ofertaService.buscarPorId(id);
            if (oferta == null) {
                throw new IllegalArgumentException("Oferta no encontrada");
            }

            // Verificar que la oferta pertenece al vendedor
            if (!oferta.getVendedor().getId().equals(usuario.getId())) {
                redirectAttributes.addFlashAttribute("error",
                        "No tiene permiso para eliminar esta oferta");
                return "redirect:/vendedor/Miproductos";
            }

            ofertaService.eliminarOferta(id);
            redirectAttributes.addFlashAttribute("mensaje", "Oferta eliminada exitosamente");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar la oferta");
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