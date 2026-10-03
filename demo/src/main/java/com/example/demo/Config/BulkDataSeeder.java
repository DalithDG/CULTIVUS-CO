package com.example.demo.Config;

import com.example.demo.Model.*;
import com.example.demo.Model.embebidos.*;
import com.example.demo.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Order(2)
@Profile("dev")
public class BulkDataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(BulkDataSeeder.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoCatalogoRepository productoCatalogoRepository;

    @Autowired
    private OfertaRepository ofertaRepository;
    
    @Autowired
    private ProductoRepository productoRepository;
    
    @Autowired
    private PedidoRepository pedidoRepository;
    
    @Autowired
    private ResenaRepository resenaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (ofertaRepository.count() > 10000) {
            logger.info("ℹ️ La base de datos ya contiene un volumen alto de datos. Se omite BulkDataSeeder.");
            return;
        }

        logger.info("🚀 Iniciando inyección masiva SIMULACIÓN REAL (BulkDataSeeder)...");
        long startTime = System.currentTimeMillis();

        limpiarBaseDeDatos();
        
        List<Categoria> categorias = inyectarCategorias();
        List<Usuario> vendedores = inyectarVendedores(200);
        List<Usuario> compradores = inyectarCompradores(50);
        List<ProductoCatalogo> catalogo = inyectarCatalogo(100, categorias);
        
        inyectarProductosAntiguos(15000, catalogo, vendedores);
        
        List<OfertaVendedor> ofertasInyectadas = inyectarOfertas(14700, catalogo, vendedores);
        
        List<Pedido> pedidos = inyectarPedidos(1000, compradores, ofertasInyectadas);
        inyectarResenas(500, pedidos);

        long endTime = System.currentTimeMillis();
        logger.info("✅ BulkDataSeeder finalizó exitosamente en " + (endTime - startTime) + "ms.");
    }

    private void limpiarBaseDeDatos() {
        logger.info("🧹 Limpiando base de datos (Categorias, Productos, Ofertas, Pedidos, Reseñas)...");
        categoriaRepository.deleteAll();
        productoCatalogoRepository.deleteAll();
        productoRepository.deleteAll();
        ofertaRepository.deleteAll();
        pedidoRepository.deleteAll();
        resenaRepository.deleteAll();
        
        List<Usuario> usuarios = usuarioRepository.findAll();
        for (Usuario u : usuarios) {
            if (!u.hasRole(Role.ADMIN)) {
                usuarioRepository.delete(u);
            }
        }
        logger.info("✅ Limpieza completada.");
    }

    private List<Categoria> inyectarCategorias() {
        logger.info("📦 Inyectando Categorías...");
        List<Categoria> categorias = Arrays.asList(
            new Categoria("Frutas", "fas fa-apple-alt"),
            new Categoria("Verduras", "fas fa-carrot"),
            new Categoria("Tubérculos", "fas fa-seedling"),
            new Categoria("Granos y Cereales", "fas fa-wheat"),
            new Categoria("Procesados Artesanales", "fas fa-cheese")
        );
        return (List<Categoria>) categoriaRepository.saveAll(categorias);
    }

    private List<Usuario> inyectarVendedores(int cantidad) {
        logger.info("👨‍🌾 Inyectando {} Vendedores...", cantidad);
        List<Usuario> vendedores = new ArrayList<>();
        String encodedPassword = passwordEncoder.encode("Vendedor123!");
        
        String[] nombres = {"Juan", "Maria", "Carlos", "Ana", "Luis", "Carmen", "Jorge", "Laura", "Pedro", "Sofia"};
        String[] apellidos = {"Perez", "Gomez", "Rodriguez", "Fernandez", "Lopez", "Martinez", "Gonzalez", "Garcia", "Ruiz", "Diaz"};
        String[] municipios = {"Tunja", "Duitama", "Sogamoso", "Paipa", "Chiquinquirá", "Moniquirá", "Villa de Leyva"};

        Random rand = new Random();

        for (int i = 0; i < cantidad; i++) {
            Usuario u = new Usuario();
            String nombreCompleto = nombres[rand.nextInt(nombres.length)] + " " + apellidos[rand.nextInt(apellidos.length)];
            u.setNombre(nombreCompleto + " (Finca " + (i+1) + ")");
            u.setEmail("vendedor" + i + "@finca.com");
            u.setContrasena(encodedPassword);
            u.addRole(Role.VENDEDOR);
            
            UbicacionUsuario ubicacion = new UbicacionUsuario();
            ubicacion.setDepartamento("Boyacá");
            ubicacion.setCiudad(municipios[rand.nextInt(municipios.length)]);
            u.setUbicacion(ubicacion);

            PerfilVendedor pv = new PerfilVendedor();
            pv.setRazonSocial("Finca La " + apellidos[rand.nextInt(apellidos.length)]);
            pv.setDescripcionNegocio("Productor local 100% orgánico");
            pv.setVerificado(true);
            u.setPerfilVendedor(pv);

            vendedores.add(u);
        }
        return (List<Usuario>) usuarioRepository.saveAll(vendedores);
    }
    
    private List<Usuario> inyectarCompradores(int cantidad) {
        logger.info("👨‍👩‍👧‍👦 Inyectando {} Compradores...", cantidad);
        List<Usuario> compradores = new ArrayList<>();
        String encodedPassword = passwordEncoder.encode("Comprador123!");
        
        String[] nombres = {"Daniel", "Valentina", "Andres", "Camila", "Felipe", "Isabella", "David", "Mariana"};
        String[] apellidos = {"Ramirez", "Osorio", "Castillo", "Vargas", "Morales", "Castro", "Ortiz", "Navarro"};
        String[] ciudades = {"Bogotá", "Medellín", "Cali", "Barranquilla", "Bucaramanga", "Cartagena"};

        Random rand = new Random();

        for (int i = 0; i < cantidad; i++) {
            Usuario u = new Usuario();
            String nombreCompleto = nombres[rand.nextInt(nombres.length)] + " " + apellidos[rand.nextInt(apellidos.length)];
            u.setNombre(nombreCompleto);
            u.setEmail("comprador" + i + "@email.com");
            u.setContrasena(encodedPassword);
            u.addRole(Role.COMPRADOR);
            
            UbicacionUsuario ubicacion = new UbicacionUsuario();
            ubicacion.setDepartamento("Cundinamarca"); // Simulación
            ubicacion.setCiudad(ciudades[rand.nextInt(ciudades.length)]);
            u.setUbicacion(ubicacion);

            compradores.add(u);
        }
        return (List<Usuario>) usuarioRepository.saveAll(compradores);
    }

    private List<ProductoCatalogo> inyectarCatalogo(int cantidad, List<Categoria> categorias) {
        logger.info("🍎 Inyectando Catálogo de Productos ({} registros)...", cantidad);
        
        Object[][] productosBase = {
            {"Tomate Chonto", "Tomate fresco cultivado sin pesticidas", "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?q=80&w=600", "Verduras"},
            {"Aguacate Hass", "Aguacate cremoso especial para ensaladas", "https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?q=80&w=600", "Frutas"},
            {"Papa Pastusa", "Papa ideal para sopas y purés", "https://images.unsplash.com/photo-1518977676601-b53f82aba655?q=80&w=600", "Tubérculos"},
            {"Zanahoria Orgánica", "Zanahoria crujiente y dulce", "https://images.unsplash.com/photo-1598170845058-32b9d6a5da37?q=80&w=600", "Verduras"},
            {"Cebolla Cabezona", "Cebolla blanca de gran tamaño", "https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?q=80&w=600", "Verduras"},
            {"Fresa Dulce", "Fresas seleccionadas de invernadero", "https://images.unsplash.com/photo-1464965911861-746a04b4bca6?q=80&w=600", "Frutas"},
            {"Naranja Tangelo", "Naranja jugosa para jugo", "https://images.unsplash.com/photo-1611080626919-7cf5a9dbab5b?q=80&w=600", "Frutas"},
            {"Plátano Hartón", "Plátano verde grande", "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?q=80&w=600", "Frutas"},
            {"Limón Tahití", "Limón sin semilla", "https://images.unsplash.com/photo-1590502593747-42298799482c?q=80&w=600", "Frutas"},
            {"Yuca Blanca", "Yuca suave y de rápida cocción", "https://images.unsplash.com/photo-1588661667086-444458cc0060?q=80&w=600", "Tubérculos"}
        };

        String[] adjetivos = {"Premium", "Extra", "Especial", "Familiar", "Ecológico", "Artesanal", "Gourmet", "Fresco", "de Temporada", "Tradicional"};

        List<ProductoCatalogo> catalogo = new ArrayList<>();
        Random rand = new Random();

        int idx = 0;
        while (catalogo.size() < cantidad) {
            Object[] base = productosBase[idx % productosBase.length];
            String nombre = (String) base[0];
            if (idx >= productosBase.length) {
                nombre += " " + adjetivos[rand.nextInt(adjetivos.length)] + " " + (idx / productosBase.length);
            }
            
            String categoriaNombre = (String) base[3];
            Categoria cat = categorias.stream().filter(c -> c.getNombre().equals(categoriaNombre)).findFirst().orElse(categorias.get(0));
            CategoriaProducto catEmbebida = new CategoriaProducto(cat.getId(), cat.getNombre());

            ProductoCatalogo p = new ProductoCatalogo(
                nombre,
                (String) base[1],
                (String) base[2],
                catEmbebida,
                "B2C",
                "KG"
            );
            p.setAprobado(true);
            catalogo.add(p);
            idx++;
        }

        return (List<ProductoCatalogo>) productoCatalogoRepository.saveAll(catalogo);
    }
    
    private void inyectarProductosAntiguos(int cantidad, List<ProductoCatalogo> catalogo, List<Usuario> vendedores) {
        logger.info("📦 Inyectando {} modelo antiguo de Productos (Admin Dashboard)...", cantidad);
        List<Producto> loteProductos = new ArrayList<>();
        Random rand = new Random();
        
        for (int i = 0; i < cantidad; i++) {
            ProductoCatalogo pCat = catalogo.get(rand.nextInt(catalogo.size()));
            Usuario v = vendedores.get(rand.nextInt(vendedores.size()));
            DatosVendedor dv = new DatosVendedor();
            dv.setId(v.getId());
            dv.setNombre(v.getNombre());
            dv.setRazonSocial(v.getPerfilVendedor().getRazonSocial());
            
            UnidadMedida um = new UnidadMedida("Kilogramos", "KG", "Masa");
            
            Producto p = new Producto(
                pCat.getNombre() + " - Lote " + i,
                1500.0 + rand.nextInt(5000),
                (double) (50 + rand.nextInt(450)),
                pCat.getDescripcion(),
                pCat.getImagenUrl(),
                1.0,
                pCat.getCategoria(),
                um,
                dv
            );
            loteProductos.add(p);

            // Inyectar en lotes de 1000 para no agotar la memoria
            if (loteProductos.size() == 1000) {
                productoRepository.saveAll(loteProductos);
                loteProductos.clear();
            }
        }
        
        if (!loteProductos.isEmpty()) {
            productoRepository.saveAll(loteProductos);
        }
    }

    private List<OfertaVendedor> inyectarOfertas(int cantidad, List<ProductoCatalogo> catalogo, List<Usuario> vendedores) {
        logger.info("🏷️ Inyectando {} Ofertas...", cantidad);
        List<OfertaVendedor> todasLasOfertas = new ArrayList<>();
        List<OfertaVendedor> loteOfertas = new ArrayList<>();
        Random rand = new Random();

        int batchSize = 1000;
        int insertados = 0;

        class Par {
            ProductoCatalogo p; Usuario v;
            Par(ProductoCatalogo p, Usuario v) { this.p = p; this.v = v; }
        }

        List<Par> todasCombinaciones = new ArrayList<>(catalogo.size() * vendedores.size());
        for (ProductoCatalogo p : catalogo) {
            for (Usuario v : vendedores) {
                todasCombinaciones.add(new Par(p, v));
            }
        }

        Collections.shuffle(todasCombinaciones);

        for (int i = 0; i < cantidad && i < todasCombinaciones.size(); i++) {
            Par par = todasCombinaciones.get(i);
            
            DatosVendedor dv = new DatosVendedor();
            dv.setId(par.v.getId());
            dv.setNombre(par.v.getNombre());
            dv.setRazonSocial(par.v.getPerfilVendedor().getRazonSocial());
            
            double precioBase = 1500.0 + (rand.nextInt(5000));
            precioBase = Math.round(precioBase / 100.0) * 100.0;
            
            OfertaVendedor oferta = new OfertaVendedor(
                par.p.getId(),
                dv,
                precioBase,
                (double) (50 + rand.nextInt(450)),
                1.0,
                1.0,
                "Empaque ecológico",
                (double) (1 + rand.nextInt(4)),
                par.p.getImagenUrl(),
                "Cosecha del día, excelente calidad.",
                par.p.getNombre()
            );

            loteOfertas.add(oferta);
            todasLasOfertas.add(oferta);

            if (loteOfertas.size() == batchSize) {
                ofertaRepository.saveAll(loteOfertas);
                insertados += loteOfertas.size();
                logger.info("   Lote insertado. Progreso: {}/{}", insertados, cantidad);
                loteOfertas.clear();
            }
        }

        if (!loteOfertas.isEmpty()) {
            ofertaRepository.saveAll(loteOfertas);
            insertados += loteOfertas.size();
            logger.info("   Lote final insertado. Progreso: {}/{}", insertados, cantidad);
        }
        
        logger.info("📊 Actualizando métricas de productos...");
        for (ProductoCatalogo p : catalogo) {
            long numOfertas = ofertaRepository.countByProductoCatalogoIdAndDisponibleTrue(p.getId());
            p.setTotalVendedores((int) numOfertas);
            productoCatalogoRepository.save(p);
        }
        
        return todasLasOfertas;
    }
    
    private List<Pedido> inyectarPedidos(int cantidad, List<Usuario> compradores, List<OfertaVendedor> ofertas) {
        logger.info("📦 Inyectando {} Pedidos...", cantidad);
        List<Pedido> pedidos = new ArrayList<>();
        Random rand = new Random();
        
        String[] metodos = {"EFECTIVO", "TRANSFERENCIA", "TARJETA_CREDITO"};
        String[] estados = {"ENTREGADO", "ENVIADO", "PENDIENTE", "ENTREGADO", "ENTREGADO"}; // Mayoría entregado para que haya reseñas
        
        for (int i = 0; i < cantidad; i++) {
            // Seleccionar comprador y oferta aleatorios
            Usuario comprador = compradores.get(rand.nextInt(compradores.size()));
            OfertaVendedor oferta = ofertas.get(rand.nextInt(ofertas.size()));
            
            DatosComprador dc = new DatosComprador(comprador.getId(), comprador.getNombre());
            
            DireccionPedido dp = new DireccionPedido("Carrera " + rand.nextInt(100) + " #" + rand.nextInt(100), 
                                                     comprador.getUbicacion().getCiudad(), 
                                                     comprador.getUbicacion().getDepartamento());
            
            double cantidadComprada = oferta.getCompraMinima() + rand.nextInt(10);
            
            ProductoPedido item = new ProductoPedido(
                    oferta.getProductoCatalogoId(), 
                    oferta.getNombreProducto(), 
                    oferta.getImagenUrl(), 
                    oferta.getPrecio(), 
                    cantidadComprada, 
                    "KG");
            
            List<ProductoPedido> items = new ArrayList<>();
            items.add(item);
            
            double total = item.getSubtotal();
            DatosPago pago = new DatosPago(metodos[rand.nextInt(metodos.length)], total);
            
            Pedido p = new Pedido(dc, oferta.getVendedor(), dp, items, pago);
            p.setEstado(estados[rand.nextInt(estados.length)]);
            
            // Simular fecha pasada (hasta 6 meses atrás)
            LocalDateTime fechaAnterior = LocalDateTime.now().minusDays(rand.nextInt(180));
            p.setFechaPedido(fechaAnterior);
            p.setCreatedAt(fechaAnterior);
            
            pedidos.add(p);
            
            // Para no sobrecargar la memoria lo guardamos en lotes o directamente
            if (pedidos.size() % 500 == 0) {
                pedidoRepository.saveAll(pedidos);
                logger.info("   Pedidos inyectados: {}", i + 1);
                pedidos.clear();
            }
        }
        
        if (!pedidos.isEmpty()) {
            pedidoRepository.saveAll(pedidos);
        }
        
        return pedidoRepository.findAll();
    }
    
    private void inyectarResenas(int cantidad, List<Pedido> pedidos) {
        logger.info("⭐ Inyectando {} Reseñas...", cantidad);
        
        // Filtramos pedidos entregados
        List<Pedido> entregados = new ArrayList<>();
        for (Pedido p : pedidos) {
            if ("ENTREGADO".equals(p.getEstado())) {
                entregados.add(p);
            }
        }
        
        if (entregados.isEmpty()) return;
        
        String[] comentarios = {
            "Excelente calidad, muy fresco todo.",
            "Me encantó, volveré a comprar.",
            "El pedido llegó a tiempo y en perfectas condiciones.",
            "Buen producto, pero el empaque podría mejorar.",
            "Increíble sabor, muy recomendado.",
            "Producto aceptable, cumple con lo prometido.",
            "Los productos estaban un poco maduros, pero bien.",
            "Súper recomendado, directo del campo a mi mesa.",
            "Gran atención del vendedor y excelente producto.",
            "Todo perfecto, como siempre."
        };
        
        List<Resena> resenas = new ArrayList<>();
        Random rand = new Random();
        
        for (int i = 0; i < cantidad && i < entregados.size(); i++) {
            Pedido p = entregados.get(i);
            
            if (p.getItems().isEmpty()) continue;
            
            String productoId = p.getItems().get(0).getProductoId();
            int calificacion = 3 + rand.nextInt(3); // Entre 3 y 5
            
            Resena r = new Resena(
                productoId,
                p.getComprador().getId(),
                p.getId(),
                calificacion,
                comentarios[rand.nextInt(comentarios.length)],
                p.getFechaPedido().toLocalDate().plusDays(1 + rand.nextInt(3))
            );
            
            resenas.add(r);
        }
        
        resenaRepository.saveAll(resenas);
        logger.info("✅ Reseñas inyectadas.");
    }
}
