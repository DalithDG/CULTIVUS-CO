/**
 * Panel de administración de Cultivus.
 *
 * Cada sección es una página independiente: este script gestiona el
 * sidebar (drawer móvil a la izquierda y modo colapsado en escritorio),
 * los submenús, el menú de usuario y la adaptación responsiva de tablas
 * a tarjetas estructuradas en móvil.
 */
(function () {
    "use strict";

    var CLAVE_COLAPSADO = "cultivus-admin-sidebar-colapsado";
    var sidebar = document.getElementById("adminSidebar");
    var overlay = document.getElementById("adminOverlay");
    var toggle = document.getElementById("adminMenuToggle") || document.getElementById("admSidebarToggle");
    var closeBtn = document.getElementById("adminSidebarClose");

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

    if (closeBtn) {
        closeBtn.addEventListener("click", cerrarSidebar);
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

    // Menú desplegable de cuenta de administrador en topbar.
    var userMenu = document.getElementById("admUserMenu");
    var userTrigger = document.getElementById("admUserTrigger");

    function cerrarUserMenu() {
        if (!userMenu) return;
        userMenu.classList.remove("is-open");
        if (userTrigger) userTrigger.setAttribute("aria-expanded", "false");
    }

    if (userMenu && userTrigger) {
        userTrigger.addEventListener("click", function (e) {
            e.stopPropagation();
            var abierto = userMenu.classList.toggle("is-open");
            userTrigger.setAttribute("aria-expanded", abierto ? "true" : "false");
        });

        document.addEventListener("click", function (e) {
            if (!userMenu.contains(e.target)) {
                cerrarUserMenu();
            }
        });
    }

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
        if (evento.key === "Escape") {
            cerrarUserMenu();
            if (esMovil()) {
                cerrarSidebar();
            }
        }
    });

    // Adaptación inteligente de tablas (.data-table) para tarjetas móviles.
    function enriquecerTablasResponsivas() {
        document.querySelectorAll(".data-table").forEach(function (tabla) {
            var cabeceras = Array.prototype.map.call(
                tabla.querySelectorAll("thead th"),
                function (th) {
                    return (th.textContent || "").replace(/\s+/g, " ").trim();
                }
            );

            tabla.querySelectorAll("tbody tr").forEach(function (fila) {
                var celdas = fila.querySelectorAll("td");
                if (celdas.length === 1 && celdas[0].hasAttribute("colspan")) {
                    fila.classList.add("adm-tr-empty");
                    celdas[0].classList.add("adm-td-empty");
                    return;
                }

                celdas.forEach(function (td, indice) {
                    var etiqueta = td.getAttribute("data-label") || cabeceras[indice] || "";
                    if (etiqueta) {
                        td.setAttribute("data-label", etiqueta);
                    }

                    var etiquetaLower = etiqueta.toLowerCase();

                    if (
                        etiquetaLower.indexOf("acci") !== -1 ||
                        td.querySelector(".adm-actions, .adm-role-form, .adm-sug-actions, form")
                    ) {
                        td.classList.add("adm-td-actions");
                    } else if (
                        td.querySelector(".adm-detail-list, .adm-seller-offer-card") ||
                        etiquetaLower === "detalle" ||
                        etiquetaLower === "comentario" ||
                        etiquetaLower === "contenido" ||
                        etiquetaLower === "mensaje" ||
                        etiquetaLower.indexOf("oferta") !== -1
                    ) {
                        td.classList.add("adm-td-block");
                    }
                });
            });
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", enriquecerTablasResponsivas);
    } else {
        enriquecerTablasResponsivas();
    }
})();
