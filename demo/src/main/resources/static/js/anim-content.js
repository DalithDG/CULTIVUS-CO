/* Inspirado en AnimatedContent de React Bits (MIT + Commons Clause) */
(function () {
    'use strict';

    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (!window.gsap || !window.ScrollTrigger) return;

    var gsap = window.gsap;
    var ScrollTrigger = window.ScrollTrigger;
    gsap.registerPlugin(ScrollTrigger);

    function num(v, fallback) {
        var n = parseFloat(v);
        return isNaN(n) ? fallback : n;
    }

    function bool(v, fallback) {
        if (v === undefined || v === null) return fallback;
        return String(v).toLowerCase() === 'true';
    }

    function initialFrom(direction, distance) {
        var d = distance || 40;
        if (direction === 'down') return { y: -d };
        if (direction === 'left') return { x: d };
        if (direction === 'right') return { x: -d };
        return { y: d };
    }

    document.querySelectorAll('[data-anim-content]').forEach(function (el) {
        var direction = el.getAttribute('data-anim-direction') || 'up';
        var distance = num(el.getAttribute('data-anim-distance'), 40);
        var duration = num(el.getAttribute('data-anim-duration'), 0.7);
        var delay = num(el.getAttribute('data-anim-delay'), 0);
        var blur = bool(el.getAttribute('data-anim-blur'), false);
        var staggerAttr = el.getAttribute('data-anim-stagger');
        var stagger = staggerAttr !== null ? num(staggerAttr, 0) : 0;

        var children = Array.prototype.slice.call(el.children);
        var targets = (stagger > 0 && children.length) ? children : [el];
        var useStagger = stagger > 0 && children.length > 0;

        var init = initialFrom(direction, distance);
        init.opacity = 0;
        if (blur) init.filter = 'blur(6px)';

        var toVars = {
            opacity: 1,
            x: 0,
            y: 0,
            duration: duration,
            delay: delay,
            ease: 'power3.out',
            clearProps: 'transform,opacity,filter'
        };
        if (blur) toVars.filter = 'blur(0px)';
        if (useStagger) toVars.stagger = stagger;

        gsap.set(targets, init);
        gsap.to(targets, Object.assign({}, toVars, {
            scrollTrigger: {
                trigger: el,
                start: 'top 88%',
                once: true
            }
        }));
    });

    window.addEventListener('load', function () {
        ScrollTrigger.refresh();
    });
})();