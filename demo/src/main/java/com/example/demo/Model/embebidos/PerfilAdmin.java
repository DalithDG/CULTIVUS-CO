package com.example.demo.Model.embebidos;

import java.time.LocalDateTime;

public class PerfilAdmin {

    private String nivelPermisos = "ADMIN"; // ADMIN, SUPER_ADMIN
    private boolean activo = true;
    private LocalDateTime fechaNombramiento;

    public PerfilAdmin() {
        this.fechaNombramiento = LocalDateTime.now();
    }

    public PerfilAdmin(String nivelPermisos) {
        this.nivelPermisos = nivelPermisos;
        this.activo = true;
        this.fechaNombramiento = LocalDateTime.now();
    }

    public String getNivelPermisos() { return nivelPermisos; }
    public void setNivelPermisos(String nivelPermisos) { this.nivelPermisos = nivelPermisos; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaNombramiento() { return fechaNombramiento; }
    public void setFechaNombramiento(LocalDateTime fechaNombramiento) { this.fechaNombramiento = fechaNombramiento; }
}