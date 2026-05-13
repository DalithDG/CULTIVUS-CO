package com.example.demo.Model;

import com.example.demo.Model.embebidos.PerfilAdmin;
import com.example.demo.Model.embebidos.PerfilVendedor;
import com.example.demo.Model.embebidos.UbicacionUsuario;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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

    // Capacidad de múltiples roles: "ADMIN", "COMPRADOR", "VENDEDOR"
    @Field("roles")
    private Set<Role> roles = new HashSet<>();

    @Field("created_at")
    private LocalDateTime createdAt;

    // Ubicación embebida — reemplaza Ciudad y Departamento
    @Field("ubicacion")
    private UbicacionUsuario ubicacion;

    // Perfil de vendedor — disponible para cualquier usuario (rol flexible)
    @Field("perfil_vendedor")
    private PerfilVendedor perfilVendedor;

    // Perfil de administrador — disponible para cualquier usuario (rol flexible)
    @Field("perfil_admin")
    private PerfilAdmin perfilAdmin;

    @Field("ultima_conexion")
    private LocalDateTime ultimaConexion;

    // Constructores
    public Usuario() {
        this.createdAt = LocalDateTime.now();
    }

    public Usuario(String nombre, String email, String contrasena, Role rol) {
        this.nombre = nombre;
        this.email = email;
        this.contrasena = contrasena;
        this.roles = new HashSet<>();
        if (rol != null) {
            this.roles.add(rol);
        }
        this.createdAt = LocalDateTime.now();
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public Role getRol() {
        if (roles == null || roles.isEmpty()) return null;
        // Retornar el rol de mayor jerarquía o el primero para compatibilidad
        if (roles.contains(Role.ADMIN)) return Role.ADMIN;
        if (roles.contains(Role.VENDEDOR)) return Role.VENDEDOR;
        return roles.iterator().next();
    }

    public void setRol(Role nuevoRol) {
        if (this.roles == null) this.roles = new HashSet<>();
        this.roles.add(nuevoRol);
    }

    public void addRole(Role role) {
        if (this.roles == null) this.roles = new HashSet<>();
        this.roles.add(role);
    }

    public boolean hasRole(Role role) {
        return this.roles != null && this.roles.contains(role);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UbicacionUsuario getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(UbicacionUsuario ubicacion) {
        this.ubicacion = ubicacion;
    }

    public PerfilVendedor getPerfilVendedor() {
        return perfilVendedor;
    }

    public void setPerfilVendedor(PerfilVendedor perfilVendedor) {
        this.perfilVendedor = perfilVendedor;
    }

    public PerfilAdmin getPerfilAdmin() {
        return perfilAdmin;
    }

    public void setPerfilAdmin(PerfilAdmin perfilAdmin) {
        this.perfilAdmin = perfilAdmin;
    }

    public LocalDateTime getUltimaConexion() {
        return ultimaConexion;
    }

    public void setUltimaConexion(LocalDateTime ultimaConexion) {
        this.ultimaConexion = ultimaConexion;
    }
}