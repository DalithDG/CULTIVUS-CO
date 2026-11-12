/* Inspirado en React Bits (github.com/DavidHDev/react-bits), licencia MIT + Commons Clause. Reescrito en CSS/JS vanilla para CULTIVUS-CO */
(function () {
    'use strict';

    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    function init(container) {
        container.classList.add('al-enhanced');
        var anim = container.getAttribute('data-al-animation') || 'pop';
        container.classList.add('al-anim-' + anim);

        var items = Array.prototype.slice.call(container.children);
        var stagger = parseFloat(container.getAttribute('data-al-stagger'));
        if (isNaN(stagger)) stagger = 0.05;
        var maxDelay = 10;

        items.forEach(function (item, i) {
            item.style.transitionDelay = (Math.min(i, maxDelay) * stagger) + 's';
        });

        var hasScroll = container.scrollHeight > container.clientHeight + 1;
        var root = hasScroll ? container : null;

        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('al-in');
                    io.unobserve(entry.target);
                }
            });
        }, { root: root, threshold: 0.1 });

        items.forEach(function (item) { io.observe(item); });

        if (hasScroll) {
            var rafPending = false;
            function updateMask() {
                var top = container.scrollTop > 2;
                var bottom = container.scrollTop + container.clientHeight < container.scrollHeight - 2;
                container.classList.toggle('al-fade-top', top);
                container.classList.toggle('al-fade-bottom', bottom);
            }
            container.addEventListener('scroll', function () {
                if (rafPending) return;
                rafPending = true;
                requestAnimationFrame(function () { rafPending = false; updateMask(); });
            });
            updateMask();
        }
    }

    document.querySelectorAll('[data-anim="list"]').forEach(init);
})();