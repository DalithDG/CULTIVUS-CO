package com.example.demo.services;

import com.example.demo.Model.Notificacion;
import com.example.demo.repository.NotificacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificacionService {

    @Autowired
    private NotificacionRepository notificacionRepository;

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
}
