// 1. Función de navegación disponible globalmente de inmediato
window.navigateToPage = function(pageName) {
    if (!pageName) pageName = 'dashboard';
    
    // Normalizar alias
    if (pageName === 'pedidos') pageName = 'ordenes';
    if (pageName === 'resenas') pageName = 'actividad';
    
    console.log('Navigating to:', pageName);

    // Ocultar todas las páginas
    const pages = document.querySelectorAll('.page');
    pages.forEach(p => p.style.display = 'none');

    // Títulos de las páginas
    const titles = {
        'dashboard': 'Bienvenido al Panel Administrador',
        'usuarios': 'Gestionar Usuarios',
        'verificacion-tiendas': 'Verificación de Tiendas',
        'sesiones': 'Sesiones Activas',
        'notificaciones': 'Gestión de Notificaciones',
        'mensajes': 'Centro de Mensajes',
        'catalogo': 'Catálogo de Productos',
        'configuracion': 'Configuración del Sistema',
        'moderacion': 'Moderar Ofertas y Catálogo',
        'ordenes': 'Gestión de Pedidos',
        'transportistas': 'Gestión de Transportistas',
        'powerbi': 'Inteligencia de Negocios (Power BI)',
        'actividad': 'Actividad y Reseñas',
        'ofertas': 'Ofertas Globales'
    };

    const targetPage = document.getElementById(pageName + '-page');
    const titleElem = document.getElementById('page-title');

    if (targetPage) {
        targetPage.style.display = 'block';
        if (titleElem && titles[pageName]) {
            titleElem.textContent = titles[pageName];
        }
    } else {
        const dash = document.getElementById('dashboard-page');
        if (dash) dash.style.display = 'block';
        if (titleElem) titleElem.textContent = titles['dashboard'];
    }

    // Actualizar clase activa en el sidebar
    document.querySelectorAll('.menu-item').forEach(item => {
        // No remover 'open' de los menús desplegables si contienen la página activa
        item.classList.remove('active');
    });
    document.querySelectorAll('.submenu-link').forEach(link => link.classList.remove('active'));

    const activeLink = document.querySelector(`[data-page="${pageName}"]`);
    if (activeLink) {
        activeLink.classList.add('active');
        const parentMenuItem = activeLink.closest('.menu-item');
        if (parentMenuItem) {
            parentMenuItem.classList.add('active');
            if (parentMenuItem.classList.contains('has-submenu')) {
                parentMenuItem.classList.add('open');
            }
        }
    }
};

// 2. Inicialización de eventos
function initAdminPanel() {
    console.log('🚀 Inicializando Panel Administrador...');

    const sidebar = document.getElementById('sidebar');
    const menuBtn = document.getElementById('menuBtn');
    const mainContent = document.getElementById('mainContent');

    // Toggle del Sidebar (Escritorio y Móvil)
    if (menuBtn && sidebar) {
        menuBtn.onclick = function(e) {
            e.preventDefault();
            sidebar.classList.toggle('collapsed');
            if (mainContent) mainContent.classList.toggle('collapsed');
            if (window.innerWidth <= 768) {
                sidebar.classList.toggle('open');
            }
        };
    }

    // Toggle de Submenús (Usuarios, Productos, etc.)
    document.querySelectorAll('.menu-link[data-submenu]').forEach(link => {
        link.onclick = function(e) {
            e.preventDefault();
            const menuItem = this.closest('.menu-item');
            
            // Cerrar otros submenús
            document.querySelectorAll('.menu-item.has-submenu').forEach(item => {
                if (item !== menuItem) item.classList.remove('open');
            });

            // Abrir/Cerrar actual
            if (menuItem) menuItem.classList.toggle('open');
        };
    });

    // Navegación entre páginas (SPA cuando no hay enlace href real o para links con #)
    document.querySelectorAll('[data-page]').forEach(link => {
        link.addEventListener('click', function (e) {
            const href = this.getAttribute('href');
            if (!href || href === '#' || href.startsWith('#')) {
                e.preventDefault();
                const page = this.getAttribute('data-page');
                window.navigateToPage(page);
            }
        });
    });

    // Detectar página inicial desde data-active-page o URL pathname
    let initialPage = '';
    if (mainContent && mainContent.dataset && mainContent.dataset.activePage) {
        initialPage = mainContent.dataset.activePage;
    }
    if (!initialPage) {
        const path = window.location.pathname;
        const pathToPage = {
            '/admin/dashboard': 'dashboard',
            '/admin/powerbi': 'powerbi',
            '/admin/usuarios': 'usuarios',
            '/admin/resenas': 'actividad',
            '/admin/productos': 'catalogo',
            '/admin/ofertas': 'ofertas',
            '/admin/moderacion': 'moderacion',
            '/admin/pedidos': 'ordenes',
            '/admin/verificacion-tiendas': 'verificacion-tiendas',
            '/admin/transportistas': 'transportistas',
            '/admin/sesiones': 'sesiones',
            '/admin/notificaciones': 'notificaciones',
            '/admin/mensajes': 'mensajes',
            '/admin/configuracion': 'configuracion'
        };
        initialPage = pathToPage[path] || 'dashboard';
    }

    window.navigateToPage(initialPage);
    console.log('✅ Panel Administrador: JavaScript listo. Página activa:', initialPage);
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initAdminPanel);
} else {
    initAdminPanel();
}
