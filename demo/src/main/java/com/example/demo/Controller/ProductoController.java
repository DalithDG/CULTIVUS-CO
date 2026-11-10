package com.example.demo.Controller;

import com.example.demo.Model.Categoria;
import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.CategoriaProducto;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.UnidadMedida;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.services.CatalogoService;
import com.example.demo.services.ImagenUrlHelper;
import com.example.demo.services.OfertaService;
import com.example.demo.services.VendedorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/vendedor")
public class ProductoController {

    /** Las fotos de ofertas solo se aceptan si vienen de nuestra carpeta de Cloudinary. */
    private static final String PREFIJO_IMAGEN_CLOUDINARY = "https://res.cloudinary.com/";

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

    // Lista fija de unidades de medida: UnidadMedida.UNIDADES (sin colección propia)

    /**
     * Muestra el formulario para crear una nueva oferta.
     * El vendedor elige categoría → producto (cargado por AJAX) y define precio/stock.
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

        // Todas las categorías (para "No encuentro mi producto")
        List<Categoria> categorias = categoriaRepository.findAll();
        if (categorias.isEmpty()) {
            crearCategoriasPorDefecto();
            categorias = categoriaRepository.findAll();
        }

        // Solo las categorías que tienen productos aprobados y activos (primer desplegable)
        Map<String, String> conProductos = catalogoService.categoriasConProductosActivos();
        List<Categoria> categoriasConProductos = new ArrayList<>();
        for (Categoria c : categorias) {
            if (conProductos.containsKey(c.getId())) {
                categoriasConProductos.add(c);
            }
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("categoriasConProductos", categoriasConProductos);
        model.addAttribute("categorias", categorias);
        model.addAttribute("unidadesMedida", UnidadMedida.UNIDADES);
        return "vendedor/agregar-producto";
    }

    /**
     * Productos aprobados y activos de una categoría (segundo desplegable, por AJAX).
     * Incluye la unidad por defecto y las unidades permitidas de cada producto.
     */
    @GetMapping("/productos/catalogo")
    @ResponseBody
    public ResponseEntity<?> productosPorCategoria(@RequestParam("categoriaId") String categoriaId,
            HttpSession session) {
        ResponseEntity<?> noAutorizado = verificarVendedorJson(session);
        if (noAutorizado != null) {
            return noAutorizado;
        }
        List<Map<String, Object>> respuesta = new ArrayList<>();
        List<ProductoCatalogo> productos = new ArrayList<>(catalogoService.listarPorCategoria(categoriaId));
        Map<String, ProductoCatalogo> unicos = new LinkedHashMap<>();
        for (ProductoCatalogo p : productos) {
            if (p == null || p.getNombre() == null || !p.isActivo() || !p.isAprobado()) {
                continue;
            }
            String norm = p.getNombreNormalizado() != null ? p.getNombreNormalizado() : ProductoCatalogo.normalizarNombre(p.getNombre());
            unicos.putIfAbsent(norm, p);
        }
        List<ProductoCatalogo> listaOrdenada = new ArrayList<>(unicos.values());
        listaOrdenada.sort((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()));
        for (ProductoCatalogo p : listaOrdenada) {
            List<UnidadMedida> permitidas = unidadesPermitidasDe(p);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("nombre", p.getNombre());
            item.put("imagenUrl", p.getImagenUrl());
            item.put("unidadPorDefecto", unidadJson(p.getUnidadPorDefecto() != null
                    ? p.getUnidadPorDefecto() : permitidas.get(0)));
            List<Map<String, Object>> unidades = new ArrayList<>();
            for (UnidadMedida u : permitidas) {
                unidades.add(unidadJson(u));
            }
            item.put("unidadesPermitidas", unidades);
            respuesta.add(item);
        }
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Productos con nombre parecido (aviso antes de sugerir uno nuevo).
     * Compara por nombreNormalizado (sin tildes ni mayúsculas).
     */
    @GetMapping("/productos/similares")
    @ResponseBody
    public ResponseEntity<?> productosSimilares(@RequestParam("nombre") String nombre, HttpSession session) {
        ResponseEntity<?> noAutorizado = verificarVendedorJson(session);
        if (noAutorizado != null) {
            return noAutorizado;
        }
        List<Map<String, Object>> respuesta = new ArrayList<>();
        for (ProductoCatalogo p : catalogoService.buscarSimilares(nombre)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("nombre", p.getNombre());
            item.put("categoriaId", p.getCategoria() != null ? p.getCategoria().getId() : null);
            item.put("categoria", p.getCategoria() != null ? p.getCategoria().getNombre() : null);
            item.put("aprobado", p.isAprobado());
            respuesta.add(item);
        }
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Procesa la creación de una oferta para un producto de la lista.
     * La oferta se publica de inmediato; la unidad se elige entre las permitidas del producto.
     */
    @PostMapping("/productos/guardar")
    public String guardarOferta(
            @RequestParam("productoCatalogoId") String productoCatalogoId,
            @RequestParam(value = "unidadAbreviatura", required = false) String unidadAbreviatura,
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

            // Obtener producto de la lista
            ProductoCatalogo catalogo = catalogoService.obtenerPorId(productoCatalogoId);
            if (catalogo == null) {
                throw new IllegalArgumentException("Producto no encontrado");
            }

            // Unidad: si el producto solo admite una, va bloqueada (se ignora lo que envíe el cliente)
            List<UnidadMedida> permitidas = unidadesPermitidasDe(catalogo);
            UnidadMedida unidad;
            if (permitidas.size() == 1 || unidadAbreviatura == null || unidadAbreviatura.isBlank()) {
                unidad = catalogo.getUnidadPorDefecto() != null ? catalogo.getUnidadPorDefecto() : permitidas.get(0);
            } else {
                unidad = catalogo.buscarUnidadPermitida(unidadAbreviatura);
                if (unidad == null) {
                    throw new IllegalArgumentException("La unidad elegida no está permitida para " + catalogo.getNombre() + ".");
                }
            }

            // La foto propia es obligatoria (el servicio lo valida); ya no se copia la del catálogo
            imagenUrl = normalizarImagenUrl(imagenUrl);

            OfertaVendedor oferta = construirOferta(usuario, productoCatalogoId, unidad, precio, stock, peso,
                    pesoPromedioUnidad, descripcionPaquete, compraMinima, imagenUrl, descripcionVendedor,
                    catalogo.getNombre());

            ofertaService.crearOferta(oferta);

            redirectAttributes.addFlashAttribute("mensaje",
                    "¡Felicidades! Tu oferta para '" + catalogo.getNombre() + "' ha sido publicada exitosamente.");
            return "redirect:/vendedor/mis-productos";

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
     * "No encuentro mi producto": sugiere un producto nuevo y guarda la oferta en estado PENDIENTE
     * (no visible al público) ligada a él, hasta que el admin lo apruebe, lo marque "ya existe" o lo rechace.
     */
    @PostMapping("/productos/sugerir")
    public String sugerirProducto(
            @RequestParam("nombre") String nombre,
            @RequestParam("categoriaId") String categoriaId,
            @RequestParam("unidadSugerida") String unidadSugerida,
            @RequestParam(value = "unidadOtraNombre", required = false) String unidadOtraNombre,
            @RequestParam(value = "unidadOtraFraccionable", defaultValue = "false") boolean unidadOtraFraccionable,
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
            if (nombre == null || nombre.isBlank()) {
                throw new IllegalArgumentException("Escribe el nombre del producto.");
            }

            Categoria categoria = categoriaRepository.findById(categoriaId)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
            CategoriaProducto categoriaEmbebida = new CategoriaProducto(categoria.getId(), categoria.getNombre());

            UnidadMedida unidad = resolverUnidadSugerida(unidadSugerida, unidadOtraNombre, unidadOtraFraccionable);

            String imagen = normalizarImagenUrl(imagenUrl);
            if (imagen == null) {
                throw new IllegalArgumentException("Sube una foto de tu producto para enviar la sugerencia.");
            }
            String descripcion = (descripcionVendedor != null && !descripcionVendedor.isBlank())
                    ? descripcionVendedor.trim() : "Producto sugerido por un vendedor de Cultivus.";

            // La foto del vendedor pertenece a su oferta; la referencia del catálogo la define el admin
            ProductoCatalogo sugerido = new ProductoCatalogo(nombre.trim(), descripcion, ImagenUrlHelper.SIN_FOTO,
                    categoriaEmbebida, unidad, List.of(unidad));

            OfertaVendedor oferta = construirOferta(usuario, null, unidad, precio, stock, peso,
                    pesoPromedioUnidad, descripcionPaquete, compraMinima, imagen, descripcionVendedor,
                    nombre.trim());
            oferta.setEstadoPublicacion(EstadoPublicacion.PENDIENTE);

            ofertaService.sugerirProductoConOferta(sugerido, oferta, usuario.getId());

            redirectAttributes.addFlashAttribute("mensaje",
                    "Enviamos '" + nombre.trim() + "' a revisión. Tu oferta quedará pendiente (no visible al público) "
                            + "hasta que un administrador la apruebe; te avisaremos por notificación.");
            return "redirect:/vendedor/mis-productos";

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
    @GetMapping("/mis-productos")
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
        catalogoService.enriquecerConReferencia(ofertas);

        model.addAttribute("usuario", usuario);
        model.addAttribute("ofertas", ofertas);
        return "vendedor/mis-productos";
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
            return "redirect:/vendedor/mis-productos";
        }

        // Verificar que la oferta pertenece al vendedor
        if (!oferta.getVendedor().getId().equals(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permiso para editar esta oferta");
            return "redirect:/vendedor/mis-productos";
        }

        ProductoCatalogo productoCatalogo = catalogoService.obtenerPorId(oferta.getProductoCatalogoId());
        if (productoCatalogo != null) {
            oferta.setImagenReferencia(productoCatalogo.getImagenUrl());
        }

        List<UnidadMedida> unidadesElegibles = productoCatalogo != null
                ? unidadesPermitidasDe(productoCatalogo) : new ArrayList<>();

        model.addAttribute("oferta", oferta);
        model.addAttribute("productoCatalogo", productoCatalogo);
        model.addAttribute("unidad", oferta.unidadEfectiva());
        model.addAttribute("unidadesElegibles", unidadesElegibles);
        model.addAttribute("unidadBloqueada", ofertaService.unidadBloqueada(oferta.getId()));
        model.addAttribute("unidadesMedida", UnidadMedida.UNIDADES);
        model.addAttribute("usuario", usuario);
        return "vendedor/editar-producto";
    }

    /**
     * Procesa la actualización de una oferta.
     * Precio, stock y descripción siempre editables; la unidad se bloquea si hay pedidos o carritos.
     */
    @PostMapping("/productos/actualizar/{id}")
    public String actualizarOferta(@PathVariable String id,
            @RequestParam("precio") Double precio,
            @RequestParam("stock") Double stock,
            @RequestParam(value = "unidadAbreviatura", required = false) String unidadAbreviatura,
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
            return "redirect:/vendedor/mis-productos";
        }
        if (!oferta.getVendedor().getId().equals(usuario.getId())) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permiso para editar esta oferta");
            return "redirect:/vendedor/mis-productos";
        }

        try {
            // Cambio de unidad (el servicio lo rechaza si hay pedidos o carritos con esta oferta)
            if (unidadAbreviatura != null && !unidadAbreviatura.isBlank()
                    && (oferta.getUnidadMedida() == null
                    || !unidadAbreviatura.equalsIgnoreCase(oferta.getUnidadMedida().getAbreviatura()))) {
                ProductoCatalogo catalogo = catalogoService.obtenerPorId(oferta.getProductoCatalogoId());
                UnidadMedida nueva = catalogo != null ? catalogo.buscarUnidadPermitida(unidadAbreviatura) : null;
                if (nueva == null) {
                    throw new IllegalArgumentException("La unidad elegida no está permitida para este producto.");
                }
                oferta.setUnidadMedida(nueva.copia());
            }
            UnidadMedida unidad = oferta.unidadEfectiva();

            oferta.setPrecio(precio);
            oferta.setStock(stock);
            oferta.setPeso(peso != null && peso > 0 ? peso : null); // el servicio calcula el valor por defecto

            // Peso aproximado y descripción del paquete solo si la unidad es de tipo Unidad
            if (unidad.permiteDecimales()) {
                oferta.setPesoPromedioUnidad(null);
                oferta.setDescripcionPaquete(null);
            } else {
                oferta.setPesoPromedioUnidad(pesoPromedioUnidad);
                oferta.setDescripcionPaquete(descripcionPaquete);
            }
            oferta.setCompraMinima(compraMinima);

            String imagenNueva = normalizarImagenUrl(imagenUrl);
            if (imagenNueva != null) {
                oferta.setImagenUrl(imagenNueva);
            }
            if (descripcionVendedor != null) {
                oferta.setDescripcionVendedor(descripcionVendedor);
            }

            ofertaService.actualizarOferta(oferta);

            redirectAttributes.addFlashAttribute("mensaje", "Oferta actualizada exitosamente");
            return "redirect:/vendedor/mis-productos";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/productos/editar/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al actualizar la oferta: " + e.getMessage());
            return "redirect:/vendedor/productos/editar/" + id;
        }
    }

    /**
     * Corrige y reenvía una sugerencia rechazada (nombre y unidad); vuelve a quedar PENDIENTE.
     */
    @PostMapping("/productos/reenviar/{id}")
    public String reenviarSugerencia(@PathVariable String id,
            @RequestParam("nombre") String nombre,
            @RequestParam("unidadSugerida") String unidadSugerida,
            @RequestParam(value = "unidadOtraNombre", required = false) String unidadOtraNombre,
            @RequestParam(value = "unidadOtraFraccionable", defaultValue = "false") boolean unidadOtraFraccionable,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión primero");
            return "redirect:/usuario/login";
        }
        try {
            UnidadMedida unidad = resolverUnidadSugerida(unidadSugerida, unidadOtraNombre, unidadOtraFraccionable);
            ofertaService.reenviarOferta(id, usuario.getId(), nombre, unidad);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Reenviamos tu sugerencia a revisión. Te avisaremos cuando un administrador decida.");
            return "redirect:/vendedor/mis-productos";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/vendedor/productos/editar/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al reenviar la sugerencia: " + e.getMessage());
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
                return "redirect:/vendedor/mis-productos";
            }

            ofertaService.eliminarOferta(id);
            redirectAttributes.addFlashAttribute("mensaje", "Oferta eliminada exitosamente");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar la oferta");
        }
        return "redirect:/vendedor/mis-productos";
    }

    // ── Métodos auxiliares ────────────────────────────────────────────────────

    /** Vacío → null; si trae valor, debe ser una URL de Cloudinary (https). */
    private String normalizarImagenUrl(String imagenUrl) {
        if (imagenUrl == null || imagenUrl.isBlank()) {
            return null;
        }
        String url = imagenUrl.trim();
        if (!url.startsWith(PREFIJO_IMAGEN_CLOUDINARY)) {
            throw new IllegalArgumentException("La foto debe subirse desde el formulario.");
        }
        return url;
    }

    private OfertaVendedor construirOferta(Usuario usuario, String productoCatalogoId, UnidadMedida unidad,
            Double precio, Double stock, Double peso, Double pesoPromedioUnidad, String descripcionPaquete,
            Double compraMinima, String imagenUrl, String descripcionVendedor, String nombreProducto) {

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

        // Peso aproximado y descripción del paquete solo aplican a unidades de tipo Unidad
        Double pesoProm = unidad.permiteDecimales() ? null : pesoPromedioUnidad;
        String descPaquete = unidad.permiteDecimales() ? null : descripcionPaquete;

        OfertaVendedor oferta = new OfertaVendedor(
                productoCatalogoId,
                datosVendedor,
                precio,
                stock,
                (peso != null && peso > 0) ? peso : null, // el servicio calcula el valor por defecto
                pesoProm,
                descPaquete,
                compraMinima,
                imagenUrl,
                descripcionVendedor,
                nombreProducto
        );
        oferta.setUnidadMedida(unidad.copia());
        return oferta;
    }

    /** Interpreta la unidad elegida al sugerir un producto: de la lista fija o "otra" (personalizada). */
    private UnidadMedida resolverUnidadSugerida(String unidadSugerida, String otraNombre, boolean fraccionable) {
        if (unidadSugerida == null || unidadSugerida.isBlank()) {
            throw new IllegalArgumentException("Elige la unidad de venta.");
        }
        if (UnidadMedida.ABREVIATURA_OTRA.equalsIgnoreCase(unidadSugerida.trim())) {
            return UnidadMedida.personalizada(otraNombre, fraccionable);
        }
        for (UnidadMedida u : UnidadMedida.UNIDADES) {
            if (u.getAbreviatura().equals(unidadSugerida.trim())) {
                return u.copia();
            }
        }
        throw new IllegalArgumentException("La unidad elegida no es válida.");
    }

    /** Unidades permitidas del producto; para productos aún sin migrar se deriva de la unidad legacy. */
    private List<UnidadMedida> unidadesPermitidasDe(ProductoCatalogo p) {
        List<UnidadMedida> permitidas = p.getUnidadesPermitidas();
        if (permitidas != null && !permitidas.isEmpty()) {
            return permitidas;
        }
        List<UnidadMedida> lista = new ArrayList<>();
        lista.add(UnidadMedida.deAbreviatura(p.getUnidadMedida()));
        return lista;
    }

    private Map<String, Object> unidadJson(UnidadMedida u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", u.getNombre());
        m.put("abreviatura", u.getAbreviatura());
        m.put("tipo", u.getTipo());
        m.put("permiteDecimales", u.permiteDecimales());
        return m;
    }

    private ResponseEntity<?> verificarVendedorJson(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Debe iniciar sesión primero"));
        }
        if (!vendedorService.esVendedor(usuario.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Debe registrarse como vendedor primero"));
        }
        return null;
    }

    private void crearCategoriasPorDefecto() {
        String[] categorias = {
            "Frutas", "Verduras", "Lácteos", "Carnes y Aves",
            "Granos y Cereales", "Condimentos",
            "Café y Cacao", "Miel"
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
