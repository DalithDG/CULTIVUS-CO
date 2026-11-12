/* Inspirado en React Bits (github.com/DavidHDev/react-bits), licencia MIT + Commons Clause. Reescrito en CSS/JS vanilla para CULTIVUS-CO */
(function () {
    'use strict';

    var reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var starSvg = '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>';

    function init(el) {
        var count = parseInt(el.getAttribute('data-pr-count'), 10) || 5;
        var name = el.getAttribute('data-pr-name') || 'calificacion';
        var value = parseInt(el.getAttribute('data-pr-value'), 10) || 0;
        var readonly = el.getAttribute('data-pr-readonly') === 'true';
        var clear = el.getAttribute('data-pr-clear') === 'true';
        var labelsAttr = el.getAttribute('data-pr-labels');
        var labels = labelsAttr ? labelsAttr.split(',').map(function (s) { return s.trim(); }) : null;

        el.classList.add('pr-wrap');

        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        el.appendChild(input);

        var group = document.createElement('div');
        group.className = 'pr-stars';
        group.setAttribute('role', 'radiogroup');
        el.appendChild(group);

        var label = document.createElement('div');
        label.className = 'pr-label';
        el.appendChild(label);

        var stars = [];
        var current = value;
        var hoverValue = 0;

        function labelText(v) {
            if (labels && labels[v - 1]) return labels[v - 1];
            return String(v);
        }

        function paint() {
            var v = hoverValue || current;
            stars.forEach(function (s, i) {
                var idx = i + 1;
                s.classList.toggle('pr-active', idx <= v);
                s.classList.remove('is-lifted', 'is-hover');
                if (hoverValue) {
                    if (idx <= hoverValue) s.classList.add('is-lifted');
                    if (idx === hoverValue) s.classList.add('is-hover');
                }
            });
            if (hoverValue) {
                var star = stars[hoverValue - 1];
                label.textContent = labelText(hoverValue);
                label.style.left = (star.offsetLeft + star.offsetWidth / 2) + 'px';
                label.classList.add('is-visible');
            } else {
                label.classList.remove('is-visible');
            }
        }

        function updateAria() {
            stars.forEach(function (s, i) {
                var idx = i + 1;
                s.setAttribute('aria-checked', idx === current ? 'true' : 'false');
                s.tabIndex = idx === current || (current === 0 && idx === 1) ? 0 : -1;
            });
        }

        function select(val) {
            if (readonly) return;
            if (clear && val === current) {
                current = 0;
            } else {
                current = val;
            }
            input.value = current;
            updateAria();
            paint();
            if (current > 0 && !reducedMotion) {
                var star = stars[current - 1];
                try {
                    star.animate(
                        [{ transform: 'translateY(-6px) scale(1.15)' }, { transform: 'translateY(-6px) scale(1.3)' }, { transform: 'translateY(-6px) scale(1.15)' }],
                        { duration: 300, easing: 'ease-out' }
                    );
                } catch (e) { /* noop */ }
            }
        }

        for (var i = 1; i <= count; i++) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'pr-star';
            btn.setAttribute('role', 'radio');
            btn.setAttribute('aria-checked', i === current ? 'true' : 'false');
            btn.setAttribute('aria-label', labels && labels[i - 1] ? labels[i - 1] : (i + ' estrellas'));
            btn.tabIndex = (i === current || (current === 0 && i === 1)) ? 0 : -1;
            btn.innerHTML = starSvg;
            btn.dataset.value = i;
            if (readonly) btn.disabled = true;
            group.appendChild(btn);
            stars.push(btn);
        }

        if (!readonly) {
            stars.forEach(function (s, i) {
                s.addEventListener('mouseenter', function () {
                    hoverValue = i + 1;
                    paint();
                });
                s.addEventListener('click', function () {
                    select(i + 1);
                });
            });

            group.addEventListener('mouseleave', function () {
                hoverValue = 0;
                paint();
            });

            // Drag support
            var dragging = false;
            group.addEventListener('pointerdown', function (e) {
                dragging = true;
                try { group.setPointerCapture(e.pointerId); } catch (err) { }
                updateHoverFromPoint(e);
            });
            group.addEventListener('pointermove', function (e) {
                if (!dragging) return;
                updateHoverFromPoint(e);
            });
            group.addEventListener('pointerup', function (e) {
                if (!dragging) return;
                dragging = false;
                if (hoverValue) select(hoverValue);
                hoverValue = 0;
                paint();
            });

            function updateHoverFromPoint(e) {
                var elAt = document.elementFromPoint(e.clientX, e.clientY);
                var star = elAt && elAt.closest ? elAt.closest('.pr-star') : null;
                if (star && group.contains(star)) {
                    hoverValue = parseInt(star.dataset.value, 10);
                } else {
                    hoverValue = 0;
                }
                paint();
            }

            // Keyboard
            group.addEventListener('keydown', function (e) {
                var idx = stars.indexOf(document.activeElement);
                if (idx < 0) return;
                var key = e.key;
                var newVal = current;
                if (key === 'ArrowRight' || key === 'ArrowUp') {
                    newVal = Math.min(count, (current || 0) + 1);
                    if (newVal < 1) newVal = 1;
                    e.preventDefault();
                } else if (key === 'ArrowLeft' || key === 'ArrowDown') {
                    newVal = Math.max(1, (current || 1) - 1);
                    e.preventDefault();
                } else if (key === 'Home') {
                    newVal = 1;
                    e.preventDefault();
                } else if (key === 'End') {
                    newVal = count;
                    e.preventDefault();
                } else if (key === ' ' || key === 'Enter') {
                    e.preventDefault();
                    select(current || idx + 1);
                    return;
                } else if (key === 'Delete' || key === 'Backspace') {
                    if (clear) {
                        current = 0;
                        input.value = 0;
                        updateAria();
                        paint();
                    }
                    e.preventDefault();
                    return;
                } else {
                    return;
                }
                select(newVal);
                var targetIdx = Math.max(0, Math.min(count - 1, newVal - 1));
                stars[targetIdx].focus();
            });
        }

        paint();
        updateAria();
    }

    document.querySelectorAll('[data-anim="peek-rating"]').forEach(init);
})();