package com.example.demo.config;

import com.example.demo.Model.Usuario;
import com.example.demo.Model.embebidos.PerfilAdmin;
import com.example.demo.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    // Temporalmente desactivado
    // @Autowired
    // private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            logger.info("🚀 Iniciando DataLoader...");

            // Crear usuario ADMIN si no existe
            if (!usuarioRepository.existsByEmail("admin1@demo.com")) {

                // Crear perfil admin embebido
                PerfilAdmin perfilAdmin = new PerfilAdmin("SUPER_ADMIN");
                perfilAdmin.setActivo(true);

                // Crear usuario admin con perfil embebido
                Usuario admin = new Usuario();
                admin.setNombre("Administrador");
                admin.setEmail("admin1@demo.com");
                admin.setContrasena("admin1234"); // Temporal: sin encriptar
                admin.setRol("ADMIN");
                admin.setPerfilAdmin(perfilAdmin);

                usuarioRepository.save(admin);

                logger.info("✅ Usuario ADMIN creado:");
                logger.info("   📧 Email: admin1@demo.com");
                logger.info("   🔑 Contraseña: admin1234");
                System.out.println("✅ Usuario ADMIN creado:");
                System.out.println("   📧 Email: admin1@demo.com");
                System.out.println("   🔑 Contraseña: admin1234");

            } else {
                logger.info("ℹ️ Usuario ADMIN ya existe");
                System.out.println("ℹ️ Usuario ADMIN ya existe");
            }

            logger.info("✅ DataLoader finalizado correctamente");

        } catch (Exception e) {
            logger.error("❌ Error en DataLoader: ", e);
            System.err.println("❌ Error en DataLoader: " + e.getMessage());
        }
    }
}