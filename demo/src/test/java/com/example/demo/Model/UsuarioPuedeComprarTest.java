package com.example.demo.Model;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class UsuarioPuedeComprarTest {

    @Test
    void compradorPuroPuedeComprar() {
        Usuario usuario = new Usuario();
        usuario.setRoles(Set.of(Role.COMPRADOR));
        assertTrue(usuario.puedeComprar());
    }

    @Test
    void usuarioSinRolesPuedeComprar() {
        Usuario usuario = new Usuario();
        assertTrue(usuario.puedeComprar());
    }

    @Test
    void vendedorNoPuedeComprar() {
        Usuario usuario = new Usuario();
        usuario.setRoles(Set.of(Role.COMPRADOR, Role.VENDEDOR));
        assertFalse(usuario.puedeComprar());
    }

    @Test
    void vendedorSoloNoPuedeComprar() {
        Usuario usuario = new Usuario();
        usuario.setRoles(Set.of(Role.VENDEDOR));
        assertFalse(usuario.puedeComprar());
    }

    @Test
    void transportistaPuedeComprarSiNoEsVendedor() {
        Usuario usuario = new Usuario();
        usuario.setRoles(Set.of(Role.COMPRADOR, Role.TRANSPORTISTA));
        assertTrue(usuario.puedeComprar());
    }

    @Test
    void adminPuedeComprarSiNoEsVendedor() {
        Usuario usuario = new Usuario();
        usuario.setRoles(Set.of(Role.ADMIN));
        assertTrue(usuario.puedeComprar());
    }
}

