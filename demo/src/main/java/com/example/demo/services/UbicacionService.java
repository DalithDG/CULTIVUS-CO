package com.example.demo.services;

import com.example.demo.Model.Ubicacion;
import com.example.demo.Model.embebidos.CiudadUbicacion;
import com.example.demo.repository.UbicacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UbicacionService {

    @Autowired
    private UbicacionRepository ubicacionRepository;

    // Obtener todos los departamentos
    public List<Ubicacion> obtenerTodos() {
        return ubicacionRepository.findAll();
    }

    // Obtener departamento por ID
    public Optional<Ubicacion> obtenerPorId(String id) {
        return ubicacionRepository.findById(id);
    }

    // Obtener departamento por nombre
    public Optional<Ubicacion> obtenerPorNombre(String nombre) {
        return ubicacionRepository.findByNombre(nombre);
    }

    // Obtener ciudades de un departamento
    public List<CiudadUbicacion> obtenerCiudadesPorDepartamento(String departamentoId) {
        return ubicacionRepository.findById(departamentoId)
                .map(Ubicacion::getCiudades)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Departamento no encontrado con ID: " + departamentoId));
    }

    // Obtener ciudades de un departamento por nombre
    public List<CiudadUbicacion> obtenerCiudadesPorNombreDepartamento(String nombreDepartamento) {
        return ubicacionRepository.findByNombre(nombreDepartamento)
                .map(Ubicacion::getCiudades)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Departamento no encontrado: " + nombreDepartamento));
    }

    // Crear un nuevo departamento con sus ciudades
    public Ubicacion crearUbicacion(String nombreDepartamento,
                                     List<CiudadUbicacion> ciudades) {
        if (ubicacionRepository.existsByNombre(nombreDepartamento)) {
            throw new IllegalArgumentException(
                    "Ya existe un departamento con el nombre: " + nombreDepartamento);
        }

        Ubicacion ubicacion = new Ubicacion(nombreDepartamento, ciudades);
        return ubicacionRepository.save(ubicacion);
    }

    // Agregar una ciudad a un departamento existente
    public Ubicacion agregarCiudad(String departamentoId, String nombreCiudad) {
        Ubicacion ubicacion = ubicacionRepository.findById(departamentoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Departamento no encontrado con ID: " + departamentoId));

        CiudadUbicacion nuevaCiudad = new CiudadUbicacion(nombreCiudad);
        ubicacion.getCiudades().add(nuevaCiudad);

        return ubicacionRepository.save(ubicacion);
    }

    // Eliminar un departamento
    public void eliminarUbicacion(String id) {
        if (!ubicacionRepository.existsById(id)) {
            throw new IllegalArgumentException(
                    "Departamento no encontrado con ID: " + id);
        }
        ubicacionRepository.deleteById(id);
    }

    // Verificar si existe un departamento
    public boolean existePorNombre(String nombre) {
        return ubicacionRepository.existsByNombre(nombre);
    }
}