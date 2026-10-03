package com.example.demo.Config;

import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.AppConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataLoaderTest {

    private static final String ADMIN_EMAIL = "admin1@demo.com";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AppConfigService configService;

    @InjectMocks
    private DataLoader dataLoader;

    @Test
    void migrationEncodesTheExistingPlaintextPassword() {
        String existingPassword = "custom-admin-password";
        Usuario admin = new Usuario();
        admin.setContrasena(existingPassword);

        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode(existingPassword)).thenReturn("encoded-existing-password");

        dataLoader.run();

        assertEquals("encoded-existing-password", admin.getContrasena());
        verify(passwordEncoder).encode(existingPassword);
        verify(usuarioRepository).save(admin);
    }

    @Test
    void migrationLeavesAnEncodedPasswordUnchanged() {
        String encodedPassword = "$2a$existing-encoded-password";
        Usuario admin = new Usuario();
        admin.setContrasena(encodedPassword);

        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        dataLoader.run();

        assertEquals(encodedPassword, admin.getContrasena());
        verify(passwordEncoder, never()).encode(encodedPassword);
        verify(usuarioRepository, never()).save(admin);
    }
}