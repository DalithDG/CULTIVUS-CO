package com.example.demo.Config;

import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilAdmin;
import com.example.demo.Model.embebidos.PerfilTransportista;
import com.example.demo.Model.TarifaEnvio;
import com.example.demo.Model.ZonaMunicipio;
import com.example.demo.Model.embebidos.CiudadUbicacion;
import com.example.demo.repository.TarifaEnvioRepository;
import com.example.demo.repository.UbicacionRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.repository.ZonaMunicipioRepository;
import com.example.demo.services.AppConfigService;
import com.example.demo.services.ZonasLogistica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class DataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppConfigService configService;
    private final ZonaMunicipioRepository zonaMunicipioRepository;
    private final TarifaEnvioRepository tarifaEnvioRepository;
    private final UbicacionRepository ubicacionRepository;

    public DataLoader(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AppConfigService configService, ZonaMunicipioRepository zonaMunicipioRepository,
            TarifaEnvioRepository tarifaEnvioRepository, UbicacionRepository ubicacionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.configService = configService;
        this.zonaMunicipioRepository = zonaMunicipioRepository;
        this.tarifaEnvioRepository = tarifaEnvioRepository;
        this.ubicacionRepository = ubicacionRepository;
    }

    private static final String ADMIN_EMAIL = "admin1@demo.com";
    private static final String ADMIN_PASSWORD = "Admin1234!";

    @Override
    public void run(String... args) {
        try {
            logger.info("🚀 Iniciando DataLoader...");

            usuarioRepository.findByEmail(ADMIN_EMAIL).ifPresentOrElse(
                    admin -> migrarAdminSiEsNecesario(admin),
                    () -> crearAdmin());

            crearTransportistaDemoSiNoExiste();

            inicializarConfiguracion();

            inicializarZonasYTarifas();

            logger.info("✅ DataLoader finalizado correctamente");

        } catch (Exception e) {
            logger.error("❌ Error en DataLoader: ", e);
            System.err.println("❌ Error en DataLoader: " + e.getMessage());
        }
    }

    /**
     * Si el admin ya existe pero su contraseña no está hasheada con BCrypt,
     * se actualiza automáticamente para no dejar contraseñas en texto plano.
     */
    private void migrarAdminSiEsNecesario(Usuario admin) {
        String contrasenaActual = admin.getContrasena();

        // BCrypt siempre empieza con "$2a$", "$2b$" o "$2y$"
        boolean yaEstaHasheada = contrasenaActual != null &&
                contrasenaActual.startsWith("$2");

        if (!yaEstaHasheada) {
            logger.warn("⚠️  Admin encontrado con contraseña en texto plano. Migrando a BCrypt...");
            admin.setContrasena(passwordEncoder.encode(contrasenaActual));
            usuarioRepository.save(admin);
            logger.info("✅ Contraseña del admin migrada a BCrypt correctamente");
        } else {
            if (!passwordEncoder.matches(ADMIN_PASSWORD, contrasenaActual)) {
                logger.info("⚠️  La contraseña en BD no coincide con ADMIN_PASSWORD. Actualizando...");
                admin.setContrasena(passwordEncoder.encode(ADMIN_PASSWORD));
                usuarioRepository.save(admin);
                logger.info("✅ Contraseña del admin actualizada a la definida en código.");
            } else {
                logger.info("ℹ️  Usuario ADMIN ya existe con contraseña hasheada y es correcta. Sin cambios.");
            }
        }
    }

    private void crearAdmin() {
        PerfilAdmin perfilAdmin = new PerfilAdmin("SUPER_ADMIN");
        perfilAdmin.setActivo(true);

        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setEmail(ADMIN_EMAIL);
        admin.setContrasena(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRol(Role.ADMIN);
        admin.setPerfilAdmin(perfilAdmin);

        usuarioRepository.save(admin);

        logger.info("✅ Usuario ADMIN creado:");
        logger.info("   📧 Email: {}", ADMIN_EMAIL);
        logger.info("   🔑 Contraseña: {}", ADMIN_PASSWORD);
        System.out.println("✅ Usuario ADMIN creado:");
        System.out.println("   📧 Email: " + ADMIN_EMAIL);
        System.out.println("   🔑 Contraseña: " + ADMIN_PASSWORD);
    }

    private void crearTransportistaDemoSiNoExiste() {
        String transportistaEmail = "transportista@demo.com";
        String transportistaPass = "Trans1234!";

        if (usuarioRepository.findByEmail(transportistaEmail).isEmpty()) {
            PerfilTransportista perfil = new PerfilTransportista(
                    "Camioneta Pick-up",
                    "TRA-789",
                    1500.0,
                    "C2",
                    "3101234567",
                    "Cartagena",
                    "Bolívar",
                    "Dique, Montes de María",
                    true,
                    "Transportes del Campo S.A.S.",
                    "Bancolombia",
                    "123-456789-00"
            );
            perfil.setTipo(PerfilTransportista.TIPO_INDEPENDIENTE);
            perfil.setZonasCoberturaIds(List.of(1, 2));
            perfil.setTodasLasZonas(false);
            perfil.setVerificado(true);
            perfil.setDisponible(true);

            Usuario transportista = new Usuario();
            transportista.setNombre("Carlos Mendoza (Conductor)");
            transportista.setEmail(transportistaEmail);
            transportista.setContrasena(passwordEncoder.encode(transportistaPass));
            transportista.addRole(Role.TRANSPORTISTA);
            transportista.addRole(Role.COMPRADOR);
            transportista.setPerfilTransportista(perfil);

            usuarioRepository.save(transportista);
            logger.info("✅ Usuario TRANSPORTISTA demo creado:");
            logger.info("   📧 Email: {}", transportistaEmail);
            logger.info("   🔑 Contraseña: {}", transportistaPass);
        }
    }

    private void inicializarConfiguracion() {
        logger.info("⚙️  Inicializando parámetros de configuración...");

        configService.guardarConfig("LIMITE_COMPRA_MAX", "5000000", "Límite máximo por transacción de compra (COP)",
                "NUMBER");
        configService.guardarConfig("COMPRA_MINIMA_GLOBAL", "50000",
                "Monto mínimo global para realizar un pedido (COP)",
                "NUMBER");
        configService.guardarConfig("MAX_PRODUCTOS_VENDEDOR", "50", "Cantidad máxima de productos activos por vendedor",
                "NUMBER");
        configService.guardarConfig("COMISION_VENTA_PORCENTAJE", "8.0",
                "Porcentaje de comisión que cobra la plataforma sobre los productos de cada venta, sin envío (%)", "NUMBER");
        configService.guardarConfig("MENSAJE_BIENVENIDA", "¡Bienvenido a Cultivus! Del campo a tu mesa.",
                "Mensaje que se muestra en el inicio", "STRING");
        configService.guardarConfig("TARIFA_ENVIO_DEFECTO", "15000",
                "Tarifa de envío por defecto (COP) si falta ciudad o zona", "NUMBER");
        configService.guardarConfig("HORAS_MAX_SIN_TRANSPORTISTA", "24",
                "Horas máximas para que un envío consiga transportista antes de pasar a SIN_TRANSPORTISTA", "NUMBER");

        sembrarSiNoExiste("VENTANA_RECLAMOS_MINUTOS", "1440",
                "Ventana de tiempo en minutos tras la entrega para que se libere el saldo de retencion a disponible", "NUMBER");
        sembrarSiNoExiste("RETIRO_MINIMO_COP", "50000",
                "Monto minimo en COP para solicitar un retiro de la billetera", "NUMBER");

        logger.info("✅ Parámetros de configuración inicializados.");
    }

    private void sembrarSiNoExiste(String clave, String valor, String descripcion, String tipo) {
        if (configService.obtenerValor(clave, null) == null) {
            configService.guardarConfig(clave, valor, descripcion, tipo);
        }
    }

    private void inicializarZonasYTarifas() {
        if (zonaMunicipioRepository.count() == 0) {
            logger.info("🗺️ Cargando catálogo de zonas y municipios de Bolívar...");
            Map<String, String> nombresUbicaciones = new HashMap<>();
            if (ubicacionRepository != null) {
                ubicacionRepository.findByNombre("Bolívar").ifPresent(depto -> {
                    if (depto.getCiudades() != null) {
                        for (CiudadUbicacion c : depto.getCiudades()) {
                            if (c.getNombre() != null) {
                                nombresUbicaciones.put(ZonasLogistica.normalizar(c.getNombre()), c.getNombre().trim());
                            }
                        }
                    }
                });
            }

            List<ZonaMunicipio> listaZonas = new ArrayList<>();
            for (ZonasLogistica.ZonaDef def : ZonasLogistica.ZONAS) {
                for (String muni : def.municipios()) {
                    String normalizado = ZonasLogistica.normalizar(muni);
                    String display = nombresUbicaciones.getOrDefault(normalizado, muni.trim());
                    listaZonas.add(new ZonaMunicipio(display, normalizado, "Bolívar", def.id(), def.nombre()));
                }
            }
            zonaMunicipioRepository.saveAll(listaZonas);
            logger.info("✅ {} municipios asignados a zonas logísticas.", listaZonas.size());
        }

        if (tarifaEnvioRepository.count() == 0) {
            logger.info("💲 Cargando matriz de tarifas por zonas...");
            List<TarifaEnvio> tarifas = new ArrayList<>();
            for (int origen = 1; origen <= 6; origen++) {
                for (int destino = 1; destino <= 6; destino++) {
                    long centavos = ZonasLogistica.tarifaEjemploCentavos(origen, destino);
                    tarifas.add(new TarifaEnvio(origen, destino, centavos));
                }
            }
            tarifaEnvioRepository.saveAll(tarifas);
            logger.info("✅ Matriz de 36 tarifas de envío guardada exitosamente.");
        }
    }
}