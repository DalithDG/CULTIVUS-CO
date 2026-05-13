package com.example.demo.services;

import com.example.demo.Model.AppConfig;
import com.example.demo.repository.AppConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AppConfigService {

    @Autowired
    private AppConfigRepository configRepository;

    public List<AppConfig> obtenerTodas() {
        return configRepository.findAll();
    }

    public String obtenerValor(String clave, String valorPorDefecto) {
        return configRepository.findByClave(clave)
                .map(AppConfig::getValor)
                .orElse(valorPorDefecto);
    }

    public double obtenerValorDouble(String clave, double valorPorDefecto) {
        try {
            String valor = obtenerValor(clave, String.valueOf(valorPorDefecto));
            return Double.parseDouble(valor);
        } catch (NumberFormatException e) {
            return valorPorDefecto;
        }
    }

    public int obtenerValorInt(String clave, int valorPorDefecto) {
        try {
            String valor = obtenerValor(clave, String.valueOf(valorPorDefecto));
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return valorPorDefecto;
        }
    }

    public void guardarConfig(String clave, String valor, String descripcion, String tipo) {
        AppConfig config = configRepository.findByClave(clave)
                .orElse(new AppConfig(clave, valor, descripcion, tipo));
        
        config.setValor(valor);
        config.setDescripcion(descripcion);
        config.setTipo(tipo);
        
        configRepository.save(config);
    }

    public void actualizarValor(String id, String nuevoValor) {
        configRepository.findById(id).ifPresent(config -> {
            config.setValor(nuevoValor);
            configRepository.save(config);
        });
    }
}
