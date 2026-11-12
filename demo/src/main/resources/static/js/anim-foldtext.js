/* Inspirado en Fold Text de React Bits (MIT + Commons Clause) */
(function () {
    'use strict';

    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    function wrapChars(node, root) {
        if (node.nodeType === Node.TEXT_NODE) {
            var text = node.nodeValue;
            var frag = document.createDocumentFragment();
            for (var i = 0; i < text.length; i++) {
                var ch = text.charAt(i);
                if (ch === ' ' || ch === '\n' || ch === '\t' || ch === '\r') {
                    frag.appendChild(document.createTextNode(ch));
                } else {
                    var span = document.createElement('span');
                    span.className = 'fold-text-char';
                    span.setAttribute('aria-hidden', 'true');
                    span.textContent = ch;
                    frag.appendChild(span);
                }
            }
            node.parentNode.replaceChild(frag, node);
        } else if (node.nodeType === Node.ELEMENT_NODE && node !== root) {
            Array.prototype.slice.call(node.childNodes).forEach(function (child) {
                wrapChars(child, root);
            });
        }
    }

    function reveal(title) {
        if (title.__foldRevealed) return;
        title.__foldRevealed = true;
        var chars = title.querySelectorAll('.fold-text-char');
        chars.forEach(function (ch, idx) {
            ch.style.transitionDelay = (idx * 0.04) + 's';
            void ch.offsetWidth;
            ch.classList.add('is-folded-in');
        });
    }

    document.querySelectorAll('[data-fold-text]').forEach(function (title) {
        var original = (title.textContent || '').trim().replace(/\s+/g, ' ');
        if (original) title.setAttribute('aria-label', original);

        Array.prototype.slice.call(title.childNodes).forEach(function (child) {
            wrapChars(child, title);
        });

        if (!('IntersectionObserver' in window)) {
            reveal(title);
            return;
        }
        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    reveal(title);
                    observer.unobserve(title);
                }
            });
        }, { threshold: 0.2 });
        observer.observe(title);
    });
})();