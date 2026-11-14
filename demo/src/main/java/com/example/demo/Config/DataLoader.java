package com.example.demo.Config;

import com.example.demo.Model.Role;
import com.example.demo.Model.Ubicacion;
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
import com.example.demo.services.CatalogoInicializador;
import com.example.demo.services.PasswordPolicy;
import com.example.demo.services.ZonasLogistica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final CatalogoInicializador catalogoInicializador;
    private final Environment environment;

    public DataLoader(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AppConfigService configService, ZonaMunicipioRepository zonaMunicipioRepository,
            TarifaEnvioRepository tarifaEnvioRepository, UbicacionRepository ubicacionRepository,
            Environment environment,
            @Autowired(required = false) CatalogoInicializador catalogoInicializador) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.configService = configService;
        this.zonaMunicipioRepository = zonaMunicipioRepository;
        this.tarifaEnvioRepository = tarifaEnvioRepository;
        this.ubicacionRepository = ubicacionRepository;
        this.environment = environment;
        this.catalogoInicializador = catalogoInicializador;
    }

    private static final String ADMIN_EMAIL = "admin1@demo.com";
    private static final String ADMIN_PASSWORD_VAR = "ADMIN_PASSWORD";
    private static final String ADMIN_FORCE_RESET_VAR = "ADMIN_PASSWORD_FORCE_RESET";
    private static final String TRANSPORTISTA_EMAIL = "transportista@demo.com";
    private static final String TRANSPORTISTA_PASSWORD_VAR = "TRANSPORTISTA_DEMO_PASSWORD";
    private static final String TRANSPORTISTA_FORCE_RESET_VAR = "TRANSPORTISTA_DEMO_PASSWORD_FORCE_RESET";

    @Override
    public void run(String... args) {
        try {
            logger.info("🚀 Iniciando DataLoader...");

            String adminPassword = leerVariable(ADMIN_PASSWORD_VAR);
            boolean adminForceReset = "true".equalsIgnoreCase(leerVariable(ADMIN_FORCE_RESET_VAR));

            usuarioRepository.findByEmail(ADMIN_EMAIL).ifPresentOrElse(
                    admin -> procesarAdminExistente(admin, adminPassword, adminForceReset),
                    () -> crearAdmin(adminPassword));

            crearTransportistaDemoSiNoExiste();

            inicializarConfiguracion();

            inicializarZonasYTarifas();

            if (catalogoInicializador != null) {
                catalogoInicializador.ejecutar();
            }

            logger.info("✅ DataLoader finalizado correctamente");

        } catch (Exception e) {
            logger.error("❌ Error en DataLoader: ", e);
            System.err.println("❌ Error en DataLoader: " + e.getMessage());
        }
    }

    /**
     * Procesa un admin que ya existe sin restablecer su contraseña en cada
     * arranque. Solo la actualiza si {@code ADMIN_PASSWORD_FORCE_RESET=true},
     * {@code ADMIN_PASSWORD} está definida y cumple la política. La comparación
     * con el hash actual hace que el restablecimiento sea idempotente: una vez
     * aplicado, dejar la variable activa no vuelve a cambiarla.
     */
    private void procesarAdminExistente(Usuario admin, String adminPassword, boolean forceReset) {
        if (!admin.isEmailVerificado()) {
            admin.setEmailVerificado(true);
            usuarioRepository.save(admin);
        }

        String contrasenaActual = admin.getContrasena();

        // BCrypt siempre empieza con "$2a$", "$2b$" o "$2y$".
        boolean yaEstaHasheada = contrasenaActual != null && contrasenaActual.startsWith("$2");

        if (!yaEstaHasheada && contrasenaActual != null) {
            // No cambia la contraseña: solo protege el mismo valor con BCrypt.
            logger.warn("Admin existente con contraseña sin BCrypt. Migrando el hash sin cambiar la contraseña.");
            admin.setContrasena(passwordEncoder.encode(contrasenaActual));
            usuarioRepository.save(admin);
            contrasenaActual = admin.getContrasena();
        }

        if (!forceReset) {
            logger.info("Admin existente: contraseña sin cambios. "
                    + "Define {} = true solo para restablecerla una vez.", ADMIN_FORCE_RESET_VAR);
            return;
        }

        restablecerContrasenaSiCorresponde(admin, "admin " + ADMIN_EMAIL, ADMIN_PASSWORD_VAR,
                ADMIN_FORCE_RESET_VAR, adminPassword, contrasenaActual);
    }

    private void crearAdmin(String adminPassword) {
        if (adminPassword == null) {
            logger.error("No se crea el admin {}: falta la variable {}. "
                    + "Defínela y vuelve a desplegar.", ADMIN_EMAIL, ADMIN_PASSWORD_VAR);
            return;
        }
        String errorPolitica = PasswordPolicy.validar(adminPassword);
        if (errorPolitica != null) {
            logger.error("No se crea el admin {}: {} no cumple PasswordPolicy: {}",
                    ADMIN_EMAIL, ADMIN_PASSWORD_VAR, errorPolitica);
            return;
        }

        PerfilAdmin perfilAdmin = new PerfilAdmin("SUPER_ADMIN");
        perfilAdmin.setActivo(true);

        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setEmail(ADMIN_EMAIL);
        admin.setContrasena(passwordEncoder.encode(adminPassword));
        admin.setRol(Role.ADMIN);
        admin.setPerfilAdmin(perfilAdmin);
        admin.setEmailVerificado(true);

        usuarioRepository.save(admin);

        logger.info("Usuario ADMIN creado: {}. La contraseña se tomó de {} y no se imprime.",
                ADMIN_EMAIL, ADMIN_PASSWORD_VAR);
    }

    private void crearTransportistaDemoSiNoExiste() {
        String transportistaPass = leerVariable(TRANSPORTISTA_PASSWORD_VAR);
        boolean forceReset = "true".equalsIgnoreCase(leerVariable(TRANSPORTISTA_FORCE_RESET_VAR));

        Optional<Usuario> existente = usuarioRepository.findByEmail(TRANSPORTISTA_EMAIL);
        if (existente.isPresent()) {
            Usuario transportista = existente.get();
            if (!forceReset) {
                logger.info("Transportista demo existente: contraseña sin cambios. "
                        + "Define {} = true solo para restablecerla una vez.",
                        TRANSPORTISTA_FORCE_RESET_VAR);
                return;
            }
            restablecerContrasenaSiCorresponde(transportista,
                    "transportista demo " + TRANSPORTISTA_EMAIL, TRANSPORTISTA_PASSWORD_VAR,
                    TRANSPORTISTA_FORCE_RESET_VAR, transportistaPass,
                    transportista.getContrasena());
            return;
        }

        if (transportistaPass == null) {
            logger.warn("No se crea el transportista demo {}: falta la variable {}.",
                    TRANSPORTISTA_EMAIL, TRANSPORTISTA_PASSWORD_VAR);
            return;
        }
        String errorPolitica = PasswordPolicy.validar(transportistaPass);
        if (errorPolitica != null) {
            logger.error("No se crea el transportista demo {}: {} no cumple PasswordPolicy: {}",
                    TRANSPORTISTA_EMAIL, TRANSPORTISTA_PASSWORD_VAR, errorPolitica);
            return;
        }

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
        transportista.setEmail(TRANSPORTISTA_EMAIL);
        transportista.setContrasena(passwordEncoder.encode(transportistaPass));
        transportista.addRole(Role.TRANSPORTISTA);
        transportista.addRole(Role.COMPRADOR);
        transportista.setPerfilTransportista(perfil);
        transportista.setEmailVerificado(true);

        usuarioRepository.save(transportista);
        logger.info("Usuario TRANSPORTISTA demo creado: {}. La contraseña se tomó de {} y no se imprime.",
                TRANSPORTISTA_EMAIL, TRANSPORTISTA_PASSWORD_VAR);
    }

    /**
     * Restablece la contraseña de un usuario existente solo si la variable de
     * fuerza está activa, la contraseña está definida, cumple PasswordPolicy y
     * todavía no coincide con el hash guardado.
     */
    private void restablecerContrasenaSiCorresponde(Usuario usuario, String etiqueta,
            String variablePassword, String variableForceReset, String password, String hashActual) {
        if (password == null) {
            logger.warn("{}: {} = true pero {} no está definida. No se restablece la contraseña.",
                    etiqueta, variableForceReset, variablePassword);
            return;
        }
        String errorPolitica = PasswordPolicy.validar(password);
        if (errorPolitica != null) {
            logger.warn("{}: {} no cumple PasswordPolicy: {}. No se restablece la contraseña.",
                    etiqueta, variablePassword, errorPolitica);
            return;
        }
        if (hashActual != null && passwordEncoder.matches(password, hashActual)) {
            logger.info("{}: la contraseña ya coincide con {}. No se restablece.", etiqueta, variablePassword);
            return;
        }

        usuario.setContrasena(passwordEncoder.encode(password));
        usuarioRepository.save(usuario);
        logger.warn("Contraseña de {} restablecida una vez por {} = true. "
                + "Desactiva esa variable después de este despliegue.", etiqueta, variableForceReset);
    }

    /** Lee una variable de entorno/propiedad sin imprimir su valor. */
    private String leerVariable(String nombre) {
        String valor = environment.getProperty(nombre);
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        return valor;
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
        if (ubicacionRepository != null && ubicacionRepository.count() == 0) {
            logger.info("📍 Sembrando departamentos y ciudades en UbicacionRepository...");
            List<CiudadUbicacion> bolivarCiudades = new ArrayList<>();
            for (ZonasLogistica.ZonaDef def : ZonasLogistica.ZONAS) {
                for (String muni : def.municipios()) {
                    bolivarCiudades.add(new CiudadUbicacion(muni.trim()));
                }
            }
            bolivarCiudades.sort((c1, c2) -> c1.getNombre().compareToIgnoreCase(c2.getNombre()));
            ubicacionRepository.save(new Ubicacion("Bolívar", bolivarCiudades));

            ubicacionRepository.save(new Ubicacion("Atlántico", List.of(
                    new CiudadUbicacion("Barranquilla"), new CiudadUbicacion("Soledad"), new CiudadUbicacion("Malambo"),
                    new CiudadUbicacion("Sabanalarga"), new CiudadUbicacion("Baranoa"), new CiudadUbicacion("Puerto Colombia"),
                    new CiudadUbicacion("Galapa")
            )));
            ubicacionRepository.save(new Ubicacion("Sucre", List.of(
                    new CiudadUbicacion("Sincelejo"), new CiudadUbicacion("Corozal"), new CiudadUbicacion("San Marcos"),
                    new CiudadUbicacion("Tolú"), new CiudadUbicacion("Coveñas"), new CiudadUbicacion("Sampués")
            )));
            ubicacionRepository.save(new Ubicacion("Magdalena", List.of(
                    new CiudadUbicacion("Santa Marta"), new CiudadUbicacion("Ciénaga"), new CiudadUbicacion("Fundación"),
                    new CiudadUbicacion("El Banco"), new CiudadUbicacion("Plato")
            )));
            ubicacionRepository.save(new Ubicacion("Antioquia", List.of(
                    new CiudadUbicacion("Medellín"), new CiudadUbicacion("Bello"), new CiudadUbicacion("Itagüí"),
                    new CiudadUbicacion("Envigado"), new CiudadUbicacion("Rionegro")
            )));
            ubicacionRepository.save(new Ubicacion("Cundinamarca", List.of(
                    new CiudadUbicacion("Bogotá D.C."), new CiudadUbicacion("Soacha"), new CiudadUbicacion("Chía"),
                    new CiudadUbicacion("Zipaquirá"), new CiudadUbicacion("Facatativá")
            )));
            logger.info("✅ Departamentos y ciudades sembrados exitosamente.");
        }

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