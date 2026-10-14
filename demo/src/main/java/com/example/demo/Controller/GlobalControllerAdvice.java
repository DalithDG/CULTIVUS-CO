package com.example.demo.Controller;

import com.example.demo.Model.Notificacion;
import com.example.demo.Model.Usuario;
import com.example.demo.repository.CarritoRepository;
import com.example.demo.repository.CategoriaRepository;
import com.example.demo.repository.NotificacionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @ModelAttribute("notificacionesCount")
    public long getNotificacionesCount(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.getId() != null) {
            return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuario.getId());
        }
        return 0;
    }

    @ModelAttribute("carritoCount")
    public int getCarritoCount(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario != null && usuario.getId() != null) {
            return carritoRepository.findByUsuarioId(usuario.getId())
                    .map(c -> c.getItems() != null ? c.getItems().size() : 0)
                    .orElse(0);
        }
        return 0;
    }

    @ModelAttribute("categorias")
    public List<com.example.demo.Model.Categoria> getCategorias() {
        return categoriaRepository.findAll();
    }

    @ModelAttribute("currentPath")
    public String getCurrentPath(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return "";
        }
        return request.getRequestURI();
    }
}

