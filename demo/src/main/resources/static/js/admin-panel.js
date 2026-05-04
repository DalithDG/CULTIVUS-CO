document.addEventListener('DOMContentLoaded', function () {

    // ===== DOM ELEMENTS =====
    const sidebar = document.getElementById('sidebar');
    const menuBtn = document.getElementById('menuBtn');
    const mainContent = document.getElementById('mainContent');
    const menuLinks = document.querySelectorAll('.menu-link[data-submenu]');
    const menuItems = document.querySelectorAll('.menu-item.has-submenu');
    const pageTitle = document.getElementById('page-title');

    // ===== PAGE CONFIG =====
    const pageConfig = {
        'dashboard':        { title: 'Dashboard' },
        'usuarios':         { title: 'Gestionar Usuarios' },
        'roles':            { title: 'Roles y Permisos' },
        'actividad':        { title: 'Registro de Actividad' },
        'catalogo':         { title: 'Catálogo de Productos' },
        'inventario':       { title: 'Inventario' },
        'categorias':       { title: 'Categorías' },
        'agregar-producto':  { title: 'Agregar Nuevo Producto' },
        'ordenes':          { title: 'Órdenes' },
        'reportes':         { title: 'Reportes' },
        'clientes':         { title: 'Clientes' },
        'general':          { title: 'Configuración General' },
        'seguridad':        { title: 'Configuración de Seguridad' },
        'respaldos':        { title: 'Respaldos' },
        'integraciones':    { title: 'Integraciones' },
        'ayuda':            { title: 'Centro de Ayuda' },
        'verificacion-tiendas': { title: 'Verificación de Tiendas' }
    };

    // ===== NAVIGATE TO PAGE =====
    function navigateToPage(pageName) {
        // Ocultar todas las páginas
        document.querySelectorAll('.page').forEach(function (page) {
            page.style.display = 'none';
        });

        // Mostrar la página seleccionada
        var pageElement = document.getElementById(pageName + '-page');
        if (pageElement) {
            pageElement.style.display = 'block';

            // Actualizar título
            if (pageTitle && pageConfig[pageName]) {
                pageTitle.textContent = pageConfig[pageName].title;
            }

            // Scroll al inicio
            var content = document.querySelector('.content');
            if (content) content.scrollTop = 0;
        } else {
            // Si no existe la página SPA, mostrar dashboard por defecto
            var dash = document.getElementById('dashboard-page');
            if (dash) dash.style.display = 'block';
        }
    }

    // Exponer globalmente para que el inline script del HTML pueda usarla
    window.navigateToPage = navigateToPage;

    // ===== SIDEBAR TOGGLE =====
    if (menuBtn) {
        menuBtn.addEventListener('click', function () {
            sidebar.classList.toggle('collapsed');
            if (mainContent) mainContent.classList.toggle('collapsed');

            if (window.innerWidth <= 768) {
                sidebar.classList.toggle('open');
            }
        });
    }

    // ===== SUBMENU TOGGLE =====
    menuLinks.forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();

            var menuItem = link.closest('.menu-item');

            // Cerrar otros submenús
            menuItems.forEach(function (item) {
                if (item !== menuItem) {
                    item.classList.remove('open');
                }
            });

            // Toggle del submenú actual
            if (menuItem) menuItem.classList.toggle('open');
        });
    });

    // ===== PAGE NAVIGATION (links internos con data-page) =====
    document.querySelectorAll('[data-page]').forEach(function (link) {
        link.addEventListener('click', function (e) {
            var href = link.getAttribute('href');

            // Solo manejar internamente si el href es # (no es un enlace real al backend)
            if (!href || href === '#' || href.startsWith('#')) {
                e.preventDefault();

                var pageName = link.getAttribute('data-page');
                navigateToPage(pageName);

                // Cerrar submenús
                menuItems.forEach(function (item) {
                    item.classList.remove('open');
                });

                // Cerrar sidebar en mobile
                if (window.innerWidth <= 768) {
                    sidebar.classList.add('collapsed');
                    sidebar.classList.remove('open');
                }
            }
            // Si tiene href real (ej: /admin/usuarios), deja navegar al backend
        });
    });

    // ===== CERRAR SUBMENÚ AL HACER CLICK FUERA =====
    document.addEventListener('click', function (e) {
        if (!e.target.closest('.menu-item.has-submenu')) {
            menuItems.forEach(function (item) {
                item.classList.remove('open');
            });
        }
    });

    // ===== CERRAR SIDEBAR EN MOBILE AL HACER CLICK EN SUBMENU =====
    document.querySelectorAll('.submenu-link').forEach(function (link) {
        link.addEventListener('click', function () {
            if (window.innerWidth <= 768) {
                sidebar.classList.add('collapsed');
                sidebar.classList.remove('open');
            }
        });
    });

    // ===== RESIZE HANDLER =====
    window.addEventListener('resize', function () {
        if (window.innerWidth > 768) {
            if (sidebar) {
                sidebar.classList.remove('collapsed');
                sidebar.classList.remove('open');
            }
        }
    });

    // ===== KEYBOARD SHORTCUT: Ctrl+B para toggle sidebar =====
    document.addEventListener('keydown', function (e) {
        if ((e.ctrlKey || e.metaKey) && e.key === 'b') {
            e.preventDefault();
            if (menuBtn) menuBtn.click();
        }
    });

    // ===== STAT CARDS ANIMATION =====
    var statCards = document.querySelectorAll('.stat-card');
    if ('IntersectionObserver' in window) {
        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.style.opacity = '1';
                    entry.target.style.transform = 'translateY(0)';
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.1 });

        statCards.forEach(function (card) {
            card.style.opacity = '0';
            card.style.transform = 'translateY(20px)';
            card.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
            observer.observe(card);
        });
    }

    // ===== NOTIFICACIONES =====
    function showNotification(message, type) {
        type = type || 'info';
        var notification = document.createElement('div');
        notification.textContent = message;
        notification.style.cssText =
            'position:fixed;top:20px;right:20px;padding:15px 20px;' +
            'background:' + (type === 'success' ? '#4CAF50' : type === 'error' ? '#FF5252' : '#2196F3') + ';' +
            'color:white;border-radius:8px;box-shadow:0 4px 12px rgba(0,0,0,.2);' +
            'z-index:10000;font-size:14px;';
        document.body.appendChild(notification);
        setTimeout(function () { notification.remove(); }, 3000);
    }

    // Añadir animaciones CSS
    var style = document.createElement('style');
    style.textContent =
        '@keyframes fadeIn{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}' +
        '.page{animation:fadeIn .3s ease}';
    document.head.appendChild(style);

    console.log('✅ Panel Administrador cargado correctamente');
});
