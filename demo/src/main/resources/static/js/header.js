// Funcionalidad común de cabecera: menú móvil, dropdowns táctiles y sombra al hacer scroll.
// Se carga una sola vez por página a través del fragmento de header.
(function () {
    function initMobileMenu() {
        const mobileMenuToggle = document.getElementById('mobileMenuToggle');
        const mobileMenu = document.getElementById('mobileMenu');
        const mobileMenuClose = document.getElementById('mobileMenuClose');

        if (!mobileMenuToggle || !mobileMenu) return;

        function openMobileMenu() {
            mobileMenu.classList.add('active');
            mobileMenu.setAttribute('aria-hidden', 'false');
            document.body.classList.add('menu-open');
            document.body.style.overflow = 'hidden';
        }

        function closeMobileMenu() {
            mobileMenu.classList.remove('active');
            mobileMenu.setAttribute('aria-hidden', 'true');
            document.body.classList.remove('menu-open');
            document.body.style.overflow = '';
        }

        mobileMenuToggle.addEventListener('click', openMobileMenu);

        if (mobileMenuClose) {
            mobileMenuClose.addEventListener('click', closeMobileMenu);
        }

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && mobileMenu.classList.contains('active')) {
                closeMobileMenu();
            }
        });
    }

    function initTouchDropdowns() {
        const dropdowns = document.querySelectorAll('.nav-dropdown');
        dropdowns.forEach(function (dd) {
            const toggle = dd.querySelector('.nav-dropdown__toggle');
            if (!toggle) return;

            toggle.addEventListener('click', function (e) {
                const isTouch = window.matchMedia('(hover: none) and (pointer: coarse)').matches || window.innerWidth <= 1024;
                if (isTouch) {
                    if (!dd.classList.contains('is-open')) {
                        e.preventDefault();
                        dropdowns.forEach(function (other) { if (other !== dd) other.classList.remove('is-open'); });
                        dd.classList.add('is-open');
                    }
                }
            });
        });

        document.addEventListener('click', function (e) {
            if (!e.target.closest('.nav-dropdown')) {
                document.querySelectorAll('.nav-dropdown.is-open').forEach(function (dd) {
                    dd.classList.remove('is-open');
                });
            }
        });
    }

    function initStickyHeader() {
        const header = document.getElementById('mainHeader');
        if (!header) return;

        window.addEventListener('scroll', function () {
            header.classList.toggle('scrolled', window.pageYOffset > 50);
        }, { passive: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            initMobileMenu();
            initTouchDropdowns();
            initStickyHeader();
        });
    } else {
        initMobileMenu();
        initTouchDropdowns();
        initStickyHeader();
    }
})();
