/**
 * Panel de administración de Cultivus.
 *
 * Cada sección es una página independiente: este script solo gestiona el
 * sidebar, los submenús y el estado colapsado. No oculta ni muestra páginas.
 */
(function () {
    "use strict";

    var CLAVE_COLAPSADO = "cultivus-admin-sidebar-colapsado";
    var sidebar = document.getElementById("adminSidebar");
    var overlay = document.getElementById("adminOverlay");
    var toggle = document.getElementById("adminMenuToggle");

    function esMovil() {
        return window.innerWidth <= 1024;
    }

    function actualizarAria(abierto) {
        if (toggle) {
            toggle.setAttribute("aria-expanded", abierto ? "true" : "false");
        }
    }

    function abrirSidebar() {
        if (!sidebar) return;
        sidebar.classList.add("is-open");
        if (overlay) overlay.classList.add("is-visible");
        document.body.style.overflow = "hidden";
        actualizarAria(true);
    }

    function cerrarSidebar() {
        if (!sidebar) return;
        sidebar.classList.remove("is-open");
        if (overlay) overlay.classList.remove("is-visible");
        document.body.style.overflow = "";
        actualizarAria(false);
    }

    function alternarSidebar() {
        if (!sidebar) return;

        if (esMovil()) {
            if (sidebar.classList.contains("is-open")) {
                cerrarSidebar();
            } else {
                abrirSidebar();
            }
            return;
        }

        var colapsado = document.body.classList.toggle("sidebar-collapsed");
        localStorage.setItem(CLAVE_COLAPSADO, colapsado ? "1" : "0");
        actualizarAria(!colapsado);
    }

    if (toggle) {
        toggle.addEventListener("click", alternarSidebar);
    }

    if (overlay) {
        overlay.addEventListener("click", cerrarSidebar);
    }

    // Restaurar preferencia de sidebar colapsado en escritorio.
    if (!esMovil() && localStorage.getItem(CLAVE_COLAPSADO) === "1") {
        document.body.classList.add("sidebar-collapsed");
        actualizarAria(false);
    }

    // Submenús: abrir/cerrar el grupo y actualizar aria-expanded.
    document.querySelectorAll(".admin-nav-parent").forEach(function (boton) {
        boton.addEventListener("click", function () {
            var grupo = boton.closest(".admin-nav-group");
            if (!grupo) return;

            var abierto = grupo.classList.toggle("is-open");
            boton.setAttribute("aria-expanded", abierto ? "true" : "false");
        });
    });

    // En móvil, al navegar se cierra el drawer.
    document.querySelectorAll(".admin-sidebar a").forEach(function (enlace) {
        enlace.addEventListener("click", function () {
            if (esMovil()) cerrarSidebar();
        });
    });

    // Al volver a escritorio, limpiar el estado móvil.
    var ultimoModoMovil = esMovil();
    window.addEventListener("resize", function () {
        var ahoraMovil = esMovil();
        if (ultimoModoMovil && !ahoraMovil) {
            cerrarSidebar();
        }
        ultimoModoMovil = ahoraMovil;
    });

    // Cerrar con Escape.
    document.addEventListener("keydown", function (evento) {
        if (evento.key === "Escape" && esMovil()) {
            cerrarSidebar();
        }
    });
})();
