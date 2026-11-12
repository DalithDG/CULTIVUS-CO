/* Efecto de flotación suave propio de Cultivus (CSS puro) */
(function () {
    if (!('IntersectionObserver' in window)) return;
    var hero = document.querySelector('.home-hero');
    if (!hero) return;
    var floats = document.querySelectorAll('[data-float]');
    var observer = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
            floats.forEach(function (el) {
                if (entry.isIntersecting) {
                    el.classList.remove('is-paused');
                } else {
                    el.classList.add('is-paused');
                }
            });
        });
    });
    observer.observe(hero);
})();