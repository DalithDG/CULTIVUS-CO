package com.example.demo.Controller;

import com.example.demo.Model.Notificacion;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.NotificacionRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @ModelAttribute("notificacionesCount")
    public long getNotificacionesCount(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null) {
            List<Notificacion> notificaciones = notificacionRepository.findByUsuarioIdOrderByFechaDesc(usuario.getId());
            long count = notificaciones.stream().filter(n -> !n.isLeida()).count();
            
            // También actualizar en la sesión para mayor consistencia
            session.setAttribute("notificacionesCount", count);
            return count;
        }
        return 0;
    }
}
