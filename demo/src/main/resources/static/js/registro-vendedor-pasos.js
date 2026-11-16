/* ==========================================================================
   CULTIVUS - Registro de vendedor: asistente progresivo (Entrega C)
   Solo actúa si existe #registroVendedorForm. Sin JS, el formulario queda
   intacto: todas las secciones visibles y el envío funciona como siempre.
   ========================================================================== */
(function () {
    'use strict';

    function init() {
        var form = document.getElementById('registroVendedorForm');
        if (!form) return;

        var sections = Array.prototype.slice.call(form.querySelectorAll('.vp-section'));
        if (!sections.length) return;

        var container = form.closest('.vp-reg-container');
        var reduceMotion = window.matchMedia
            ? window.matchMedia('(prefers-reduced-motion: reduce)')
            : { matches: false };

        var names = sections.map(function (section) {
            var title = section.querySelector('.vp-section__title');
            if (!title) return 'Paso';
            var clone = title.cloneNode(true);
            var num = clone.querySelector('.vp-section__num');
            if (num && num.parentNode) num.parentNode.removeChild(num);
            return clone.textContent.replace(/\s+/g, ' ').trim();
        });

        function sectionKind(section) {
            if (section.querySelector('#contrasena') || section.querySelector('#email')) return 'cuenta';
            if (section.querySelector('#razonSocial')) return 'negocio';
            if (section.querySelector('#descripcionNegocio')) return 'productos';
            return 'otra';
        }
        var kinds = sections.map(sectionKind);

        // La numeración visible de las tarjetas se ajusta a los pasos presentes
        // (por ejemplo, con sesión iniciada solo hay 2 pasos).
        sections.forEach(function (section, i) {
            var num = section.querySelector('.vp-section__num');
            if (num) num.textContent = String(i + 1);
        });

        var current = 0;
        var stepButtons = [];

        // ── Indicador de pasos ──────────────────────────────────────────────
        var nav = document.createElement('nav');
        nav.className = 'vp-steps';
        nav.setAttribute('aria-label', 'Progreso de registro');

        var list = document.createElement('ol');
        list.className = 'checkout-steps';

        sections.forEach(function (section, i) {
            var li = document.createElement('li');
            li.className = 'checkout-steps__item';

            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'vp-step-btn';
            btn.disabled = true;
            btn.setAttribute('aria-label', 'Paso ' + (i + 1) + ': ' + names[i]);

            var num = document.createElement('span');
            num.className = 'checkout-steps__num';
            num.textContent = String(i + 1);

            var label = document.createElement('span');
            label.className = 'vp-step-name';
            label.textContent = names[i];

            btn.appendChild(num);
            btn.appendChild(label);
            btn.addEventListener('click', function () { onStepClick(i); });

            li.appendChild(btn);
            list.appendChild(li);
            stepButtons.push({ li: li, btn: btn });
        });

        nav.appendChild(list);

        var live = document.createElement('p');
        live.className = 'vp-step-live';
        live.setAttribute('aria-live', 'polite');
        live.setAttribute('aria-atomic', 'true');
        nav.appendChild(live);

        if (container && container.parentNode) {
            container.parentNode.insertBefore(nav, container);
        }

        // ── Controles de navegación por paso (Regresar / Continuar) ─────────
        sections.forEach(function (section, i) {
            if (i > 0) {
                var backTop = document.createElement('button');
                backTop.type = 'button';
                backTop.className = 'vp-step-back-top';
                backTop.innerHTML = '<i class="fas fa-arrow-left" aria-hidden="true"></i><span>Regresar a ' + names[i - 1] + '</span>';
                backTop.addEventListener('click', function () { onEdit(i - 1); });
                section.insertBefore(backTop, section.firstChild);
            }

            if (i < sections.length - 1) {
                var actions = document.createElement('div');
                actions.className = 'vp-step-actions';

                if (i > 0) {
                    var prev = document.createElement('button');
                    prev.type = 'button';
                    prev.className = 'ui-btn ui-btn--secondary vp-step-prev';
                    prev.innerHTML = '<i class="fas fa-arrow-left"></i><span>Anterior</span>';
                    prev.addEventListener('click', function () { onEdit(i - 1); });
                    actions.appendChild(prev);
                }

                var cont = document.createElement('button');
                cont.type = 'button';
                cont.className = 'ui-btn ui-btn--primary vp-step-continue';
                cont.innerHTML = '<span>Continuar</span><i class="fas fa-arrow-right"></i>';
                cont.addEventListener('click', function () { onContinue(i); });

                actions.appendChild(cont);
                section.appendChild(actions);
            } else if (i > 0) {
                var submitRow = section.querySelector('.vp-submit-row');
                if (submitRow) {
                    var backBtn = document.createElement('button');
                    backBtn.type = 'button';
                    backBtn.className = 'ui-btn ui-btn--secondary ui-btn--lg vp-step-prev';
                    backBtn.innerHTML = '<i class="fas fa-arrow-left"></i><span>Paso anterior</span>';
                    backBtn.addEventListener('click', function () { onEdit(i - 1); });
                    submitRow.insertBefore(backBtn, submitRow.firstChild);
                }
            }
        });

        // ── Utilidades de validación ────────────────────────────────────────
        function getFields(section) {
            return Array.prototype.filter.call(
                section.querySelectorAll('input, select, textarea'),
                function (el) {
                    return el.type !== 'hidden' && !el.disabled && el.willValidate;
                }
            );
        }

        function sectionValid(section) {
            return getFields(section).every(function (el) {
                return el.checkValidity();
            });
        }

        function reportFirstInvalid(section) {
            var fields = getFields(section);
            for (var i = 0; i < fields.length; i++) {
                if (!fields[i].checkValidity()) {
                    fields[i].reportValidity();
                    return false;
                }
            }
            return true;
        }

        function validateSection(section) {
            // Si el foco está en un campo, al salir se ejecuta la validación de
            // teléfono que ya vive en registro-vendedor.js.
            var active = document.activeElement;
            if (active && section.contains(active) && /^(INPUT|SELECT|TEXTAREA)$/.test(active.tagName)) {
                active.blur();
            }
            var fields = getFields(section);
            for (var i = 0; i < fields.length; i++) {
                if (!fields[i].checkValidity()) {
                    fields[i].reportValidity();
                    return false;
                }
            }
            return true;
        }

        function firstInvalidIndex() {
            for (var i = 0; i < sections.length; i++) {
                if (!sectionValid(sections[i])) return i;
            }
            return -1;
        }

        // ── Estados ─────────────────────────────────────────────────────────
        function setSectionState(index, state) {
            var section = sections[index];
            section.setAttribute('data-state', state);
            section.hidden = state !== 'active';
        }

        function updateSteps() {
            sections.forEach(function (section, i) {
                var done = section.getAttribute('data-state') === 'done';
                var item = stepButtons[i];
                item.li.classList.toggle('is-current', i === current);
                item.li.classList.toggle('is-done', done);
                var numEl = item.btn.querySelector('.checkout-steps__num');
                if (numEl) {
                    numEl.innerHTML = done ? '<i class="fas fa-check"></i>' : String(i + 1);
                }
                if (i === current) item.btn.setAttribute('aria-current', 'step');
                else item.btn.removeAttribute('aria-current');
                item.btn.disabled = !done || i === current;
                item.btn.setAttribute(
                    'aria-label',
                    'Paso ' + (i + 1) + ': ' + names[i] + (done ? ' (completado). Volver a este paso' : '')
                );
            });
        }

        function announce() {
            live.textContent = 'Paso ' + (current + 1) + ' de ' + sections.length + ': ' + names[current];
        }

        function applyStates(activeIndex) {
            sections.forEach(function (section, i) {
                if (i < activeIndex) setSectionState(i, 'done');
                else if (i === activeIndex) setSectionState(i, 'active');
                else setSectionState(i, 'pending');
            });
            current = activeIndex;
            updateSteps();
        }

        // ── Animación de entrada ────────────────────────────────────────────
        function animateIn(section) {
            if (reduceMotion.matches) return;

            section.style.opacity = '0';
            section.style.transform = 'translateY(10px)';
            void section.offsetHeight;

            section.style.transition = 'opacity 240ms cubic-bezier(0.22, 1, 0.36, 1), transform 240ms cubic-bezier(0.22, 1, 0.36, 1)';
            section.style.opacity = '1';
            section.style.transform = 'translateY(0)';

            var finished = false;
            var timer;
            function clean() {
                if (finished) return;
                finished = true;
                clearTimeout(timer);
                section.style.transition = '';
                section.style.opacity = '';
                section.style.transform = '';
                section.removeEventListener('transitionend', onEnd);
            }
            function onEnd(ev) {
                if (ev.target === section) clean();
            }
            section.addEventListener('transitionend', onEnd);
            timer = setTimeout(clean, 300);
        }

        function scrollToStep(section) {
            if (!section) return;
            try {
                section.scrollIntoView({
                    behavior: reduceMotion.matches ? 'auto' : 'smooth',
                    block: 'start'
                });
            } catch (e) {
                section.scrollIntoView();
            }
        }

        function focusFirst(section) {
            var catBtn = section.querySelector('.cat-multi__btn');
            var field = catBtn || section.querySelector(
                'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled])'
            );
            if (!field) return;
            try {
                field.focus({ preventScroll: true });
            } catch (e) {
                field.focus();
            }
        }

        function showStep(index, opts) {
            opts = opts || {};
            applyStates(index);
            var section = sections[index];
            // El aviso de categorías no debe aparecer solo por activar el paso:
            // se limpia aquí y vuelve a mostrarse si se intenta enviar o si se
            // cierra el desplegable sin elegir.
            var catErr = section.querySelector('#catError');
            if (catErr) catErr.textContent = '';
            if (opts.animate) animateIn(section);
            if (opts.scroll) scrollToStep(section);
            if (opts.focus) focusFirst(section);
            if (opts.announce !== false) announce();
        }

        // ── Navegación ──────────────────────────────────────────────────────
        function onContinue(index) {
            if (index !== current) return;
            if (!validateSection(sections[index])) return;
            if (index + 1 < sections.length) {
                showStep(index + 1, { animate: true, scroll: true, focus: true });
            }
        }

        function onEdit(index) {
            if (index === current || index < 0 || index >= sections.length) return;
            if (index > current && !validateSection(sections[current])) return;

            showStep(index, { animate: true, scroll: true, focus: true });
        }

        function onStepClick(index) {
            if (index >= current) return;
            if (sections[index].getAttribute('data-state') !== 'done') return;
            onEdit(index);
        }

        // ── Enter = Continuar ───────────────────────────────────────────────
        form.addEventListener('keydown', function (e) {
            if (e.key !== 'Enter' || e.shiftKey || e.altKey || e.ctrlKey || e.metaKey) return;
            var el = e.target;
            if (!el || !/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)) return;
            if (el.tagName === 'TEXTAREA' || el.tagName === 'SELECT') return;
            if (el.type === 'checkbox' || el.type === 'radio' || el.type === 'button' || el.type === 'submit') return;

            var section = el.closest('.vp-section');
            if (!section) return;
            var index = sections.indexOf(section);
            if (index < 0 || index !== current) return;

            var cont = section.querySelector('.vp-step-continue');
            if (!cont) return; // última sección: el envío lo maneja el formulario
            e.preventDefault();
            cont.click();
        });

        // ── Envío ───────────────────────────────────────────────────────────
        function hasCategories() {
            return !!form.querySelector('input[name="tipoProductos"]:checked');
        }

        function openForError(index) {
            showStep(index, { animate: false, scroll: true, focus: false });
            reportFirstInvalid(sections[index]);
        }

        document.addEventListener('submit', function (e) {
            if (e.target !== form) return;

            var active = document.activeElement;
            if (active && form.contains(active) && /^(INPUT|SELECT|TEXTAREA)$/.test(active.tagName)) {
                active.blur();
            }

            var invalid = firstInvalidIndex();
            if (invalid >= 0) {
                e.preventDefault();
                e.stopPropagation();
                openForError(invalid);
                return;
            }

            // Los campos nativos están bien, pero falta elegir categorías:
            // se abre la última sección y el JS existente muestra el aviso y
            // cancela el envío.
            if (!hasCategories()) {
                var prod = kinds.indexOf('productos');
                if (prod >= 0) {
                    showStep(prod, { animate: false, scroll: true, focus: false });
                }
                return;
            }
        }, true);

        document.addEventListener('submit', function (e) {
            if (e.target !== form || e.defaultPrevented) return;
            var submitBtn = document.querySelector('button[type="submit"][form="registroVendedorForm"]')
                || form.querySelector('button[type="submit"]');
            if (submitBtn && !submitBtn.disabled) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Registrando...';
            }
        }, false);

        // ── Error del servidor: abrir la sección del campo con problema ─────
        function normalizeText(text) {
            return (text || '')
                .normalize('NFD')
                .replace(/[\u0300-\u036f]/g, '')
                .toLowerCase();
        }

        function indexByKind(kind) {
            for (var i = 0; i < kinds.length; i++) {
                if (kinds[i] === kind) return i;
            }
            return -1;
        }

        function resolveErrorSection() {
            var alert = form.querySelector('.ui-alert--danger');
            if (!alert) return -1;

            var text = normalizeText(alert.textContent);
            var map = [
                { kind: 'cuenta', words: ['contrasena', 'correo', 'email', 'nombre completo'] },
                { kind: 'negocio', words: ['razon', 'social', 'telefono', 'celular', 'direccion', 'departamento', 'municipio', 'ciudad', 'negocio', 'predio'] },
                { kind: 'productos', words: ['categoria', 'categorias', 'productos', 'cosechas', 'descripcion'] }
            ];

            for (var i = 0; i < map.length; i++) {
                var idx = indexByKind(map[i].kind);
                if (idx < 0) continue;
                for (var j = 0; j < map[i].words.length; j++) {
                    if (text.indexOf(map[i].words[j]) !== -1) return idx;
                }
            }

            var invalid = firstInvalidIndex();
            return invalid >= 0 ? invalid : 0;
        }

        var errorIndex = resolveErrorSection();
        var startIndex = errorIndex >= 0 ? errorIndex : 0;

        sections.forEach(function (section, i) {
            if (i < startIndex) setSectionState(i, 'done');
            else if (i === startIndex) setSectionState(i, 'active');
            else setSectionState(i, 'pending');
        });
        current = startIndex;
        updateSteps();

        // A partir de aquí el formulario pasa a modo asistente. Sin esta clase
        // el CSS no oculta nada y el envío nativo sigue funcionando.
        form.setAttribute('novalidate', 'novalidate');
        form.classList.add('vp-steps-enabled');

        announce();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
