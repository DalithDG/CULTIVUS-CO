package com.example.demo.services;

import com.example.demo.Model.Notificacion;
import com.example.demo.Model.Role;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.NotificacionRepository;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificacionService {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    /**
     * Envía una notificación a un usuario específico
     */
    public void enviar(String usuarioId, String titulo, String mensaje, String tipo) {
        Notificacion notificacion = new Notificacion(titulo, mensaje, tipo);
        notificacion.setUsuarioId(usuarioId);
        notificacionRepository.save(notificacion);
    }

    /**
     * Envía una notificación general (para todos los admins o usuarios)
     */
    public void enviarGeneral(String titulo, String mensaje, String tipo) {
        Notificacion notificacion = new Notificacion(titulo, mensaje, tipo);
        notificacion.setUsuarioId(null);
        notificacionRepository.save(notificacion);
    }

    /**
     * Envía una notificación a cada usuario con rol ADMIN.
     * No usa enviarGeneral porque las generales (usuarioId = null) las ven todos los usuarios.
     * Si no hay administradores registrados, se deja como notificación general para no perderla.
     */
    public void enviarAAdmins(String titulo, String mensaje, String tipo) {
        List<Usuario> admins = usuarioRepository != null
                ? usuarioRepository.findByRolesContaining(Role.ADMIN)
                : List.of();
        if (admins == null || admins.isEmpty()) {
            enviarGeneral(titulo, mensaje, tipo);
            return;
        }
        for (Usuario admin : admins) {
            enviar(admin.getId(), titulo, mensaje, tipo);
        }
    }
}
