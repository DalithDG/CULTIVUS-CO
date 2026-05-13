// 1. Función de navegación disponible globalmente de inmediato
window.navigateToPage = function(pageName) {
    console.log('Navigating to:', pageName);
    
    // Ocultar todas las páginas
    const pages = document.querySelectorAll('.page');
    pages.forEach(p => p.style.display = 'none');

    // Títulos de las páginas
    const titles = {
        'dashboard': 'Dashboard',
        'usuarios': 'Gestionar Usuarios',
        'verificacion-tiendas': 'Verificación de Tiendas',
        'sesiones': 'Sesiones Activas',
        'notificaciones': 'Gestión de Notificaciones',
        'mensajes': 'Centro de Mensajes',
        'catalogo': 'Catálogo de Productos',
        'configuracion': 'Configuración del Sistema'
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
    }
};

// 2. Inicialización de eventos al cargar el DOM
document.addEventListener('DOMContentLoaded', function () {
    console.log('🚀 Inicializando Panel Administrador...');

    const sidebar = document.getElementById('sidebar');
    const menuBtn = document.getElementById('menuBtn');
    const mainContent = document.getElementById('mainContent');

    // Toggle del Sidebar (Escritorio y Móvil)
    if (menuBtn && sidebar) {
        menuBtn.onclick = function(e) {
            e.preventDefault();
            console.log('Toggle Sidebar click');
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
            const submenuId = this.getAttribute('data-submenu') + '-submenu';
            const menuItem = this.closest('.menu-item');
            console.log('Toggle Submenu:', submenuId);
            
            // Cerrar otros submenús
            document.querySelectorAll('.menu-item.has-submenu').forEach(item => {
                if (item !== menuItem) item.classList.remove('open');
            });

            // Abrir/Cerrar actual
            if (menuItem) menuItem.classList.toggle('open');
        };
    });

    // Navegación entre páginas (SPA)
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

    console.log('✅ Panel Administrador: JavaScript listo.');
});
