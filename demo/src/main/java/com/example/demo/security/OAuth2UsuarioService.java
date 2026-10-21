package com.example.demo.security;

import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Servicio que se ejecuta después de que un proveedor OAuth2 (Google o
 * Facebook) autentica al usuario.
 * Recibe los datos del perfil (email, nombre, foto) y:
 * - Si el usuario ya existe en MongoDB → lo actualiza si es necesario
 * - Si es la primera vez → crea automáticamente un Usuario nuevo
 */
@Service
public class OAuth2UsuarioService extends DefaultOAuth2UserService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Delegate la carga del perfil de Google al servicio base
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Usuario usuario = procesarUsuarioOAuth2(oAuth2User);

        // Reconstruir las autoridades desde los roles guardados en MongoDB
        java.util.Collection<org.springframework.security.core.GrantedAuthority> authorities =
                usuario.getRoles().stream()
                        .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol.name()))
                        .collect(java.util.stream.Collectors.toList());

        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(), nameAttributeKey);
    }

    /**
     * Busca el usuario por email. Si no existe, lo crea con rol COMPRADOR.
     * Si ya existe, actualiza su nombre si cambió en el proveedor.
     */
    private Usuario procesarUsuarioOAuth2(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String nombre = oAuth2User.getAttribute("name");

        if (email == null) {
            throw new OAuth2AuthenticationException("El proveedor no proporcionó un email válido");
        }

        String emailLimpio = email.trim().toLowerCase();

        Optional<Usuario> usuarioExistente = usuarioRepository.findByEmail(emailLimpio);

        if (usuarioExistente.isPresent()) {
            // Usuario ya registrado — actualizar nombre si cambió en el proveedor
            Usuario usuario = usuarioExistente.get();
            if (nombre != null && !nombre.equals(usuario.getNombre())) {
                usuario.setNombre(nombre);
            }
            usuario.setUltimaConexion(java.time.LocalDateTime.now());
            usuarioRepository.save(usuario);
            return usuario;
        } else {
            // Primera vez con OAuth2 → crear cuenta automáticamente
            Usuario nuevoUsuario = new Usuario();
            nuevoUsuario.setEmail(emailLimpio);
            nuevoUsuario.setNombre(nombre != null ? nombre : emailLimpio);
            // Sin contraseña: este usuario solo puede autenticarse vía OAuth2
            nuevoUsuario.setContrasena(null);
            nuevoUsuario.setRol(Role.COMPRADOR);
            nuevoUsuario.setUltimaConexion(java.time.LocalDateTime.now());

            usuarioRepository.save(nuevoUsuario);
            return nuevoUsuario;
        }
    }
}
