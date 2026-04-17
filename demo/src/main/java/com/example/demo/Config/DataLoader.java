package com.example.demo.Config;

import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilAdmin;
import com.example.demo.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL    = "admin1@demo.com";
    private static final String ADMIN_PASSWORD = "Admin1234!";

    @Override
    public void run(String... args) {
        try {
            logger.info("🚀 Iniciando DataLoader...");

            usuarioRepository.findByEmail(ADMIN_EMAIL).ifPresentOrElse(
                admin -> migrarAdminSiEsNecesario(admin),
                () -> crearAdmin()
            );

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
            admin.setContrasena(passwordEncoder.encode(ADMIN_PASSWORD));
            usuarioRepository.save(admin);
            logger.info("✅ Contraseña del admin migrada a BCrypt correctamente");
            System.out.println("✅ Admin migrado. Nueva contraseña: " + ADMIN_PASSWORD);
        } else {
            logger.info("ℹ️  Usuario ADMIN ya existe con contraseña hasheada. Sin cambios.");
        }
    }

    private void crearAdmin() {
        PerfilAdmin perfilAdmin = new PerfilAdmin("SUPER_ADMIN");
        perfilAdmin.setActivo(true);

        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setEmail(ADMIN_EMAIL);
        admin.setContrasena(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRol("ADMIN");
        admin.setPerfilAdmin(perfilAdmin);

        usuarioRepository.save(admin);

        logger.info("✅ Usuario ADMIN creado:");
        logger.info("   📧 Email: {}", ADMIN_EMAIL);
        logger.info("   🔑 Contraseña: {}", ADMIN_PASSWORD);
        System.out.println("✅ Usuario ADMIN creado:");
        System.out.println("   📧 Email: " + ADMIN_EMAIL);
        System.out.println("   🔑 Contraseña: " + ADMIN_PASSWORD);
    }
}