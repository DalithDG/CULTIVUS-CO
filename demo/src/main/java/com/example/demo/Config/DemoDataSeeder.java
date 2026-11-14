package com.example.demo.Config;

import com.example.demo.Model.Banco;
import com.example.demo.Model.Billetera;
import com.example.demo.Model.CanceladoPor;
import com.example.demo.Model.Compra;
import com.example.demo.Model.Envio;
import com.example.demo.Model.EstadoPedido;
import com.example.demo.Model.EstadoPublicacion;
import com.example.demo.Model.MovimientoBilletera;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.Pedido;
import com.example.demo.Model.ProductoCatalogo;
import com.example.demo.Model.Resena;
import com.example.demo.Model.ResenaVendedor;
import com.example.demo.Model.Role;
import com.example.demo.Model.SolicitudRetiro;
import com.example.demo.Model.TipoCuenta;
import com.example.demo.Model.TipoDocumento;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.CuentaBancaria;
import com.example.demo.Model.embebidos.DatosComprador;
import com.example.demo.Model.embebidos.DatosPago;
import com.example.demo.Model.embebidos.DatosVendedor;
import com.example.demo.Model.embebidos.DireccionPedido;
import com.example.demo.Model.embebidos.PerfilAdmin;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.Model.embebidos.ProductoPedido;
import com.example.demo.Model.embebidos.ParadaEnvio;
import com.example.demo.Model.embebidos.SnapshotTransportista;
import com.example.demo.Model.embebidos.UbicacionUsuario;
import com.example.demo.repository.CompraRepository;
import com.example.demo.repository.EnvioRepository;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.ProductoCatalogoRepository;
import com.example.demo.repository.ResenaRepository;
import com.example.demo.repository.ResenaVendedorRepository;
import com.example.demo.repository.SolicitudRetiroRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.AppConfigService;
import com.example.demo.services.BilleteraAlmacen;
import com.example.demo.services.BilleteraService;
import com.example.demo.services.CalculoEnvioService;
import com.example.demo.services.CatalogoInicializador;
import com.example.demo.services.ImagenUrlHelper;
import com.example.demo.services.PasswordPolicy;
import com.mongodb.client.result.DeleteResult;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sembrador de datos de demostración para la feria/expo de CULTIVUS.
 *
 * <p>Se activa solo con variables de entorno y nunca se ejecuta por defecto:</p>
 * <ul>
 *   <li>{@code SEED_DEMO_DATA=true}: siembra usuarios, ofertas, compras, pedidos, envíos,
 *       billeteras, retiros y reseñas ficticias.</li>
 *   <li>{@code SEED_DEMO_CLEANUP=true}: elimina únicamente los registros marcados como demo.</li>
 *   <li>{@code SEED_DEMO_PASSWORD=...}: contraseña común de las cuentas demo. No se registra
 *       en logs ni se escribe en el repositorio.</li>
 * </ul>
 *
 * <p>Marcadores de seguridad: correos {@code @demo.cultivus.co}, referencias de pago
 * {@code DEMO-...} y transacciones {@code DEMO-TX-...}. Nunca toca {@code admin1@demo.com},
 * {@code transportista@demo.com} ni datos sin marcador.</p>
 *
 * <p>El sembrado corre en un hilo daemon y captura cualquier error para que el arranque de la
 * aplicación nunca se bloquee ni falle por culpa de los datos de demostración.</p>
 */
@Component
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String DOMINIO_DEMO = "@demo.cultivus.co";
    private static final String PREFIJO_REFERENCIA = "DEMO-";
    private static final String PREFIJO_TRANSACCION = "DEMO-TX-";

    private static final String EMAIL_ADMIN = "admin@demo.cultivus.co";
    private static final String EMAIL_VENDEDOR = "vendedor@demo.cultivus.co";
    private static final String EMAIL_COMPRADOR = "comprador@demo.cultivus.co";
    private static final String EMAIL_TRANSPORTISTA = "transportista@demo.cultivus.co";

    private static final int VENDEDORES_REGULARES = 40;
    private static final int COMPRADORES_REGULARES = 30;
    private static final int TRANSPORTISTAS_REGULARES = 20;
    private static final int OFERTAS_POR_VENDEDOR = 6;
    private static final int OFERTAS_VENDEDOR_DESTACADO = 14;
    private static final int COMPRAS_DEMO = 40;
    private static final int PEDIDOS_DEMO = 50;

    private static final long SEMILLA_ALEATORIA = 20260501L;

    private static final String[] NOMBRES_VENDEDORES = {
            "María Fernanda Ospina", "José Luis Payares", "Ana Milena Torres", "Carlos Andrés Buelvas",
            "Rosa Elena Martínez", "Jhon Jairo Padilla", "Yeimy Paola Castro", "Luis Alberto Mercado",
            "Diana Carolina Pérez", "Alberto José Guerra", "Katherine Romero", "Edwin Alfonso Díaz",
            "Sandra Milena Álvarez", "Rafael Antonio Meza", "Lina Marcela Valdez", "Óscar Iván Salgado",
            "Marta Cecilia Julio", "Pedro Nel Barrios", "Yuranis Esther Acosta", "Wilson de Jesús Ruiz",
            "Claudia Patricia Fontalvo", "Álvaro Enrique Rebolledo", "Nubia Esther Tovar", "Fredy Manuel Orozco",
            "Luz Adriana Herrera", "Jorge Eliécer Muñoz", "Daniela Sofía Lozano", "Miguel Ángel Caicedo",
            "Esperanza del Carmen Ruiz", "Ramiro Antonio Villalba", "Sergio Andrés Contreras", "Brenda Julieth Sierra",
            "Hernán Darío Palencia", "Mónica Isabel Arrieta", "Elkin Manuel Rojas", "Yuliana Andrea Barreto",
            "Rafael Enrique Mendoza", "Carmen Alicia Pacheco", "Víctor Manuel Cuadrado", "Isabel Cristina Narváez"
    };

    private static final String[] NOMBRES_COMPRADORES = {
            "Andrés Felipe Martínez", "Paula Andrea Gómez", "Julián David Contreras", "Sara Lucía Herrera",
            "Mauricio Andrés Ospina", "Valentina Restrepo", "Nicolás Esteban Cárdenas", "Laura Sofía Mendoza",
            "Diego Armando Ríos", "Camila Andrea Torres", "Sebastián José Blanco", "Mariana Cadena",
            "Felipe de Jesús Núñez", "Daniela María Salas", "Juan Camilo Vergara", "María José Beltrán",
            "Ricardo Alfonso Mejía", "Natalia Isabel Muñoz", "Óscar Javier Pineda", "Tatiana Liceth Castro",
            "Eduardo Enrique Daza", "Lorena Patricia Vargas", "Álvaro José Espitia", "Carolina Andrea Peña",
            "Joaquín Emilio Uribe", "Melissa Andrea Prada", "Cristian Camilo Solano", "Adriana del Carmen Lorduy",
            "Emilio Andrés Barrios", "Verónica Isabel Manrique"
    };

    private static final String[] NOMBRES_TRANSPORTISTAS = {
            "Jorge Luis Pineda", "Carlos Mario Martínez", "Alfonso Rafael Escobar", "Dairo José Cárdenas",
            "Yeison Alberto Lora", "Wilmar de Jesús Borges", "Emiro Manuel Cassiani", "Rubén Darío Pérez",
            "Héctor Julio Contreras", "Leonardo Andrés Támara", "Álvaro José Barrios", "Rafael Segundo Núñez",
            "Misael Antonio Ríos", "Eder Enrique Padilla", "Vladimir de la Ossa", "Jhon Fredy Ochoa",
            "Arley Manuel Salgado", "Robinson José Marimón", "Yeifer David Cuadrado", "Samuel Elías Arrieta"
    };

    private static final String[] APELLIDOS_NEGOCIO = {
            "Ospina", "Payares", "Torres", "Buelvas", "Martínez", "Padilla", "Castro", "Mercado",
            "Pérez", "Guerra", "Romero", "Díaz", "Álvarez", "Meza", "Valdez", "Salgado",
            "Julio", "Barrios", "Acosta", "Ruiz", "Fontalvo", "Rebolledo", "Tovar", "Orozco",
            "Herrera", "Muñoz", "Lozano", "Caicedo", "Villalba", "Contreras", "Sierra", "Palencia",
            "Arrieta", "Rojas", "Barreto", "Mendoza", "Pacheco", "Cuadrado", "Narváez", "Salas"
    };

    private static final String[] PREFIJOS_NEGOCIO = {
            "Finca", "Agropecuaria", "Cooperativa", "Distribuidora", "Hacienda", "Granja", "Asociación", "Mercado"
    };

    private static final String[] CIUDADES_BOLIVAR = {
            "Cartagena", "Turbaco", "Arjona", "Calamar",
            "El Carmen de Bolívar", "San Jacinto", "San Juan Nepomuceno", "María la Baja",
            "Magangué", "Achí", "Pinillos", "San Jacinto del Cauca",
            "Mompox", "Cicuco", "Talaigua Nuevo", "Margarita",
            "El Peñón", "San Martín de Loba", "Barranco de Loba", "Regidor",
            "Morales", "Santa Rosa del Sur", "Simití", "Cantagallo"
    };

    private static final String[] CIUDADES_ATLANTICO = {
            "Barranquilla", "Soledad", "Malambo", "Sabanalarga", "Baranoa", "Puerto Colombia", "Galapa"
    };

    private static final String[] CIUDADES_SUCRE = {
            "Sincelejo", "Corozal", "San Marcos", "Tolú", "Coveñas", "Sampués"
    };

    private static final String[] CIUDADES_ENTREGA = {
            "Cartagena", "Turbaco", "Magangué", "Mompox", "El Carmen de Bolívar", "San Jacinto",
            "Achí", "Cicuco", "El Peñón", "Morales", "Arjona", "Calamar",
            "Sincelejo", "Barranquilla", "San Juan Nepomuceno", "Santa Rosa del Sur"
    };

    private static final String[] CATEGORIAS_VENDEDOR = {
            // Distribución proporcional al catálogo (57 productos) para que la mayoría
            // de vendedores tenga ofertas de su especialidad.
            "Frutas", "Frutas", "Frutas", "Frutas", "Frutas", "Frutas", "Frutas",
            "Verduras", "Verduras", "Verduras", "Verduras", "Verduras", "Verduras", "Verduras",
            "Granos y Cereales", "Granos y Cereales", "Granos y Cereales", "Granos y Cereales",
            "Granos y Cereales",
            "Café y Cacao", "Café y Cacao", "Café y Cacao", "Café y Cacao", "Café y Cacao",
            "Lácteos", "Lácteos", "Lácteos", "Lácteos",
            "Carnes y Aves", "Carnes y Aves", "Carnes y Aves", "Carnes y Aves",
            "Condimentos", "Condimentos", "Condimentos", "Condimentos",
            "Miel", "Miel", "Miel", "Miel"
    };

    private static final String[] BANCOS_EMPRESA = {
            "Bancolombia", "Davivienda", "Banco de Bogotá", "Banco Agrario de Colombia",
            "BBVA Colombia", "Banco Caja Social", "Nu Colombia"
    };

    private static final Map<String, Double> PRECIOS_BASE = new HashMap<>();

    static {
        // Verduras (COP por la unidad por defecto del catálogo)
        PRECIOS_BASE.put("Yuca", 2_500.0);
        PRECIOS_BASE.put("Ñame espino", 4_000.0);
        PRECIOS_BASE.put("Ñame diamante", 4_500.0);
        PRECIOS_BASE.put("Batata", 3_000.0);
        PRECIOS_BASE.put("Ahuyama", 2_200.0);
        PRECIOS_BASE.put("Berenjena", 2_800.0);
        PRECIOS_BASE.put("Tomate", 3_200.0);
        PRECIOS_BASE.put("Pimentón", 4_500.0);
        PRECIOS_BASE.put("Habichuela", 4_000.0);
        PRECIOS_BASE.put("Quimbombó", 3_800.0);
        PRECIOS_BASE.put("Pepino cohombro", 2_500.0);
        PRECIOS_BASE.put("Mazorca tierna", 1_200.0);

        // Frutas
        PRECIOS_BASE.put("Plátano hartón", 1_500.0);
        PRECIOS_BASE.put("Banano", 3_000.0);
        PRECIOS_BASE.put("Mango de azúcar", 4_000.0);
        PRECIOS_BASE.put("Mango Tommy", 4_500.0);
        PRECIOS_BASE.put("Aguacate", 7_000.0);
        PRECIOS_BASE.put("Melón", 6_000.0);
        PRECIOS_BASE.put("Patilla", 8_000.0);
        PRECIOS_BASE.put("Ciruela costeña", 5_000.0);
        PRECIOS_BASE.put("Níspero", 6_000.0);
        PRECIOS_BASE.put("Limón tahití", 4_000.0);
        PRECIOS_BASE.put("Papaya", 7_000.0);
        PRECIOS_BASE.put("Guayaba", 3_500.0);
        PRECIOS_BASE.put("Coco", 3_000.0);
        PRECIOS_BASE.put("Maracuyá", 5_000.0);

        // Granos y cereales
        PRECIOS_BASE.put("Maíz seco (grano)", 3_000.0);
        PRECIOS_BASE.put("Arroz paddy", 2_800.0);
        PRECIOS_BASE.put("Arroz blanco", 4_000.0);
        PRECIOS_BASE.put("Fríjol cabecita negra", 7_000.0);
        PRECIOS_BASE.put("Fríjol guandul", 8_000.0);
        PRECIOS_BASE.put("Ajonjolí", 9_000.0);
        PRECIOS_BASE.put("Maní", 8_000.0);

        // Café y cacao
        PRECIOS_BASE.put("Café pergamino seco", 12_000.0);
        PRECIOS_BASE.put("Café tostado y molido", 15_000.0);
        PRECIOS_BASE.put("Cacao en grano seco", 14_000.0);
        PRECIOS_BASE.put("Chocolate de mesa artesanal", 12_000.0);

        // Lácteos
        PRECIOS_BASE.put("Queso costeño", 14_000.0);
        PRECIOS_BASE.put("Suero costeño", 6_000.0);
        PRECIOS_BASE.put("Leche fresca", 4_500.0);
        PRECIOS_BASE.put("Cuajada", 10_000.0);
        PRECIOS_BASE.put("Mantequilla costeña", 12_000.0);

        // Carnes y aves
        PRECIOS_BASE.put("Pollo criollo", 12_000.0);
        PRECIOS_BASE.put("Huevos criollos", 12_000.0);
        PRECIOS_BASE.put("Carne de res", 22_000.0);
        PRECIOS_BASE.put("Carne de cerdo", 18_000.0);
        PRECIOS_BASE.put("Pescado de río", 16_000.0);
        PRECIOS_BASE.put("Mojarra roja", 14_000.0);
        PRECIOS_BASE.put("Bocachico", 15_000.0);

        // Condimentos
        PRECIOS_BASE.put("Ají dulce", 5_000.0);
        PRECIOS_BASE.put("Ají picante", 6_000.0);
        PRECIOS_BASE.put("Cilantro", 2_000.0);
        PRECIOS_BASE.put("Cebolla larga", 2_500.0);
        PRECIOS_BASE.put("Ajo", 9_000.0);
        PRECIOS_BASE.put("Orégano", 2_000.0);
        PRECIOS_BASE.put("Panela", 3_500.0);

        // Miel
        PRECIOS_BASE.put("Miel de abeja", 25_000.0);
    }

    private static final Map<String, String> DEPARTAMENTO_POR_CIUDAD = new HashMap<>();

    static {
        for (String c : CIUDADES_BOLIVAR) DEPARTAMENTO_POR_CIUDAD.put(c, "Bolívar");
        for (String c : CIUDADES_ATLANTICO) DEPARTAMENTO_POR_CIUDAD.put(c, "Atlántico");
        for (String c : CIUDADES_SUCRE) DEPARTAMENTO_POR_CIUDAD.put(c, "Sucre");
    }

    private static final String[] COMENTARIOS_PRODUCTO = {
            "Excelente producto, llegó fresquecito a Cartagena.",
            "Muy buena calidad, se nota el trabajo del campo.",
            "El pedido llegó completo y bien empacado.",
            "Todo fresco y con buen sabor, volveré a comprar.",
            "Cumplió con lo prometido, buen precio.",
            "La entrega fue puntual y el producto muy bueno.",
            "Me encantó, se siente el sabor de la costa.",
            "Buen producto, aunque la entrega demoró un poquito.",
            "Muy amable el vendedor y el producto de primera.",
            "Llegó bien, fresco y en la cantidad exacta.",
            "Recomendado, buena atención y calidad.",
            "Se nota que es cosecha reciente, muy rico todo."
    };

    private static final String[] COMENTARIOS_VENDEDOR = {
            "Vendedor muy cumplido, empacó todo con cuidado.",
            "Excelente atención, respondió rápido los mensajes.",
            "La compra llegó completa y a tiempo.",
            "Persona seria y responsable, recomendada.",
            "Muy buena comunicación durante toda la entrega.",
            "Cumplió con lo acordado, volveré a comprarle.",
            "El producto llegó tal como se describía.",
            "Atención cercana y amable, gracias.",
            "Buen vendedor, aunque la entrega se demoró un poco.",
            "Todo bien, muy organizado con el despacho.",
            "Recomendado por la calidad y el servicio.",
            "Una experiencia muy buena, seguiremos comprando."
    };

    private final Environment environment;
    private final UsuarioRepository usuarioRepository;
    private final OfertaRepository ofertaRepository;
    private final ProductoCatalogoRepository productoCatalogoRepository;
    private final CompraRepository compraRepository;
    private final PedidoRepository pedidoRepository;
    private final EnvioRepository envioRepository;
    private final ResenaRepository resenaRepository;
    private final ResenaVendedorRepository resenaVendedorRepository;
    private final SolicitudRetiroRepository solicitudRetiroRepository;
    private final BilleteraService billeteraService;
    private final BilleteraAlmacen billeteraAlmacen;
    private final CalculoEnvioService calculoEnvioService;
    private final CatalogoInicializador catalogoInicializador;
    private final AppConfigService configService;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    private final Random random = new Random(SEMILLA_ALEATORIA);

    public DemoDataSeeder(Environment environment,
                          UsuarioRepository usuarioRepository,
                          OfertaRepository ofertaRepository,
                          ProductoCatalogoRepository productoCatalogoRepository,
                          CompraRepository compraRepository,
                          PedidoRepository pedidoRepository,
                          EnvioRepository envioRepository,
                          ResenaRepository resenaRepository,
                          ResenaVendedorRepository resenaVendedorRepository,
                          SolicitudRetiroRepository solicitudRetiroRepository,
                          BilleteraService billeteraService,
                          BilleteraAlmacen billeteraAlmacen,
                          CalculoEnvioService calculoEnvioService,
                          CatalogoInicializador catalogoInicializador,
                          AppConfigService configService,
                          PasswordEncoder passwordEncoder,
                          MongoTemplate mongoTemplate) {
        this.environment = environment;
        this.usuarioRepository = usuarioRepository;
        this.ofertaRepository = ofertaRepository;
        this.productoCatalogoRepository = productoCatalogoRepository;
        this.compraRepository = compraRepository;
        this.pedidoRepository = pedidoRepository;
        this.envioRepository = envioRepository;
        this.resenaRepository = resenaRepository;
        this.resenaVendedorRepository = resenaVendedorRepository;
        this.solicitudRetiroRepository = solicitudRetiroRepository;
        this.billeteraService = billeteraService;
        this.billeteraAlmacen = billeteraAlmacen;
        this.calculoEnvioService = calculoEnvioService;
        this.catalogoInicializador = catalogoInicializador;
        this.configService = configService;
        this.passwordEncoder = passwordEncoder;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        final boolean sembrar = bandera("SEED_DEMO_DATA");
        final boolean limpiar = bandera("SEED_DEMO_CLEANUP");

        if (!sembrar && !limpiar) {
            return;
        }

        final String contrasena = environment.getProperty("SEED_DEMO_PASSWORD", "");
        if (sembrar) {
            if (contrasena == null || contrasena.isBlank()) {
                log.error("❌ SEED_DEMO_DATA=true pero falta SEED_DEMO_PASSWORD. "
                        + "No se generarán datos demo y la aplicación continuará arrancando normalmente.");
                return;
            }
            String errorPolitica = PasswordPolicy.validar(contrasena);
            if (errorPolitica != null) {
                log.error("❌ SEED_DEMO_PASSWORD no cumple la política de contraseñas: {}. "
                        + "No se generarán datos demo.", errorPolitica);
                return;
            }
        }

        Thread hilo = new Thread(() -> {
            try {
                if (limpiar) {
                    limpiarDatosDemo();
                }
                if (sembrar) {
                    sembrarDatosDemo(contrasena);
                }
            } catch (Throwable t) {
                log.error("❌ El sembrador de datos demo falló; la aplicación sigue funcionando. "
                        + "Detalle: {}", t.getMessage(), t);
            }
        }, "cultivus-demo-data-seeder");
        hilo.setDaemon(true);
        hilo.start();

        log.info("🧵 Sembrador demo lanzado en segundo plano (data={}, cleanup={}).",
                sembrar, limpiar);
    }

    private boolean bandera(String clave) {
        String valor = environment.getProperty(clave);
        return valor != null && ("true".equalsIgnoreCase(valor.trim()) || "1".equals(valor.trim()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Limpieza segura
    // ─────────────────────────────────────────────────────────────────────────

    private void limpiarDatosDemo() {
        long inicio = System.currentTimeMillis();
        log.info("🧹 Iniciando limpieza de datos demo (solo marcadores @demo.cultivus.co / DEMO- / DEMO-TX-)...");

        List<Usuario> usuariosDemo = usuarioRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).endsWith(DOMINIO_DEMO))
                .collect(Collectors.toList());

        Set<String> idsUsuarios = usuariosDemo.stream().map(Usuario::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> idsVendedores = usuariosDemo.stream()
                .filter(u -> u.hasRole(Role.VENDEDOR))
                .map(Usuario::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> idsTransportistas = usuariosDemo.stream()
                .filter(u -> u.hasRole(Role.TRANSPORTISTA))
                .map(Usuario::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Compra> comprasDemo = compraRepository.findAll().stream()
                .filter(c -> c.getReferenciaPago() != null && c.getReferenciaPago().startsWith(PREFIJO_REFERENCIA))
                .collect(Collectors.toList());
        Set<String> idsCompras = comprasDemo.stream().map(Compra::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Pedido> pedidosDemo = pedidoRepository.findAll().stream()
                .filter(p -> (p.getPago() != null && p.getPago().getReferencia() != null
                        && p.getPago().getReferencia().startsWith(PREFIJO_REFERENCIA))
                        || (p.getCompraId() != null && idsCompras.contains(p.getCompraId()))
                        || (p.getComprador() != null && idsUsuarios.contains(p.getComprador().getId()))
                        || (p.getVendedor() != null && idsVendedores.contains(p.getVendedor().getId())))
                .collect(Collectors.toList());
        Set<String> idsPedidos = pedidosDemo.stream().map(Pedido::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        long borrados = 0;

        // 1. Reseñas (producto y vendedor)
        borrados += borrarPorUsuarioOPedido("resenas", idsUsuarios, idsPedidos, Resena.class);
        borrados += borrarPorUsuarioOPedido("resenas_vendedor", idsUsuarios, idsPedidos, ResenaVendedor.class);

        // 2. Notificaciones
        borrados += borrar("notificaciones", "usuario_id", idsUsuarios);

        // 3. Movimientos de billetera
        borrados += borrar("movimientos_billetera", "usuario_id", idsUsuarios);

        // 4. Billeteras
        borrados += borrar("billeteras", "usuario_id", idsUsuarios);

        // 5. Solicitudes de retiro
        borrados += borrar("solicitudes_retiro", "usuario_id", idsUsuarios);

        // 6. Envíos (por compra demo o por parada de pedido demo)
        long enviosBorrados = 0;
        if (!idsCompras.isEmpty()) {
            DeleteResult r = mongoTemplate.remove(new Query(Criteria.where("compra_id").in(idsCompras)), Envio.class);
            enviosBorrados += r.getDeletedCount();
        }
        if (!idsPedidos.isEmpty()) {
            DeleteResult r = mongoTemplate.remove(new Query(Criteria.where("paradas.pedidoId").in(idsPedidos)), Envio.class);
            enviosBorrados += r.getDeletedCount();
        }
        if (enviosBorrados > 0) {
            log.info("🧹 envios: {} registro(s) demo eliminado(s)", enviosBorrados);
            borrados += enviosBorrados;
        }

        // 7. Pedidos
        borrados += borrar("pedidos", "_id", idsPedidos);

        // 8. Compras
        borrados += borrar("compras", "_id", idsCompras);

        // 9. Ofertas de vendedores demo
        borrados += borrarOfertasDemo(idsVendedores);

        // 10. Usuarios demo
        borrados += borrar("usuarios", "_id", idsUsuarios);

        try {
            catalogoInicializador.recalcularMetricasProductos();
        } catch (Exception e) {
            log.warn("⚠️ No se pudieron recalcular las métricas del catálogo tras la limpieza: {}", e.getMessage());
        }

        log.info("✅ Limpieza demo finalizada: usuarios={}, vendedores={}, transportistas={}, compras={}, pedidos={}, "
                        + "registros borrados={}, tiempo={} ms",
                idsUsuarios.size(), idsVendedores.size(), idsTransportistas.size(), idsCompras.size(), idsPedidos.size(),
                borrados, System.currentTimeMillis() - inicio);
    }

    private long borrar(String coleccion, String campo, Set<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return 0;
        }
        DeleteResult resultado = mongoTemplate.remove(new Query(Criteria.where(campo).in(valores)), coleccion);
        long n = resultado.getDeletedCount();
        if (n > 0) {
            log.info("🧹 {}: {} registro(s) demo eliminado(s)", coleccion, n);
        }
        return n;
    }

    /**
     * Las ofertas guardan el id del vendedor dentro del snapshot embebido como {@code vendedor._id}
     * (Spring Data mapea la propiedad {@code id} a {@code _id}). Se consultan tanto ObjectId como
     * cadena para cubrir documentos creados por distintas versiones.
     */
    private long borrarOfertasDemo(Set<String> idsVendedores) {
        if (idsVendedores == null || idsVendedores.isEmpty()) {
            return 0;
        }
        long total = 0;
        List<ObjectId> objectIds = idsVendedores.stream()
                .filter(id -> id != null && id.matches("^[0-9a-fA-F]{24}$"))
                .map(ObjectId::new)
                .collect(Collectors.toList());
        if (!objectIds.isEmpty()) {
            total += mongoTemplate.remove(new Query(Criteria.where("vendedor._id").in(objectIds)), "ofertas")
                    .getDeletedCount();
        }
        total += mongoTemplate.remove(new Query(Criteria.where("vendedor._id").in(idsVendedores)), "ofertas")
                .getDeletedCount();
        total += mongoTemplate.remove(new Query(Criteria.where("vendedor.id").in(idsVendedores)), "ofertas")
                .getDeletedCount();
        if (total > 0) {
            log.info("🧹 ofertas: {} registro(s) demo eliminado(s)", total);
        }
        return total;
    }

    private long borrarPorUsuarioOPedido(String coleccion, Set<String> idsUsuarios, Set<String> idsPedidos,
                                         Class<?> entidad) {
        long total = 0;
        if (idsUsuarios != null && !idsUsuarios.isEmpty()) {
            DeleteResult r = mongoTemplate.remove(new Query(Criteria.where("usuario_id").in(idsUsuarios)), entidad);
            total += r.getDeletedCount();
        }
        if (idsPedidos != null && !idsPedidos.isEmpty()) {
            DeleteResult r = mongoTemplate.remove(new Query(Criteria.where("pedido_id").in(idsPedidos)), entidad);
            total += r.getDeletedCount();
        }
        if (total > 0) {
            log.info("🧹 {}: {} registro(s) demo eliminado(s)", coleccion, total);
        }
        return total;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Siembra
    // ─────────────────────────────────────────────────────────────────────────

    private void sembrarDatosDemo(String contrasena) {
        long inicio = System.currentTimeMillis();

        boolean yaExiste = usuarioRepository.findAll().stream()
                .anyMatch(u -> u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).endsWith(DOMINIO_DEMO));
        if (yaExiste) {
            log.info("ℹ️ Ya existen datos demo ({}). No se duplican. "
                    + "Para regenerarlos usa SEED_DEMO_CLEANUP=true y luego SEED_DEMO_DATA=true.", DOMINIO_DEMO);
            return;
        }

        List<ProductoCatalogo> productos = new ArrayList<>(productoCatalogoRepository.findAll());
        if (productos.isEmpty()) {
            throw new IllegalStateException("El catálogo está vacío: DataLoader/CatalogoInicializador no sembró productos.");
        }
        productos.sort(Comparator.comparing(ProductoCatalogo::getNombre, String.CASE_INSENSITIVE_ORDER));

        log.info("🌱 Sembrando datos demo con {} productos de catálogo y semilla aleatoria fija {}...",
                productos.size(), SEMILLA_ALEATORIA);

        String hash = passwordEncoder.encode(contrasena);

        // 1. Usuarios
        List<Usuario> vendedores = new ArrayList<>();
        List<Usuario> compradores = new ArrayList<>();
        List<Usuario> transportistas = new ArrayList<>();

        Usuario vendedorDestacado = usuarioRepository.save(crearVendedorDestacado(hash));
        vendedores.add(vendedorDestacado);

        for (int i = 0; i < VENDEDORES_REGULARES; i++) {
            Usuario v = crearVendedorRegular(i, hash);
            v = usuarioRepository.save(v);
            vendedores.add(v);
        }

        Usuario compradorDestacado = crearCompradorDestacado(hash);
        compradorDestacado = usuarioRepository.save(compradorDestacado);
        compradores.add(compradorDestacado);

        for (int i = 0; i < COMPRADORES_REGULARES; i++) {
            Usuario c = crearCompradorRegular(i, hash);
            c = usuarioRepository.save(c);
            compradores.add(c);
        }

        Usuario transportistaDestacado = crearTransportistaDestacado(hash);
        transportistaDestacado = usuarioRepository.save(transportistaDestacado);
        transportistas.add(transportistaDestacado);

        for (int i = 0; i < TRANSPORTISTAS_REGULARES; i++) {
            Usuario t = crearTransportistaRegular(i, hash);
            t = usuarioRepository.save(t);
            transportistas.add(t);
        }

        log.warn("⚠️ Se creará el administrador demo {}. SecurityConfig aísla /admin/**: esta cuenta no puede "
                + "comprar, vender ni entrar a /usuario/**. Es solo para la demo, nunca para producción.", EMAIL_ADMIN);
        Usuario admin = crearAdmin(hash);
        admin = usuarioRepository.save(admin);

        log.info("👥 Usuarios demo creados: {} vendedores, {} compradores, {} transportistas, 1 admin.",
                vendedores.size(), compradores.size(), transportistas.size());

        // 2. Ofertas
        Map<String, List<OfertaVendedor>> ofertasPorVendedor = new LinkedHashMap<>();
        Map<String, Set<String>> productosPorVendedor = new HashMap<>();
        Map<String, OfertaVendedor> ofertasPorId = new LinkedHashMap<>();
        List<OfertaVendedor> todasLasOfertas = new ArrayList<>();

        for (Usuario v : vendedores) {
            ofertasPorVendedor.put(v.getId(), new ArrayList<>());
            productosPorVendedor.put(v.getId(), new LinkedHashSet<>());
        }

        List<Usuario> vendedoresRegulares = vendedores.stream()
                .filter(v -> !v.getId().equals(vendedorDestacado.getId()))
                .collect(Collectors.toList());

        // Cobertura mínima: 3 vendedores especializados por cada producto del catálogo.
        // El vendedor destacado se reserva para frutas, así su vitrina queda coherente.
        Map<String, List<Usuario>> vendedoresPorCategoria = new LinkedHashMap<>();
        for (Usuario v : vendedoresRegulares) {
            String tipo = v.getPerfilVendedor() != null ? v.getPerfilVendedor().getTipoProductos() : null;
            if (tipo != null) {
                vendedoresPorCategoria.computeIfAbsent(tipo, k -> new ArrayList<>()).add(v);
            }
        }
        for (int p = 0; p < productos.size(); p++) {
            ProductoCatalogo producto = productos.get(p);
            String categoriaProducto = producto.getCategoria() != null ? producto.getCategoria().getNombre() : null;
            List<Usuario> candidatos = categoriaProducto != null
                    ? vendedoresPorCategoria.getOrDefault(categoriaProducto, vendedoresRegulares)
                    : vendedoresRegulares;
            if (candidatos.size() < 3) {
                candidatos = vendedoresRegulares;
            }
            for (int k = 0; k < 3; k++) {
                Usuario vendedor = candidatos.get((p * 3 + k) % candidatos.size());
                agregarOferta(vendedor, producto, ofertasPorVendedor, productosPorVendedor,
                        todasLasOfertas, ofertasPorId);
            }
        }

        // Completar cada vendedor regular hasta 6 o 7 ofertas, priorizando su especialidad.
        for (int i = 0; i < vendedores.size(); i++) {
            Usuario vendedor = vendedores.get(i);
            if (vendedor == vendedorDestacado) {
                continue;
            }
            int objetivo = i < 20 ? 7 : OFERTAS_POR_VENDEDOR;
            int intentos = 0;
            while (productosPorVendedor.get(vendedor.getId()).size() < objetivo && intentos < 300) {
                ProductoCatalogo producto = elegirProductoParaVendedor(vendedor, productos,
                        productosPorVendedor.get(vendedor.getId()), intentos);
                if (producto == null) {
                    break;
                }
                agregarOferta(vendedor, producto, ofertasPorVendedor, productosPorVendedor,
                        todasLasOfertas, ofertasPorId);
                intentos++;
            }
        }

        // Vendedor destacado: 14 frutas, una por producto de la categoría Frutas.
        List<ProductoCatalogo> frutas = productos.stream()
                .filter(p -> p.getCategoria() != null && "Frutas".equalsIgnoreCase(p.getCategoria().getNombre()))
                .collect(Collectors.toList());
        if (frutas.isEmpty()) {
            frutas = productos.subList(0, Math.min(OFERTAS_VENDEDOR_DESTACADO, productos.size()));
        }
        for (ProductoCatalogo fruta : frutas) {
            if (ofertasPorVendedor.get(vendedorDestacado.getId()).size() >= OFERTAS_VENDEDOR_DESTACADO) {
                break;
            }
            agregarOferta(vendedorDestacado, fruta, ofertasPorVendedor, productosPorVendedor,
                    todasLasOfertas, ofertasPorId);
        }

        // 3. Moderación: ~5% PENDIENTE, siempre que el producto conserve >= 3 publicadas.
        int pendientesRegulares = marcarPendientes(todasLasOfertas, ofertasPorVendedor.get(vendedorDestacado.getId()), 12, 2);

        long publicadas = todasLasOfertas.stream()
                .filter(o -> o.getEstadoPublicacion() == EstadoPublicacion.PUBLICADO).count();
        log.info("🛒 Ofertas demo: {} totales ({} publicadas, {} pendientes).",
                todasLasOfertas.size(), publicadas, todasLasOfertas.size() - publicadas);

        // 4. Compras y pedidos
        List<CompraSpec> specs = construirComprasSpecs();
        Map<Integer, List<EstadoPedido>> planEstados = planEstados();

        List<Compra> compras = new ArrayList<>();
        List<Pedido> pedidos = new ArrayList<>();
        Map<String, List<Pedido>> pedidosPorCompra = new LinkedHashMap<>();
        Map<String, Double> stockUsado = new HashMap<>();
        Map<String, Integer> entregasPorTransportista = new HashMap<>();

        double minimoCompra = Math.max(50_000.0, configService.obtenerValorDouble("COMPRA_MINIMA_GLOBAL", 50_000.0));
        double maximoCompra = Math.max(minimoCompra, configService.obtenerValorDouble("LIMITE_COMPRA_MAX", 5_000_000.0));

        int entregadosContador = 0;
        for (CompraSpec spec : specs) {
            List<EstadoPedido> estados = planEstados.get(spec.indice());
            if (estados == null || estados.isEmpty()) {
                throw new IllegalStateException("Falta el plan de estados de la compra demo " + spec.indice());
            }

            Usuario comprador = spec.showcaseBuyer() ? compradorDestacado : compradores.get(1 + (spec.indice() % COMPRADORES_REGULARES));
            String ciudad = spec.ciudad();
            String departamento = DEPARTAMENTO_POR_CIUDAD.getOrDefault(ciudad, "Bolívar");
            DireccionPedido direccion = new DireccionPedido(
                    direccionFicticia(spec.indice()), ciudad, departamento);
            direccion.setDestinatarioNombre(comprador.getNombre());
            direccion.setDestinatarioTelefono(telefonoFicticio(spec.indice()));

            List<Usuario> vendedoresCompra = elegirVendedores(spec, estados, vendedoresRegulares, vendedorDestacado);

            String referencia = PREFIJO_REFERENCIA + String.format(Locale.ROOT, "%010d", 1_000_000_000 + spec.indice());
            String transaccion = PREFIJO_TRANSACCION + String.format(Locale.ROOT, "%08d", 20_000_000 + spec.indice());

            LocalDateTime fechaBase = fechaBaseCompra(estados);
            Compra compra = new Compra(new DatosComprador(comprador.getId(), comprador.getNombre()), referencia, 0L, 0L);
            compra.setFecha(fechaBase);
            compra.setEstado(Compra.PENDIENTE);
            compra.setUpdatedAt(fechaBase);
            compra = compraRepository.save(compra);

            List<String> vendedoresIds = vendedoresCompra.stream().map(Usuario::getId).collect(Collectors.toList());
            CalculoEnvioService.ResultadoCalculoEnvio calculo = calculoEnvioService
                    .calcularTarifaParaVendedores(vendedoresIds, ciudad);

            List<Pedido> pedidosCompra = new ArrayList<>();

            for (int pos = 0; pos < vendedoresCompra.size(); pos++) {
                Usuario vendedor = vendedoresCompra.get(pos);
                EstadoPedido estado = estados.get(Math.min(pos, estados.size() - 1));
                List<ProductoPedido> items = crearItems(vendedor, ofertasPorVendedor, estado);

                long subtotalCentavos = items.stream()
                        .mapToLong(i -> Math.round((i.getSubtotal() != null ? i.getSubtotal() : 0.0) * 100.0))
                        .sum();

                LocalDateTime fechaPedido;
                LocalDateTime fechaEntrega = null;
                if (estado == EstadoPedido.ENTREGADO) {
                    entregadosContador++;
                    if (entregadosContador <= 20) {
                        fechaPedido = LocalDateTime.now().minusDays(46 + random.nextInt(12));
                        fechaEntrega = fechaPedido.plusDays(1 + random.nextInt(3));
                    } else {
                        fechaPedido = LocalDateTime.now().minusDays(2);
                        fechaEntrega = LocalDateTime.now().minusHours(6 + random.nextInt(10));
                    }
                } else {
                    fechaPedido = fechaParaEstado(estado);
                }

                DatosPago pago = new DatosPago("DEMO", subtotalCentavos / 100.0);
                pago.setReferencia(referencia);
                pago.setEstado(estado == EstadoPedido.PENDIENTE ? "PENDIENTE" : "COMPLETADO");
                pago.setFechaPago(fechaPedido);

                Pedido pedido = new Pedido(new DatosComprador(comprador.getId(), comprador.getNombre()),
                        snapshotVendedor(vendedor), direccion, items, pago);
                pedido.setCompraId(compra.getId());
                pedido.setEstadoEnum(estado);
                pedido.setSubtotalCentavos(subtotalCentavos);
                pedido.setEnvioCentavos(calculo.obtenerTarifaVendedor(vendedor.getId()));
                pedido.setTarifaEnvioCentavos(calculo.obtenerTarifaVendedor(vendedor.getId()));
                pedido.setStockDescontado(estado.esVentaConfirmada());
                pedido.setFechaPedido(fechaPedido);
                pedido.setCreatedAt(fechaPedido);
                pedido.setUpdatedAt(fechaEntrega != null ? fechaEntrega : fechaPedido);
                pedido.setFechaEntrega(fechaEntrega);

                for (CalculoEnvioService.DesgloseEnvioVendedor dg : calculo.desglose()) {
                    if (vendedor.getId().equals(dg.vendedorId())) {
                        pedido.setCiudadOrigenEnvio(dg.ciudadOrigen());
                        pedido.setCiudadDestinoEnvio(dg.ciudadDestino());
                        pedido.setZonaOrigenEnvio(dg.zonaOrigen());
                        pedido.setZonaDestinoEnvio(dg.zonaDestino());
                        break;
                    }
                }

                aplicarDetalleEstado(pedido, estado, fechaPedido, fechaEntrega);
                pedido.setPago(pago);

                pedido = pedidoRepository.save(pedido);
                pedidosCompra.add(pedido);
                pedidos.add(pedido);
            }

            asegurarMinimoCompra(pedidosCompra, minimoCompra);

            for (Pedido p : pedidosCompra) {
                if (p.getEstadoEnum() != null && p.getEstadoEnum().esVentaConfirmada()) {
                    for (ProductoPedido item : p.getItems()) {
                        stockUsado.merge(item.getProductoId(), item.getCantidad(), Double::sum);
                    }
                }
            }

            long subtotalCompra = pedidosCompra.stream()
                    .mapToLong(p -> p.getSubtotalCentavos() != null ? p.getSubtotalCentavos() : 0L).sum();
            long envioCompra = pedidosCompra.stream()
                    .mapToLong(p -> p.getEnvioCentavos() != null ? p.getEnvioCentavos() : 0L).sum();
            long totalCompra = subtotalCompra + envioCompra;

            if (totalCompra > Math.round(maximoCompra * 100.0)) {
                log.warn("⚠️ La compra demo {} supera LIMITE_COMPRA_MAX (${}). Se deja tal cual para revisión.",
                        referencia, totalCompra / 100.0);
            }

            compra.setSubtotalCentavos(subtotalCompra);
            compra.setEnvioCentavos(envioCompra);
            compra.setTotalCentavos(totalCompra);
            compra.setPedidoIds(pedidosCompra.stream().map(Pedido::getId).collect(Collectors.toList()));
            compra.setRequiereRevision(pedidosCompra.stream().anyMatch(Pedido::isEnRevision));

            boolean algunPagado = pedidosCompra.stream().anyMatch(p -> !p.isPendiente());
            compra.setEstado(estadoCompra(pedidosCompra));
            if (algunPagado) {
                compra.setTransaccionId(transaccion);
                compra.setFechaPago(pedidosCompra.stream()
                        .filter(p -> !p.isPendiente())
                        .map(Pedido::getFechaPedido)
                        .filter(java.util.Objects::nonNull)
                        .min(LocalDateTime::compareTo)
                        .orElse(fechaBase));
                for (Pedido p : pedidosCompra) {
                    if (p.getPago() != null) {
                        p.getPago().setTransaccionId(transaccion);
                    }
                }
            }
            compra.setUpdatedAt(LocalDateTime.now());
            compra = compraRepository.save(compra);
            compras.add(compra);
            pedidosPorCompra.put(compra.getId(), pedidosCompra);

            for (Pedido p : pedidosCompra) {
                if (p.getPago() != null) {
                    p.getPago().setMontoCentavosFirmado(totalCompra);
                    pedidoRepository.save(p);
                }
            }
        }

        log.info("📦 Compras demo: {} compras y {} pedidos.", compras.size(), pedidos.size());

        // Ajustar stock de las ofertas usadas (nunca negativo).
        for (Map.Entry<String, Double> uso : stockUsado.entrySet()) {
            OfertaVendedor oferta = ofertasPorId.get(uso.getKey());
            if (oferta != null) {
                double nuevoStock = Math.max(0.0, (oferta.getStock() != null ? oferta.getStock() : 0.0) - uso.getValue());
                oferta.setStock(nuevoStock);
                oferta.setUpdatedAt(LocalDateTime.now());
                ofertaRepository.save(oferta);
            }
        }

        // 5. Envíos
        List<Envio> envios = crearEnvios(compras, pedidosPorCompra, transportistas, transportistaDestacado,
                entregasPorTransportista);
        log.info("🚚 Envíos demo: {} (asignados={}, pendientes de asignación={}, entregados={}).",
                envios.size(),
                envios.stream().filter(e -> e.getTransportistaId() != null).count(),
                envios.stream().filter(e -> Envio.PENDIENTE_ASIGNACION.equals(e.getEstado())).count(),
                envios.stream().filter(e -> Envio.ENTREGADO.equals(e.getEstado())).count());

        // 6. Billeteras: abono de ventas, liberación de saldos antiguos y fletes.
        int ventanaMinutos = configService.obtenerValorInt("VENTANA_RECLAMOS_MINUTOS", 1440);
        LocalDateTime limiteLiberacion = LocalDateTime.now().minusMinutes(ventanaMinutos);

        for (Compra c : compras) {
            List<Pedido> aprobados = pedidosPorCompra.get(c.getId()).stream()
                    .filter(p -> !p.isPendiente() && !p.isCancelado() && !p.isEnRevision())
                    .collect(Collectors.toList());
            if (!aprobados.isEmpty()) {
                try {
                    billeteraService.registrarRepartoVentas(c, aprobados);
                } catch (Exception e) {
                    log.warn("⚠️ No se pudo abonar la venta de la compra {}: {}", c.getReferenciaPago(), e.getMessage());
                }
            }
        }

        for (Pedido p : pedidos) {
            if (p.isEntregado() && p.getFechaEntrega() != null && !p.getFechaEntrega().isAfter(limiteLiberacion)) {
                try {
                    billeteraService.liberarSaldoPedido(p);
                } catch (Exception e) {
                    log.warn("⚠️ No se pudo liberar el saldo del pedido {}: {}", p.getId(), e.getMessage());
                }
            }
        }

        for (Envio e : envios) {
            if (Envio.ENTREGADO.equals(e.getEstado()) && e.getTransportistaId() != null) {
                try {
                    billeteraService.registrarFleteEntregado(e);
                    if (e.getFechaEntrega() != null && !e.getFechaEntrega().isAfter(limiteLiberacion)) {
                        billeteraService.liberarSaldoEnvio(e);
                    }
                } catch (Exception ex) {
                    log.warn("⚠️ No se pudo abonar el flete del envío {}: {}", e.getId(), ex.getMessage());
                }
            }
        }

        // Actualizar total de entregas de transportistas.
        for (Map.Entry<String, Integer> entry : entregasPorTransportista.entrySet()) {
            usuarioRepository.findById(entry.getKey()).ifPresent(t -> {
                if (t.getPerfilTransportista() != null) {
                    int actuales = t.getPerfilTransportista().getTotalEntregas() != null
                            ? t.getPerfilTransportista().getTotalEntregas() : 0;
                    t.getPerfilTransportista().setTotalEntregas(actuales + entry.getValue());
                    usuarioRepository.save(t);
                }
            });
        }

        // 7. Solicitudes de retiro pendientes (~3)
        int retiros = crearRetirosPendientes(vendedores, vendedorDestacado);
        log.info("💰 Solicitudes de retiro demo pendientes: {}.", retiros);

        // 8. Reseñas solo sobre pedidos entregados.
        int resenas = crearResenas(pedidos, ofertasPorVendedor);
        log.info("⭐ Reseñas demo: {} (producto + vendedor).", resenas);

        // 9. Métricas del catálogo.
        catalogoInicializador.recalcularMetricasProductos();

        Map<String, Long> distribucion = pedidos.stream()
                .collect(Collectors.groupingBy(Pedido::getEstado, LinkedHashMap::new, Collectors.counting()));
        long billeterasConSaldo = 0;
        for (Usuario v : vendedores) {
            if (billeteraAlmacen.buscar(v.getId(), Billetera.Rol.VENDEDOR)
                    .map(b -> b.getSaldoDisponibleCentavos() + b.getSaldoRetencionCentavos() > 0)
                    .orElse(false)) {
                billeterasConSaldo++;
            }
        }

        log.info("🎉 Semilla demo completada en {} ms", System.currentTimeMillis() - inicio);
        log.info("   👥 usuarios={} (vendedores={}, compradores={}, transportistas={}, admin={})",
                1 + vendedores.size() + compradores.size() + transportistas.size(),
                vendedores.size(), compradores.size(), transportistas.size(), 1);
        log.info("   🛒 ofertas={} (publicadas={}, pendientes={})", todasLasOfertas.size(), publicadas,
                todasLasOfertas.size() - publicadas);
        log.info("   📦 compras={}, pedidos={} {}", compras.size(), pedidos.size(), distribucion);
        log.info("   🚚 envíos={}, ⭐ reseñas={}, 💰 retiros={}, billeteras con saldo={}",
                envios.size(), resenas, retiros, billeterasConSaldo);
        log.info("   🔐 La contraseña demo NO se imprime; se toma de SEED_DEMO_PASSWORD.");
        log.info("   ⚠️ Recuerda: los correos son @demo.cultivus.co y las referencias DEMO-/DEMO-TX-.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Usuarios
    // ─────────────────────────────────────────────────────────────────────────

    private Usuario crearVendedorDestacado(String hash) {
        Usuario u = new Usuario("María Fernanda Ospina", EMAIL_VENDEDOR, hash, Role.VENDEDOR);
        u.setUbicacion(new UbicacionUsuario("Cartagena", "Bolívar"));
        u.setCreatedAt(LocalDateTime.now().minusDays(240));
        u.setUltimaConexion(LocalDateTime.now().minusHours(2));
        u.setEmailVerificado(true);

        PerfilVendedor p = perfilVendedorBase("Frutas del Caribe", "3000009001",
                "Mercado de Bazurto, bodega 12", "Frutas frescas del Caribe colombiano, cosechadas en Bolívar.",
                "Frutas", true, 900);
        u.setPerfilVendedor(p);
        return u;
    }

    private Usuario crearVendedorRegular(int i, String hash) {
        String nombre = NOMBRES_VENDEDORES[i % NOMBRES_VENDEDORES.length];
        String email = String.format(Locale.ROOT, "vendedor%02d%s", i + 1, DOMINIO_DEMO);
        Usuario u = new Usuario(nombre, email, hash, Role.VENDEDOR);

        String ciudad;
        String departamento;
        if (i % 20 < 17) {
            ciudad = CIUDADES_BOLIVAR[(i * 3) % CIUDADES_BOLIVAR.length];
            departamento = "Bolívar";
        } else if (i % 20 < 19) {
            ciudad = CIUDADES_ATLANTICO[i % CIUDADES_ATLANTICO.length];
            departamento = "Atlántico";
        } else {
            ciudad = CIUDADES_SUCRE[i % CIUDADES_SUCRE.length];
            departamento = "Sucre";
        }

        u.setUbicacion(new UbicacionUsuario(ciudad, departamento));
        u.setCreatedAt(LocalDateTime.now().minusDays(90 + random.nextInt(200)));
        u.setUltimaConexion(LocalDateTime.now().minusDays(random.nextInt(15)));
        u.setEmailVerificado(true);

        String categoria = CATEGORIAS_VENDEDOR[i % CATEGORIAS_VENDEDOR.length];
        String razonSocial = PREFIJOS_NEGOCIO[i % PREFIJOS_NEGOCIO.length] + " "
                + APELLIDOS_NEGOCIO[i % APELLIDOS_NEGOCIO.length] + " " + (i + 1);
        String telefono = telefonoFicticio(i + 10);
        String direccion = "Vereda " + (char) ('A' + (i % 20)) + ", sector " + ((i * 7) % 40)
                + ", " + ciudad;
        String descripcion = "Productor local de " + categoria.toLowerCase(Locale.ROOT)
                + " en " + ciudad + ", con cosecha fresca y entrega directa.";

        // 1 de cada 8 vendedores queda sin verificar (para la bandeja del admin).
        boolean verificado = i % 8 != 0;

        PerfilVendedor p = perfilVendedorBase(razonSocial, telefono, direccion, descripcion,
                categoria, verificado, 1000 + i);
        u.setPerfilVendedor(p);
        return u;
    }

    private PerfilVendedor perfilVendedorBase(String razonSocial, String telefono, String direccion,
                                               String descripcion, String tipoProductos,
                                               boolean verificado, int indiceCuenta) {
        PerfilVendedor p = new PerfilVendedor();
        p.setRazonSocial(razonSocial);
        p.setTelefonoContacto(telefono);
        p.setDireccionNegocio(direccion);
        p.setDescripcionNegocio(descripcion);
        p.setTipoProductos(tipoProductos);
        p.setVerificado(verificado);
        p.setPermiteRecogidaEnTienda(true);
        CuentaBancaria cuenta = crearCuentaBancaria(indiceCuenta, razonSocial);
        p.setCuentaRetiro(cuenta);
        // Marcador seguro visible en el perfil; la cuenta estructurada conserva un número ficticio válido.
        p.setCuentaBancaria("0000-DEMO-" + String.format(Locale.ROOT, "%04d", indiceCuenta));
        return p;
    }

    private Usuario crearCompradorDestacado(String hash) {
        Usuario u = new Usuario("Andrés Felipe Martínez", EMAIL_COMPRADOR, hash, Role.COMPRADOR);
        u.setUbicacion(new UbicacionUsuario("Cartagena", "Bolívar"));
        u.setCreatedAt(LocalDateTime.now().minusDays(180));
        u.setUltimaConexion(LocalDateTime.now().minusMinutes(30));
        u.setEmailVerificado(true);
        return u;
    }

    private Usuario crearCompradorRegular(int i, String hash) {
        String nombre = NOMBRES_COMPRADORES[i % NOMBRES_COMPRADORES.length];
        String email = String.format(Locale.ROOT, "comprador%02d%s", i + 1, DOMINIO_DEMO);
        Usuario u = new Usuario(nombre, email, hash, Role.COMPRADOR);

        String ciudad;
        String departamento;
        if (i % 20 < 17) {
            ciudad = CIUDADES_BOLIVAR[(i * 5) % CIUDADES_BOLIVAR.length];
            departamento = "Bolívar";
        } else if (i % 20 < 19) {
            ciudad = CIUDADES_ATLANTICO[i % CIUDADES_ATLANTICO.length];
            departamento = "Atlántico";
        } else {
            ciudad = CIUDADES_SUCRE[i % CIUDADES_SUCRE.length];
            departamento = "Sucre";
        }

        u.setUbicacion(new UbicacionUsuario(ciudad, departamento));
        u.setCreatedAt(LocalDateTime.now().minusDays(30 + random.nextInt(180)));
        u.setUltimaConexion(LocalDateTime.now().minusDays(random.nextInt(10)));
        u.setEmailVerificado(true);
        return u;
    }

    private Usuario crearTransportistaDestacado(String hash) {
        Usuario u = new Usuario(NOMBRES_TRANSPORTISTAS[0], EMAIL_TRANSPORTISTA, hash, Role.TRANSPORTISTA);
        u.setUbicacion(new UbicacionUsuario("Cartagena", "Bolívar"));
        u.setCreatedAt(LocalDateTime.now().minusDays(220));
        u.setUltimaConexion(LocalDateTime.now().minusHours(1));
        u.setEmailVerificado(true);

        PerfilTransportista p = perfilTransportistaBase(0, "INDEPENDIENTE", "Cartagena", "Bolívar", true);
        p.setZonasCoberturaIds(new ArrayList<>(List.of(1, 2)));
        p.setZonasCobertura("Dique, Montes de María");
        p.setVerificado(true);
        p.setDisponible(true);
        u.setPerfilTransportista(p);
        return u;
    }

    private Usuario crearTransportistaRegular(int i, String hash) {
        String nombre = NOMBRES_TRANSPORTISTAS[(i + 1) % NOMBRES_TRANSPORTISTAS.length];
        String email = String.format(Locale.ROOT, "transportista%02d%s", i + 1, DOMINIO_DEMO);
        Usuario u = new Usuario(nombre, email, hash, Role.TRANSPORTISTA);
        u.setEmailVerificado(true);
        u.setCreatedAt(LocalDateTime.now().minusDays(60 + random.nextInt(220)));
        u.setUltimaConexion(LocalDateTime.now().minusDays(random.nextInt(12)));

        boolean empresa = i >= 10;
        String tipo = empresa ? PerfilTransportista.TIPO_EMPRESA : PerfilTransportista.TIPO_INDEPENDIENTE;
        String ciudad;
        String departamento;
        if (i % 10 < 8) {
            ciudad = CIUDADES_BOLIVAR[(i * 7) % CIUDADES_BOLIVAR.length];
            departamento = "Bolívar";
        } else if (i % 10 == 8) {
            ciudad = "Barranquilla";
            departamento = "Atlántico";
        } else {
            ciudad = "Sincelejo";
            departamento = "Sucre";
        }
        u.setUbicacion(new UbicacionUsuario(ciudad, departamento));

        boolean verificado = i % 8 != 0;
        PerfilTransportista p = perfilTransportistaBase(i, tipo, ciudad, departamento, verificado);

        List<Integer> zonas = new ArrayList<>();
        if (empresa && i % 3 == 0) {
            p.setTodasLasZonas(true);
            zonas.addAll(List.of(1, 2, 3, 4, 5, 6));
            p.setZonasCobertura("Todas las zonas de Bolívar");
        } else {
            int base = 1 + (i % 6);
            zonas.add(base);
            zonas.add(1 + ((base) % 6));
            zonas.add(1 + ((base + 1) % 6));
            p.setZonasCoberturaIds(zonas);
            p.setZonasCobertura(zonas.stream().map(com.example.demo.services.ZonasLogistica::nombreDe)
                    .filter(java.util.Objects::nonNull).collect(Collectors.joining(", ")));
        }
        u.setPerfilTransportista(p);
        return u;
    }

    private PerfilTransportista perfilTransportistaBase(int i, String tipo, String ciudad, String departamento,
                                                         boolean verificado) {
        boolean empresa = PerfilTransportista.TIPO_EMPRESA.equals(tipo);
        String[] vehiculos = {"Camioneta", "Furgón", "Camión", "Turbo", "Motocarro"};
        String tipoVehiculo = vehiculos[i % vehiculos.length];
        String placa = String.format(Locale.ROOT, "%s%03d", empresa ? "XYZ" : "ABC", 100 + i);
        String telefono = telefonoFicticio(i + 100);
        String empresaNombre = empresa
                ? "Transportes " + APELLIDOS_NEGOCIO[i % APELLIDOS_NEGOCIO.length] + " S.A.S."
                : "Independiente";
        String razonSocial = empresa ? empresaNombre : null;
        String nit = empresa ? "900" + String.format(Locale.ROOT, "%06d", 100000 + i) + "-1" : null;

        PerfilTransportista p = new PerfilTransportista(tipoVehiculo, placa,
                500.0 + (i % 8) * 250.0, "C" + (1 + i % 3), telefono, ciudad, departamento,
                "", i % 4 == 0, empresaNombre, BANCOS_EMPRESA[i % BANCOS_EMPRESA.length],
                "0000-DEMO-" + String.format(Locale.ROOT, "%04d", 2000 + i));
        p.setTipo(tipo);
        p.setEmpresa(empresaNombre);
        p.setRazonSocial(razonSocial);
        p.setNit(nit);
        p.setVerificado(verificado);
        p.setDisponible(true);
        p.setCalificacion(4.2 + random.nextDouble() * 0.8);
        p.setTotalEntregas(0);
        CuentaBancaria cuenta = crearCuentaBancaria(2000 + i, empresa ? empresaNombre : NOMBRES_TRANSPORTISTAS[i % NOMBRES_TRANSPORTISTAS.length]);
        p.setCuentaRetiro(cuenta);
        p.setCuentaBancaria("0000-DEMO-" + String.format(Locale.ROOT, "%04d", 2000 + i));
        return p;
    }

    private Usuario crearAdmin(String hash) {
        Usuario u = new Usuario("Administrador Demo Cultivus", EMAIL_ADMIN, hash, Role.ADMIN);
        u.setUbicacion(new UbicacionUsuario("Cartagena", "Bolívar"));
        u.setCreatedAt(LocalDateTime.now().minusDays(300));
        u.setUltimaConexion(LocalDateTime.now().minusMinutes(5));
        u.setEmailVerificado(true);
        PerfilAdmin perfil = new PerfilAdmin("ADMIN");
        perfil.setActivo(true);
        u.setPerfilAdmin(perfil);
        return u;
    }

    private CuentaBancaria crearCuentaBancaria(int indice, String titular) {
        Banco[] bancos = {Banco.BANCOLOMBIA, Banco.DAVIVIENDA, Banco.BANCO_DE_BOGOTA,
                Banco.BANCO_AGRARIO, Banco.BBVA_COLOMBIA, Banco.NEQUI, Banco.DAVIPLATA};
        Banco banco = bancos[Math.abs(indice) % bancos.length];
        boolean digital = banco.esBilleteraDigital();
        String numero = digital
                ? String.format(Locale.ROOT, "300000%04d", 1000 + Math.abs(indice) % 8999)
                : String.format(Locale.ROOT, "0000%08d", Math.abs(indice) % 99_999_999);
        TipoCuenta tipo = digital ? TipoCuenta.DEPOSITO_ELECTRONICO : TipoCuenta.AHORROS;
        String documento = String.format(Locale.ROOT, "1000%06d", Math.abs(indice) % 999_999);
        String titularLimpio = titular != null && titular.length() >= 3 ? titular : "Titular Demo";
        return new CuentaBancaria(banco, tipo, numero, titularLimpio, TipoDocumento.CC, documento);
    }

    private DatosVendedor snapshotVendedor(Usuario vendedor) {
        PerfilVendedor p = vendedor.getPerfilVendedor();
        return new DatosVendedor(vendedor.getId(), vendedor.getNombre(), p != null && p.isVerificado(),
                p != null ? p.getRazonSocial() : null,
                p != null ? p.getTelefonoContacto() : null,
                p != null ? p.getDescripcionNegocio() : null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ofertas
    // ─────────────────────────────────────────────────────────────────────────

    private void agregarOferta(Usuario vendedor, ProductoCatalogo producto,
                               Map<String, List<OfertaVendedor>> ofertasPorVendedor,
                               Map<String, Set<String>> productosPorVendedor,
                               List<OfertaVendedor> todasLasOfertas,
                               Map<String, OfertaVendedor> ofertasPorId) {
        Set<String> productos = productosPorVendedor.computeIfAbsent(vendedor.getId(), k -> new LinkedHashSet<>());
        if (!productos.add(producto.getId())) {
            return;
        }
        OfertaVendedor oferta = crearOferta(vendedor, producto);
        oferta = ofertaRepository.save(oferta);
        ofertasPorVendedor.computeIfAbsent(vendedor.getId(), k -> new ArrayList<>()).add(oferta);
        todasLasOfertas.add(oferta);
        ofertasPorId.put(oferta.getId(), oferta);
    }

    private OfertaVendedor crearOferta(Usuario vendedor, ProductoCatalogo producto) {
        PerfilVendedor perfil = vendedor.getPerfilVendedor();
        double base = PRECIOS_BASE.getOrDefault(producto.getNombre(), 5_000.0);
        double factor = 0.88 + random.nextDouble() * 0.24;
        double precio = Math.max(500.0, Math.round((base * factor) / 100.0) * 100.0);
        double stock = 100.0 + random.nextInt(300);
        String abreviatura = producto.getUnidadPorDefecto() != null
                ? producto.getUnidadPorDefecto().getAbreviatura() : "un";

        OfertaVendedor oferta = new OfertaVendedor();
        oferta.setProductoCatalogoId(producto.getId());
        oferta.setVendedor(snapshotVendedor(vendedor));
        oferta.setPrecio(precio);
        oferta.setStock(stock);
        oferta.setPeso(1.0);
        oferta.setPesoPromedioUnidad(1.0);
        oferta.setDescripcionPaquete("Presentación por " + abreviatura
                + (perfil != null && perfil.getRazonSocial() != null ? " de " + perfil.getRazonSocial() : ""));
        oferta.setCompraMinima(1.0);
        oferta.setDisponible(true);
        oferta.setImagenUrl(null);
        oferta.setDescripcionVendedor("Cosechado y seleccionado en "
                + (vendedor.getUbicacion() != null ? vendedor.getUbicacion().getCiudad() : "Bolívar")
                + " por " + (perfil != null ? perfil.getRazonSocial() : vendedor.getNombre()) + ".");
        oferta.setUnidadMedida(producto.getUnidadPorDefecto() != null
                ? producto.getUnidadPorDefecto().copia() : com.example.demo.Model.embebidos.UnidadMedida.UN.copia());
        oferta.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        oferta.setNombreProducto(producto.getNombre());
        if (producto.getCategoria() != null) {
            oferta.setCategoriaId(producto.getCategoria().getId());
            oferta.setCategoriaNombre(producto.getCategoria().getNombre());
        }
        oferta.setImagenReferencia(ImagenUrlHelper.rutaReferenciaProducto(producto.getNombre()));
        LocalDateTime creada = LocalDateTime.now().minusDays(20 + random.nextInt(120));
        oferta.setCreatedAt(creada);
        oferta.setUpdatedAt(creada);
        return oferta;
    }

    private ProductoCatalogo elegirProductoParaVendedor(Usuario vendedor, List<ProductoCatalogo> productos,
                                                         Set<String> yaTiene, int intento) {
        String categoria = vendedor.getPerfilVendedor() != null
                ? vendedor.getPerfilVendedor().getTipoProductos() : null;

        if (categoria != null) {
            for (ProductoCatalogo p : productos) {
                if (p.getCategoria() != null && categoria.equalsIgnoreCase(p.getCategoria().getNombre())
                        && !yaTiene.contains(p.getId())) {
                    return p;
                }
            }
        }
        int inicio = Math.abs((vendedor.getId().hashCode() + intento) % productos.size());
        for (int i = 0; i < productos.size(); i++) {
            ProductoCatalogo p = productos.get((inicio + i) % productos.size());
            if (!yaTiene.contains(p.getId())) {
                return p;
            }
        }
        return null;
    }

    private int marcarPendientes(List<OfertaVendedor> ofertas, List<OfertaVendedor> ofertasDestacado,
                                 int objetivoRegulares, int objetivoDestacado) {
        Map<String, Long> publicadasPorProducto = ofertas.stream()
                .filter(o -> o.getEstadoPublicacion() == EstadoPublicacion.PUBLICADO)
                .collect(Collectors.groupingBy(OfertaVendedor::getProductoCatalogoId, Collectors.counting()));

        int pendientes = 0;
        // Primero las 2 del vendedor destacado (el admin las verá en su bandeja).
        for (int i = ofertasDestacado.size() - 1; i >= 0 && pendientes < objetivoDestacado; i--) {
            OfertaVendedor oferta = ofertasDestacado.get(i);
            long publicadas = publicadasPorProducto.getOrDefault(oferta.getProductoCatalogoId(), 0L);
            if (publicadas > 3) {
                oferta.setEstadoPublicacion(EstadoPublicacion.PENDIENTE);
                oferta.setUpdatedAt(LocalDateTime.now());
                ofertaRepository.save(oferta);
                publicadasPorProducto.merge(oferta.getProductoCatalogoId(), -1L, Long::sum);
                pendientes++;
            }
        }

        // Luego el resto, recorriendo de forma determinista.
        int pendientesRegulares = 0;
        int paso = 11;
        for (int i = 0; i < ofertas.size() && pendientesRegulares < objetivoRegulares; i += paso) {
            OfertaVendedor oferta = ofertas.get(i);
            if (ofertasDestacado.contains(oferta)) {
                continue;
            }
            long publicadas = publicadasPorProducto.getOrDefault(oferta.getProductoCatalogoId(), 0L);
            if (publicadas > 3) {
                oferta.setEstadoPublicacion(EstadoPublicacion.PENDIENTE);
                oferta.setUpdatedAt(LocalDateTime.now());
                ofertaRepository.save(oferta);
                publicadasPorProducto.merge(oferta.getProductoCatalogoId(), -1L, Long::sum);
                pendientesRegulares++;
                pendientes++;
            }
        }
        // Si el paso dejó pocos, completar con un recorrido fino.
        if (pendientesRegulares < objetivoRegulares) {
            for (OfertaVendedor oferta : ofertas) {
                if (pendientesRegulares >= objetivoRegulares) break;
                if (ofertasDestacado.contains(oferta)
                        || oferta.getEstadoPublicacion() == EstadoPublicacion.PENDIENTE) continue;
                long publicadas = publicadasPorProducto.getOrDefault(oferta.getProductoCatalogoId(), 0L);
                if (publicadas > 3) {
                    oferta.setEstadoPublicacion(EstadoPublicacion.PENDIENTE);
                    oferta.setUpdatedAt(LocalDateTime.now());
                    ofertaRepository.save(oferta);
                    publicadasPorProducto.merge(oferta.getProductoCatalogoId(), -1L, Long::sum);
                    pendientesRegulares++;
                    pendientes++;
                }
            }
        }
        return pendientes;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Compras y pedidos
    // ─────────────────────────────────────────────────────────────────────────

    private record CompraSpec(int indice, int tamano, boolean showcaseSeller, boolean showcaseBuyer, String ciudad) {
    }

    private List<CompraSpec> construirComprasSpecs() {
        List<CompraSpec> specs = new ArrayList<>();

        // 0-4: 5 compras de un solo pedido del vendedor destacado, comprador destacado.
        for (int i = 0; i <= 4; i++) {
            specs.add(new CompraSpec(i, 1, true, true, "Cartagena"));
        }
        // 5-8: 4 compras de dos vendedores (destacado + regular), comprador destacado.
        for (int i = 5; i <= 8; i++) {
            specs.add(new CompraSpec(i, 2, true, true, "Cartagena"));
        }
        // 9-31: 23 compras de un solo vendedor regular.
        for (int i = 9; i <= 31; i++) {
            String ciudad = i == 9 ? "Cartagena" : CIUDADES_ENTREGA[i % CIUDADES_ENTREGA.length];
            specs.add(new CompraSpec(i, 1, false, false, ciudad));
        }
        // 32: una compra de dos vendedores (destacado + regular).
        specs.add(new CompraSpec(32, 2, true, false, "Cartagena"));
        // 33-37: 5 compras de dos vendedores regulares.
        for (int i = 33; i <= 37; i++) {
            specs.add(new CompraSpec(i, 2, false, false,
                    CIUDADES_ENTREGA[(i * 3) % CIUDADES_ENTREGA.length]));
        }
        // 38-39: 2 compras de un solo vendedor regular.
        specs.add(new CompraSpec(38, 1, false, false, "Magangué"));
        specs.add(new CompraSpec(39, 1, false, false, "Sincelejo"));

        if (specs.size() != COMPRAS_DEMO) {
            throw new IllegalStateException("El plan de compras demo debe tener " + COMPRAS_DEMO
                    + " compras, pero tiene " + specs.size());
        }
        return specs;
    }

    private Map<Integer, List<EstadoPedido>> planEstados() {
        Map<Integer, List<EstadoPedido>> plan = new HashMap<>();

        plan.put(0, List.of(EstadoPedido.ENTREGADO));
        plan.put(1, List.of(EstadoPedido.ENTREGADO));
        plan.put(2, List.of(EstadoPedido.ENTREGADO));
        plan.put(3, List.of(EstadoPedido.LISTO_PARA_RECOGER));
        plan.put(4, List.of(EstadoPedido.CANCELADO));
        plan.put(5, List.of(EstadoPedido.ENTREGADO, EstadoPedido.ENTREGADO));
        plan.put(6, List.of(EstadoPedido.CONFIRMADO, EstadoPedido.ENTREGADO));
        plan.put(7, List.of(EstadoPedido.ENTREGADO, EstadoPedido.ENVIADO));
        plan.put(8, List.of(EstadoPedido.LISTO_PARA_RECOGER, EstadoPedido.CANCELADO));

        List<EstadoPedido> singles = new ArrayList<>();
        for (int i = 0; i < 13; i++) singles.add(EstadoPedido.ENTREGADO);
        singles.add(EstadoPedido.CONFIRMADO);
        singles.add(EstadoPedido.CANCELADO);
        singles.add(EstadoPedido.ENVIADO);
        singles.add(EstadoPedido.EN_PROCESO);
        singles.add(EstadoPedido.EN_PROCESO);
        singles.add(EstadoPedido.LISTO_PARA_RECOGER);
        singles.add(EstadoPedido.LISTO_PARA_RECOGER);
        singles.add(EstadoPedido.PENDIENTE);
        singles.add(EstadoPedido.EN_REVISION);
        singles.add(EstadoPedido.EN_REVISION);

        for (int i = 9; i <= 31; i++) {
            plan.put(i, List.of(singles.get(i - 9)));
        }

        plan.put(32, List.of(EstadoPedido.CONFIRMADO, EstadoPedido.EN_PROCESO));
        plan.put(33, List.of(EstadoPedido.ENTREGADO, EstadoPedido.ENTREGADO));
        plan.put(34, List.of(EstadoPedido.ENTREGADO, EstadoPedido.PENDIENTE));
        plan.put(35, List.of(EstadoPedido.ENTREGADO, EstadoPedido.ENVIADO));
        plan.put(36, List.of(EstadoPedido.ENTREGADO, EstadoPedido.EN_PROCESO));
        plan.put(37, List.of(EstadoPedido.ENVIADO, EstadoPedido.PENDIENTE));
        plan.put(38, List.of(EstadoPedido.PENDIENTE));
        plan.put(39, List.of(EstadoPedido.PENDIENTE));

        long total = plan.values().stream().mapToLong(List::size).sum();
        if (total != PEDIDOS_DEMO) {
            throw new IllegalStateException("El plan de estados demo debe sumar " + PEDIDOS_DEMO
                    + " pedidos, pero suma " + total);
        }
        return plan;
    }

    private List<Usuario> elegirVendedores(CompraSpec spec, List<EstadoPedido> estados,
                                            List<Usuario> vendedoresRegulares, Usuario vendedorDestacado) {
        List<Usuario> elegidos = new ArrayList<>();

        if (spec.showcaseSeller()) {
            elegidos.add(vendedorDestacado);
        }

        if (spec.tamano() == 1) {
            if (elegidos.isEmpty()) {
                if (estados.get(0) == EstadoPedido.ENTREGADO && spec.indice() >= 9 && spec.indice() <= 21) {
                    elegidos.add(vendedoresRegulares.get((spec.indice() - 9) % 5));
                } else {
                    elegidos.add(vendedoresRegulares.get(5 + ((spec.indice() * 7) % 35)));
                }
            }
            return elegidos;
        }

        int intentos = 0;
        while (elegidos.size() < spec.tamano() && intentos < 200) {
            Usuario candidato = vendedoresRegulares.get((spec.indice() * 7 + intentos * 13) % vendedoresRegulares.size());
            if (!elegidos.contains(candidato)) {
                elegidos.add(candidato);
            }
            intentos++;
        }
        if (elegidos.size() < spec.tamano()) {
            throw new IllegalStateException("No se pudieron elegir vendedores para la compra demo " + spec.indice());
        }
        return elegidos;
    }

    private List<ProductoPedido> crearItems(Usuario vendedor, Map<String, List<OfertaVendedor>> ofertasPorVendedor,
                                            EstadoPedido estado) {
        List<OfertaVendedor> publicadas = ofertasPorVendedor.getOrDefault(vendedor.getId(), List.of()).stream()
                .filter(o -> o.getEstadoPublicacion() == EstadoPublicacion.PUBLICADO)
                .collect(Collectors.toList());
        if (publicadas.isEmpty()) {
            throw new IllegalStateException("El vendedor demo " + vendedor.getEmail() + " no tiene ofertas publicadas");
        }

        List<OfertaVendedor> copia = new ArrayList<>(publicadas);
        Collections.shuffle(copia, random);
        int cantidadItems = 1 + random.nextInt(Math.min(3, copia.size()));

        List<ProductoPedido> items = new ArrayList<>();
        for (int i = 0; i < cantidadItems; i++) {
            OfertaVendedor oferta = copia.get(i);
            String abreviatura = oferta.unidadEfectiva() != null && oferta.unidadEfectiva().getAbreviatura() != null
                    ? oferta.unidadEfectiva().getAbreviatura() : "un";
            boolean decimales = oferta.unidadEfectiva() != null && oferta.unidadEfectiva().permiteDecimales();
            double cantidad = decimales
                    ? (1.0 + random.nextInt(5) * 0.5)
                    : (1 + random.nextInt(4));
            if (estado == EstadoPedido.CANCELADO && random.nextBoolean()) {
                cantidad = Math.max(1.0, cantidad);
            }
            double subtotal = Math.round(oferta.getPrecio() * cantidad * 100.0) / 100.0;
            ProductoPedido item = new ProductoPedido(oferta.getId(), oferta.getNombreProducto(),
                    ImagenUrlHelper.rutaReferenciaProducto(oferta.getNombreProducto()),
                    oferta.getPrecio(), cantidad, abreviatura);
            item.setSubtotal(subtotal);
            items.add(item);
        }
        return items;
    }

    private void asegurarMinimoCompra(List<Pedido> pedidos, double minimo) {
        double subtotal = pedidos.stream()
                .mapToDouble(p -> p.getSubtotalCentavos() != null ? p.getSubtotalCentavos() / 100.0 : 0.0)
                .sum();
        if (subtotal >= minimo) {
            return;
        }

        for (Pedido pedido : pedidos) {
            for (ProductoPedido item : pedido.getItems()) {
                if (subtotal >= minimo) {
                    break;
                }
                double precio = item.getPrecioUnitario() != null ? item.getPrecioUnitario() : 0.0;
                if (precio <= 0) {
                    continue;
                }
                double faltante = minimo - subtotal;
                double unidadesExtra = Math.ceil(faltante / precio);
                double cantidad = (item.getCantidad() != null ? item.getCantidad() : 1.0) + unidadesExtra;
                item.setCantidad(Math.round(cantidad * 100.0) / 100.0);
                item.setSubtotal(Math.round(precio * item.getCantidad() * 100.0) / 100.0);
                pedido.setItems(pedido.getItems());
                double nuevoSubtotal = pedido.calcularTotal();
                pedido.setSubtotalCentavos(Math.round(nuevoSubtotal * 100.0));
                pedido.setTotal(nuevoSubtotal);
                if (pedido.getPago() != null) {
                    pedido.getPago().setMonto(nuevoSubtotal);
                }
                subtotal = pedidos.stream()
                        .mapToDouble(p -> p.getSubtotalCentavos() != null ? p.getSubtotalCentavos() / 100.0 : 0.0)
                        .sum();
            }
        }
    }

    private LocalDateTime fechaParaEstado(EstadoPedido estado) {
        LocalDateTime ahora = LocalDateTime.now();
        switch (estado) {
            case ENTREGADO:
                return ahora.minusDays(46 + random.nextInt(12));
            case ENVIADO:
                return ahora.minusDays(5 + random.nextInt(10));
            case LISTO_PARA_RECOGER:
                return ahora.minusDays(3 + random.nextInt(8));
            case CONFIRMADO:
                return ahora.minusDays(1 + random.nextInt(7));
            case EN_PROCESO:
                return ahora.minusDays(1 + random.nextInt(5));
            case PENDIENTE:
                return ahora.minusDays(random.nextInt(3));
            case CANCELADO:
                return ahora.minusDays(10 + random.nextInt(31));
            case EN_REVISION:
                return ahora.minusDays(3 + random.nextInt(18));
            default:
                return ahora.minusDays(1);
        }
    }

    private LocalDateTime fechaBaseCompra(List<EstadoPedido> estados) {
        return estados.stream()
                .map(this::fechaParaEstado)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusDays(1));
    }

    private void aplicarDetalleEstado(Pedido pedido, EstadoPedido estado, LocalDateTime fechaPedido,
                                      LocalDateTime fechaEntrega) {
        switch (estado) {
            case ENTREGADO:
                pedido.setFechaEntrega(fechaEntrega != null ? fechaEntrega : fechaPedido.plusDays(1));
                pedido.setEntregaManual(true);
                pedido.setMotivoEntregaManual("Entrega registrada por el transportista en la demo");
                pedido.setUpdatedAt(pedido.getFechaEntrega());
                break;
            case ENVIADO:
                pedido.setUpdatedAt(fechaPedido.plusHours(8));
                break;
            case LISTO_PARA_RECOGER:
                pedido.setFechaListoParaRecoger(fechaPedido.plusHours(6));
                pedido.setUpdatedAt(fechaPedido.plusHours(6));
                break;
            case CANCELADO:
                pedido.setCanceladoPor(CanceladoPor.VENDEDOR);
                pedido.setMotivoCancelacion("Producto agotado para la fecha de entrega");
                pedido.setFechaCancelacion(fechaPedido.plusDays(1));
                pedido.setRequiereReembolso(true);
                pedido.setMontoReembolsoCentavos(Math.round(pedido.calcularTotal() * 100.0));
                pedido.setStockDescontado(false);
                pedido.setUpdatedAt(fechaPedido.plusDays(1));
                break;
            case EN_REVISION:
                pedido.setRequiereRevision(true);
                pedido.setMotivoRevision("Stock insuficiente al confirmar el pago; pendiente de reembolso manual");
                pedido.setStockDescontado(false);
                pedido.setUpdatedAt(fechaPedido.plusHours(3));
                break;
            default:
                break;
        }
    }

    private String estadoCompra(List<Pedido> pedidos) {
        if (pedidos.stream().anyMatch(Pedido::isPendiente)) {
            return Compra.PENDIENTE;
        }
        boolean todosFinales = pedidos.stream().allMatch(p -> p.isEntregado() || p.isCancelado());
        boolean algunEntregado = pedidos.stream().anyMatch(Pedido::isEntregado);
        if (todosFinales && algunEntregado) {
            return Compra.COMPLETADA;
        }
        return Compra.PAGADA;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Envíos
    // ─────────────────────────────────────────────────────────────────────────

    private List<Envio> crearEnvios(List<Compra> compras, Map<String, List<Pedido>> pedidosPorCompra,
                                    List<Usuario> transportistas, Usuario transportistaDestacado,
                                    Map<String, Integer> entregasPorTransportista) {
        List<Envio> envios = new ArrayList<>();

        for (Compra compra : compras) {
            List<Pedido> pedidos = pedidosPorCompra.getOrDefault(compra.getId(), List.of());
            List<Pedido> activos = pedidos.stream()
                    .filter(p -> !p.isPendiente() && !p.isCancelado() && !p.isEnRevision())
                    .collect(Collectors.toList());
            if (activos.isEmpty()) {
                continue;
            }

            int indice = indiceCompra(compra);
            List<ParadaEnvio> paradas = new ArrayList<>();
            for (Pedido p : activos) {
                ParadaEnvio parada = new ParadaEnvio(p.getId(),
                        p.getVendedor() != null ? p.getVendedor().getId() : null,
                        p.getVendedor() != null ? p.getVendedor().getNombre() : "Vendedor demo",
                        direccionVendedor(p.getVendedor()), p.getCiudadOrigenEnvio(),
                        p.getZonaOrigenEnvio(), p.getEnvioCentavos());
                parada.setVendedorTelefono(p.getVendedor() != null ? p.getVendedor().getTelefonoContacto() : null);
                parada.setRecogido(p.isEntregado() || p.isEnviado());
                if (parada.isRecogido()) {
                    parada.setFechaRecogida((p.getFechaEntrega() != null ? p.getFechaEntrega() : p.getFechaPedido())
                            .minusHours(4));
                }
                parada.setItemsResumen(p.getItems().stream()
                        .map(i -> String.format(Locale.GERMANY, "%s x %.2f %s",
                                i.getNombreSnapshot(), i.getCantidad(), i.getUnidadAbreviatura()))
                        .collect(Collectors.toList()));
                paradas.add(parada);
            }

            long costoTotal = activos.stream()
                    .mapToLong(p -> p.getEnvioCentavos() != null ? p.getEnvioCentavos() : 0L)
                    .sum();
            DireccionPedido punto = copiarDireccion(activos.get(0).getDireccionEntrega());
            Envio envio = new Envio(compra.getId(), paradas, punto, costoTotal);
            envio.setFechaCreacion(compra.getFecha() != null
                    ? compra.getFecha().plusMinutes(8) : LocalDateTime.now().minusDays(1));
            envio.setUpdatedAt(envio.getFechaCreacion());

            boolean unassigned = indice == 3 || indice == 8;
            boolean showTransporter = indice == 0 || indice == 1 || indice == 2 || indice == 5
                    || indice == 7 || indice == 9;

            boolean todosEntregados = activos.stream().allMatch(Pedido::isEntregado);
            boolean algunEnviado = activos.stream().anyMatch(Pedido::isEnviado);
            boolean algunEntregado = activos.stream().anyMatch(Pedido::isEntregado);

            if (unassigned) {
                envio.setEstado(Envio.PENDIENTE_ASIGNACION);
                envio.setFechaDisponibleDesde(LocalDateTime.now().minusHours(1 + random.nextInt(4)));
            } else {
                Usuario transportista = showTransporter ? transportistaDestacado
                        : transportistas.get(1 + (indice % Math.max(1, transportistas.size() - 1)));
                envio.setTransportistaId(transportista.getId());
                envio.setTransportista(snapshotTransportista(transportista));
                envio.setFechaDisponibleDesde(envio.getFechaCreacion());
                envio.setFechaAsignacion(envio.getFechaCreacion().plusHours(2 + random.nextInt(4)));

                if (todosEntregados) {
                    envio.setEstado(Envio.ENTREGADO);
                    LocalDateTime entrega = activos.stream()
                            .map(Pedido::getFechaEntrega)
                            .filter(java.util.Objects::nonNull)
                            .max(LocalDateTime::compareTo)
                            .orElse(envio.getFechaAsignacion().plusDays(1));
                    envio.setFechaEntrega(entrega);
                    entregasPorTransportista.merge(transportista.getId(), 1, Integer::sum);
                } else if (algunEnviado || algunEntregado) {
                    envio.setEstado(Envio.EN_ENTREGA);
                } else {
                    envio.setEstado(Envio.ASIGNADO);
                    // Asignación reciente: evita que el scheduler de expiración devuelva
                    // el envío a la bolsa durante la demo (HORAS_MAX_ASIGNADO_SIN_RECOGIDA).
                    LocalDateTime asignacionReciente = LocalDateTime.now().minusHours(1 + random.nextInt(3));
                    envio.setFechaAsignacion(asignacionReciente);
                    envio.setFechaDisponibleDesde(asignacionReciente.minusHours(1 + random.nextInt(2)));
                }
            }

            envio = envioRepository.save(envio);
            envios.add(envio);
        }
        return envios;
    }

    private int indiceCompra(Compra compra) {
        if (compra.getReferenciaPago() == null) return 0;
        String digitos = compra.getReferenciaPago().substring(PREFIJO_REFERENCIA.length());
        try {
            return Math.max(0, Integer.parseInt(digitos) - 1_000_000_000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private SnapshotTransportista snapshotTransportista(Usuario t) {
        PerfilTransportista p = t.getPerfilTransportista();
        return new SnapshotTransportista(t.getId(), t.getNombre(),
                p != null ? p.getTelefonoContacto() : null,
                p != null ? p.getTipo() : PerfilTransportista.TIPO_INDEPENDIENTE,
                p != null ? p.getPlaca() : null,
                p != null ? p.getRazonSocial() : null,
                p != null ? p.getTipoVehiculo() : null);
    }

    private String direccionVendedor(DatosVendedor vendedor) {
        if (vendedor == null || vendedor.getId() == null) {
            return "Dirección de recogida demo";
        }
        return usuarioRepository.findById(vendedor.getId())
                .map(Usuario::getPerfilVendedor)
                .map(PerfilVendedor::getDireccionNegocio)
                .orElse("Dirección de recogida demo");
    }

    private DireccionPedido copiarDireccion(DireccionPedido original) {
        if (original == null) {
            return new DireccionPedido("Dirección demo", "Cartagena", "Bolívar");
        }
        DireccionPedido copia = new DireccionPedido(original.getDireccion(), original.getCiudad(),
                original.getDepartamento());
        copia.setDestinatarioNombre(original.getDestinatarioNombre());
        copia.setDestinatarioTelefono(original.getDestinatarioTelefono());
        return copia;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retiros y reseñas
    // ─────────────────────────────────────────────────────────────────────────

    private int crearRetirosPendientes(List<Usuario> vendedores, Usuario vendedorDestacado) {
        long minimoCentavos = Math.max(50_000L,
                (long) configService.obtenerValorInt("RETIRO_MINIMO_COP", 50_000)) * 100L;

        List<Billetera> candidatas = new ArrayList<>();
        for (Usuario v : vendedores) {
            billeteraAlmacen.buscar(v.getId(), Billetera.Rol.VENDEDOR)
                    .filter(b -> b.getSaldoDisponibleCentavos() >= minimoCentavos)
                    .ifPresent(candidatas::add);
        }
        candidatas.sort(Comparator.comparingLong(Billetera::getSaldoDisponibleCentavos).reversed());

        int creados = 0;
        long[] montosDeseados = {100_000L, 75_000L, 50_000L};
        for (int i = 0; i < candidatas.size() && creados < 3; i++) {
            Billetera billetera = candidatas.get(i);
            long disponible = billetera.getSaldoDisponibleCentavos();
            long montoCentavos = Math.min(disponible, Math.max(minimoCentavos, montosDeseados[creados] * 100L));
            if (montoCentavos < minimoCentavos) {
                continue;
            }
            Usuario usuario = usuarioRepository.findById(billetera.getUsuarioId()).orElse(null);
            if (usuario == null || usuario.getPerfilVendedor() == null) {
                continue;
            }
            CuentaBancaria cuenta = usuario.getPerfilVendedor().getCuentaRetiro() != null
                    ? usuario.getPerfilVendedor().getCuentaRetiro().copiar()
                    : crearCuentaBancaria(creados, usuario.getNombre());

            SolicitudRetiro solicitud = new SolicitudRetiro(usuario.getId(), Billetera.Rol.VENDEDOR,
                    usuario.getNombre(), usuario.getEmail(), montoCentavos, cuenta);
            solicitud.setFechaSolicitud(LocalDateTime.now().minusDays(1 + creados));
            solicitud = solicitudRetiroRepository.save(solicitud);

            if (!billeteraAlmacen.reservarParaRetiro(billetera.getId(), montoCentavos)) {
                solicitudRetiroRepository.delete(solicitud);
                continue;
            }

            String descripcion = "Reserva para solicitud de retiro hacia "
                    + (cuenta != null ? cuenta.getResumenEnmascarado() : "cuenta registrada");
            MovimientoBilletera movDisponible = MovimientoBilletera.nuevo(billetera,
                    MovimientoBilletera.Tipo.RETIRO_RESERVADO, Billetera.EstadoSaldo.DISPONIBLE,
                    -montoCentavos, null, null, descripcion,
                    "RETIRO_RESERVADO_DISP:" + solicitud.getId(), solicitud.getId());
            billeteraAlmacen.insertarMovimiento(movDisponible);

            MovimientoBilletera movProceso = MovimientoBilletera.nuevo(billetera,
                    MovimientoBilletera.Tipo.RETIRO_RESERVADO, Billetera.EstadoSaldo.EN_PROCESO_RETIRO,
                    montoCentavos, null, null, descripcion,
                    "RETIRO_RESERVADO_PROC:" + solicitud.getId(), solicitud.getId());
            billeteraAlmacen.insertarMovimiento(movProceso);

            creados++;
        }
        return creados;
    }

    private int crearResenas(List<Pedido> pedidos, Map<String, List<OfertaVendedor>> ofertasPorVendedor) {
        int total = 0;
        Map<String, List<Integer>> calificacionesPorVendedor = new HashMap<>();
        Map<String, ResenaVendedor> resenaUnicaPorPedido = new HashMap<>();

        for (Pedido pedido : pedidos) {
            if (!pedido.isEntregado() || pedido.getVendedor() == null || pedido.getVendedor().getId() == null) {
                continue;
            }
            LocalDate fecha = (pedido.getFechaEntrega() != null
                    ? pedido.getFechaEntrega().toLocalDate() : LocalDate.now().minusDays(10))
                    .plusDays(1);
            if (fecha.isAfter(LocalDate.now())) {
                fecha = LocalDate.now();
            }

            for (ProductoPedido item : pedido.getItems()) {
                int calificacion = random.nextInt(10) < 7 ? 5 : 4;
                String comentario = COMENTARIOS_PRODUCTO[random.nextInt(COMENTARIOS_PRODUCTO.length)];
                Resena resena = new Resena(item.getProductoId(), pedido.getComprador().getId(),
                        pedido.getId(), calificacion, comentario, fecha);
                resenaRepository.save(resena);
                total++;
            }

            int calificacionVendedor = random.nextInt(10) < 7 ? 5 : 4;
            String comentarioVendedor = COMENTARIOS_VENDEDOR[random.nextInt(COMENTARIOS_VENDEDOR.length)];
            ResenaVendedor resenaVendedor = new ResenaVendedor(pedido.getVendedor().getId(),
                    pedido.getComprador().getId(), pedido.getId(), calificacionVendedor,
                    comentarioVendedor, fecha);
            resenaVendedorRepository.save(resenaVendedor);
            resenaUnicaPorPedido.put(pedido.getId(), resenaVendedor);
            calificacionesPorVendedor.computeIfAbsent(pedido.getVendedor().getId(), k -> new ArrayList<>())
                    .add(calificacionVendedor);
            total++;
        }

        // Actualizar el promedio embebido en todas las ofertas del vendedor.
        for (Map.Entry<String, List<Integer>> entry : calificacionesPorVendedor.entrySet()) {
            double promedio = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(5.0);
            promedio = Math.round(promedio * 10.0) / 10.0;
            for (OfertaVendedor oferta : ofertasPorVendedor.getOrDefault(entry.getKey(), List.of())) {
                if (oferta.getVendedor() != null) {
                    oferta.getVendedor().setCalificacionPromedio(promedio);
                    ofertaRepository.save(oferta);
                }
            }
        }
        return total;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades
    // ─────────────────────────────────────────────────────────────────────────

    private String direccionFicticia(int indice) {
        int calle = 10 + (indice * 7) % 90;
        int carrera = 5 + (indice * 3) % 40;
        int numero = 1 + (indice * 11) % 80;
        return "Calle " + calle + " # " + carrera + "-" + numero;
    }

    private String telefonoFicticio(int indice) {
        return String.format(Locale.ROOT, "300000%04d", Math.abs(indice) % 10_000);
    }
}
