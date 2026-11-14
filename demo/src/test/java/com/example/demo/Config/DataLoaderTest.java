package com.example.demo.Config;

import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.services.AppConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataLoaderTest {

    private static final String ADMIN_EMAIL = "admin1@demo.com";
    private static final String NUEVA_CLAVE = "NuevaClave1!";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AppConfigService configService;

    @Mock
    private Environment environment;

    @Mock
    private com.example.demo.services.CatalogoInicializador catalogoInicializador;

    @InjectMocks
    private DataLoader dataLoader;

    @Test
    void migrationEncodesTheExistingPlaintextPassword() {
        String existingPassword = "custom-admin-password";
        Usuario admin = new Usuario();
        admin.setContrasena(existingPassword);
        admin.setEmailVerificado(true);

        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode(existingPassword)).thenReturn("encoded-existing-password");

        dataLoader.run();

        assertEquals("encoded-existing-password", admin.getContrasena());
        verify(passwordEncoder).encode(existingPassword);
        verify(usuarioRepository).save(admin);
    }

    @Test
    void migrationLeavesAnEncodedPasswordUnchangedWithoutForceReset() {
        String encodedPassword = "$2a$existing-encoded-password";
        Usuario admin = new Usuario();
        admin.setContrasena(encodedPassword);
        admin.setEmailVerificado(true);

        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        dataLoader.run();

        assertEquals(encodedPassword, admin.getContrasena());
        verify(passwordEncoder, never()).encode(any());
        verify(usuarioRepository, never()).save(admin);
    }

    @Test
    void forceResetUpdatesTheHashWhenThePasswordIsValid() {
        String encodedPassword = "$2a$existing-encoded-password";
        String nuevoHash = "$2a$new-encoded-password";
        Usuario admin = new Usuario();
        admin.setContrasena(encodedPassword);
        admin.setEmailVerificado(true);

        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(environment.getProperty("ADMIN_PASSWORD")).thenReturn(NUEVA_CLAVE);
        when(environment.getProperty("ADMIN_PASSWORD_FORCE_RESET")).thenReturn("true");
        when(passwordEncoder.encode(NUEVA_CLAVE)).thenReturn(nuevoHash);

        dataLoader.run();

        assertEquals(nuevoHash, admin.getContrasena());
        verify(usuarioRepository).save(admin);
    }

    @Test
    void forceResetDoesNothingWhenTheAdminPasswordIsMissing() {
        String encodedPassword = "$2a$existing-encoded-password";
        Usuario admin = new Usuario();
        admin.setContrasena(encodedPassword);
        admin.setEmailVerificado(true);

        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(environment.getProperty("ADMIN_PASSWORD_FORCE_RESET")).thenReturn("true");

        dataLoader.run();

        assertEquals(encodedPassword, admin.getContrasena());
        verify(passwordEncoder, never()).encode(any());
        verify(usuarioRepository, never()).save(admin);
    }

    @Test
    void createsTheAdminWhenTheEnvironmentPasswordIsValid() {
        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(environment.getProperty("ADMIN_PASSWORD")).thenReturn(NUEVA_CLAVE);
        when(passwordEncoder.encode(NUEVA_CLAVE)).thenReturn("$2a$created");

        dataLoader.run();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals(ADMIN_EMAIL, captor.getValue().getEmail());
        assertEquals("$2a$created", captor.getValue().getContrasena());
    }

    @Test
    void skipsAdminCreationWithoutAPassword() {
        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());

        dataLoader.run();

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void doesNotChangeTheAdminPasswordWhenForceResetIsNotTrue() {
        String encodedPassword = "$2a$existing-encoded-password";
        Usuario admin = new Usuario();
        admin.setContrasena(encodedPassword);
        admin.setEmailVerificado(true);

        when(usuarioRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(environment.getProperty("ADMIN_PASSWORD")).thenReturn(NUEVA_CLAVE);
        when(environment.getProperty("ADMIN_PASSWORD_FORCE_RESET")).thenReturn("false");

        dataLoader.run();

        assertEquals(encodedPassword, admin.getContrasena());
        verify(passwordEncoder, never()).encode(any());
        verify(usuarioRepository, never()).save(admin);
        assertTrue(admin.isEmailVerificado());
    }
}
