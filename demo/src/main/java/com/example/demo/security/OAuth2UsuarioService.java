package com.example.demo.security;

import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Servicio que se ejecuta después de que Google autentica al usuario.
 * Recibe los datos del perfil de Google (email, nombre, foto) y:
 *   - Si el usuario ya existe en MongoDB → lo actualiza si es necesario
 *   - Si es la primera vez → crea automáticamente un Usuario nuevo
 */
@Service
public class OAuth2UsuarioService extends DefaultOAuth2UserService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Delegate la carga del perfil de Google al servicio base
        OAuth2User oAuth2User = super.loadUser(userRequest);

        procesarUsuarioOAuth2(oAuth2User);

        return oAuth2User;
    }

    /**
     * Busca el usuario por email. Si no existe, lo crea con rol COMPRADOR.
     * Si ya existe, actualiza su nombre si cambió en Google.
     */
    private void procesarUsuarioOAuth2(OAuth2User oAuth2User) {
        String email  = oAuth2User.getAttribute("email");
        String nombre = oAuth2User.getAttribute("name");

        if (email == null) {
            throw new OAuth2AuthenticationException("Google no proporcionó un email válido");
        }

        String emailLimpio = email.trim().toLowerCase();

        Optional<Usuario> usuarioExistente = usuarioRepository.findByEmail(emailLimpio);

        if (usuarioExistente.isPresent()) {
            // Usuario ya registrado — actualizar nombre si cambió en Google
            Usuario usuario = usuarioExistente.get();
            if (nombre != null && !nombre.equals(usuario.getNombre())) {
                usuario.setNombre(nombre);
                usuarioRepository.save(usuario);
            }
        } else {
            // Primera vez con Google → crear cuenta automáticamente
            Usuario nuevoUsuario = new Usuario();
            nuevoUsuario.setEmail(emailLimpio);
            nuevoUsuario.setNombre(nombre != null ? nombre : emailLimpio);
            // Sin contraseña: este usuario solo puede autenticarse vía Google
            nuevoUsuario.setContrasena(null);
            nuevoUsuario.setRol("COMPRADOR");

            usuarioRepository.save(nuevoUsuario);
        }
    }
}
