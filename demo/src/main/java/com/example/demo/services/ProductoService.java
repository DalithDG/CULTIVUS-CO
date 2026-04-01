package com.example.demo.services;

import java.util.List;
import com.example.demo.Model.Producto;

public interface ProductoService {

    Producto crearProducto(Producto producto);
    List<Producto> listarProductos();
    Producto buscarPorId(String id);
    void eliminarProducto(String id);
    Producto actualizarProducto(Producto producto);
    List<Producto> buscarPorCategoria(String categoriaId);
    List<Producto> buscarPorVendedor(String vendedorId);
    List<Producto> buscarPorNombre(String nombre);
    List<Producto> buscarConStock();
    List<Producto> buscarPorRangoPrecio(Double precioMin, Double precioMax);
    List<Producto> listarDisponibles();
}