package com.example.demo.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Model.Producto;
import com.example.demo.repository.ProductoRepository;

@Service
public class ProductoServiceImpl implements ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Override
    public Producto crearProducto(Producto producto) {
        if (producto.getNombre() == null || producto.getNombre().isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío");
        }
        if (producto.getPrecio() == null || producto.getPrecio() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a cero");
        }
        if (producto.getStock() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
        if (producto.getPeso() == null || producto.getPeso() <= 0) {
            throw new IllegalArgumentException("El peso debe ser mayor a cero");
        }
        if (producto.getCategoria() == null) {
            throw new IllegalArgumentException("El producto debe tener una categoría");
        }
        if (producto.getUnidadMedida() == null) {
            throw new IllegalArgumentException("El producto debe tener una unidad de medida");
        }
        if (producto.getVendedor() == null) {
            throw new IllegalArgumentException("El producto debe tener un vendedor asociado");
        }

        // Valores automáticos
        producto.setDisponible(true);
        producto.setCreatedAt(LocalDateTime.now());

        return productoRepository.save(producto);
    }

    @Override
    public List<Producto> listarProductos() {
        return productoRepository.findAll();
    }

    @Override
    public List<Producto> listarDisponibles() {
        return productoRepository.findByDisponibleTrue();
    }

    @Override
    public Producto buscarPorId(String id) {
        Optional<Producto> producto = productoRepository.findById(id);
        return producto.orElse(null);
    }

    @Override
    public void eliminarProducto(String id) {
        if (!productoRepository.existsById(id)) {
            throw new IllegalArgumentException("Producto no encontrado con ID: " + id);
        }
        productoRepository.deleteById(id);
    }

    @Override
    public Producto actualizarProducto(Producto producto) {
        if (producto.getId() == null || producto.getId().isEmpty()) {
            throw new IllegalArgumentException("ID de producto inválido");
        }
        if (!productoRepository.existsById(producto.getId())) {
            throw new IllegalArgumentException("Producto no encontrado con ID: " + producto.getId());
        }
        if (producto.getNombre() == null || producto.getNombre().isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío");
        }
        if (producto.getPrecio() == null || producto.getPrecio() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a cero");
        }
        if (producto.getStock() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }

        // Actualizar fecha de modificación
        producto.setUpdatedAt(LocalDateTime.now());

        return productoRepository.save(producto);
    }

    @Override
    public List<Producto> buscarPorCategoria(String categoriaId) {
        return productoRepository.findByCategoriaId(categoriaId);
    }

    @Override
    public List<Producto> buscarPorVendedor(String vendedorId) {
        return productoRepository.findByVendedorId(vendedorId);
    }

    @Override
    public List<Producto> buscarPorNombre(String nombre) {
        return productoRepository.findByNombreContainingIgnoreCase(nombre);
    }

    @Override
    public List<Producto> buscarConStock() {
        return productoRepository.findByStockGreaterThan(0);
    }

    @Override
    public List<Producto> buscarPorRangoPrecio(Double precioMin, Double precioMax) {
        if (precioMin < 0 || precioMax < 0) {
            throw new IllegalArgumentException("Los precios no pueden ser negativos");
        }
        if (precioMin > precioMax) {
            throw new IllegalArgumentException("El precio mínimo no puede ser mayor al máximo");
        }
        return productoRepository.findByPrecioBetween(precioMin, precioMax);
    }
}