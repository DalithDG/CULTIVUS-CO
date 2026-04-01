package com.example.demo.Model;

import com.example.demo.Model.embebidos.PerfilAdmin;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.Model.embebidos.UbicacionUsuario;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;

    @Field("nombre")
    private String nombre;

    @Indexed(unique = true)
    @Field("email")
    private String email;

    @Field("contrasena")
    private String contrasena;

    // "ADMIN", "COMPRADOR", "VENDEDOR"
    @Field("rol")
    private String rol;

    @Field("created_at")
    private LocalDateTime createdAt;

    // Ubicación embebida — reemplaza Ciudad y Departamento
    @Field("ubicacion")
    private UbicacionUsuario ubicacion;

    // Solo si rol == "VENDEDOR"
    @Field("perfil_vendedor")
    private PerfilVendedor perfilVendedor;

    // Solo si rol == "ADMIN"
    @Field("perfil_admin")
    private PerfilAdmin perfilAdmin;

    // Constructores
    public Usuario() {
        this.createdAt = LocalDateTime.now();
    }

    public Usuario(String nombre, String email, String contrasena, String rol) {
        this.nombre = nombre;
        this.email = email;
        this.contrasena = contrasena;
        this.rol = rol;
        this.createdAt = LocalDateTime.now();
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public UbicacionUsuario getUbicacion() { return ubicacion; }
    public void setUbicacion(UbicacionUsuario ubicacion) { this.ubicacion = ubicacion; }

    public PerfilVendedor getPerfilVendedor() { return perfilVendedor; }
    public void setPerfilVendedor(PerfilVendedor perfilVendedor) { this.perfilVendedor = perfilVendedor; }

    public PerfilAdmin getPerfilAdmin() { return perfilAdmin; }
    public void setPerfilAdmin(PerfilAdmin perfilAdmin) { this.perfilAdmin = perfilAdmin; }
}