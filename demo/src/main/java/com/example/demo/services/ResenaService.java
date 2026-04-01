package com.example.demo.services;

import com.example.demo.Model.Resena;
import com.example.demo.repository.PedidoRepository;
import com.example.demo.repository.ResenaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ResenaService {

    @Autowired
    private ResenaRepository resenaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    // Crear una nueva reseña
    public Resena crearResena(String pedidoId, String productoId, String usuarioId,
                               int calificacion, String comentario) {

        // Validar calificación entre 1 y 5
        if (calificacion < 1 || calificacion > 5) {
            throw new IllegalArgumentException("La calificación debe estar entre 1 y 5");
        }

        // Verificar que no exista ya una reseña para este pedido y producto
        if (resenaRepository.existsByPedidoIdAndProductoId(pedidoId, productoId)) {
            throw new IllegalStateException("Ya existe una reseña para este producto en este pedido");
        }

        Resena resena = new Resena();
        resena.setPedidoId(pedidoId);
        resena.setProductoId(productoId);
        resena.setUsuarioId(usuarioId);
        resena.setCalificacion(calificacion);
        resena.setComentario(comentario);
        resena.setFecha(LocalDate.now());

        return resenaRepository.save(resena);
    }

    // Obtener reseñas por producto
    public List<Resena> obtenerResenasPorProducto(String productoId) {
        return resenaRepository.findByProductoId(productoId);
    }

    // Obtener reseñas por pedido
    public List<Resena> obtenerResenasPorPedido(String pedidoId) {
        return resenaRepository.findByPedidoId(pedidoId);
    }

    // Obtener reseñas por usuario
    public List<Resena> obtenerResenasPorUsuario(String usuarioId) {
        return resenaRepository.findByUsuarioId(usuarioId);
    }

    // Calcular promedio de calificaciones de un producto
    public double calcularPromedioCalificacion(String productoId) {
        List<Resena> resenas = resenaRepository.findByProductoId(productoId);
        return resenas.stream()
                .mapToInt(Resena::getCalificacion)
                .average()
                .orElse(0.0);
    }

    // Contar reseñas de un producto
    public long contarResenas(String productoId) {
        return resenaRepository.countByProductoId(productoId);
    }

    // Verificar si un usuario puede reseñar un producto en un pedido
    public boolean puedeResenar(String usuarioId, String productoId, String pedidoId) {
        // Verificar que el pedido pertenece al usuario
        return pedidoRepository.findById(pedidoId)
                .map(pedido -> {
                    // Verificar que el pedido sea del usuario
                    boolean esDueno = usuarioId.equals(pedido.getComprador().getId());

                    // Verificar que el pedido contiene el producto
                    boolean contieneProducto = pedido.getItems().stream()
                            .anyMatch(item -> productoId.equals(item.getProductoId()));

                    // Verificar que no haya reseñado ya
                    boolean yaReseno = resenaRepository
                            .existsByPedidoIdAndProductoId(pedidoId, productoId);

                    return esDueno && contieneProducto && !yaReseno;
                })
                .orElse(false);
    }

    // Obtener últimas reseñas
    public List<Resena> obtenerUltimasResenas() {
        return resenaRepository.findTop10ByOrderByFechaDesc();
    }

    // Eliminar reseña (solo el autor puede eliminarla)
    public void eliminarResena(String resenaId, String usuarioId) {
        Resena resena = resenaRepository.findById(resenaId)
                .orElseThrow(() -> new IllegalStateException("Reseña no encontrada"));

        // Verificar que el usuario sea el autor
        if (!resena.getUsuarioId().equals(usuarioId)) {
            throw new IllegalStateException("No tienes permiso para eliminar esta reseña");
        }

        resenaRepository.delete(resena);
    }

    // Obtener reseña por ID
    public Optional<Resena> obtenerResenaPorId(String resenaId) {
        return resenaRepository.findById(resenaId);
    }
}