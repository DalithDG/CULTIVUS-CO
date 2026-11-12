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

        // ── Resúmenes y botones Continuar ───────────────────────────────────
        sections.forEach(function (section, i) {
            var summary = document.createElement('div');
            summary.className = 'vp-step-summary';

            var main = document.createElement('div');
            main.className = 'vp-step-summary__main';

            var title = document.createElement('span');
            title.className = 'vp-step-summary__title';
            var check = document.createElement('i');
            check.className = 'fas fa-circle-check';
            title.appendChild(check);
            var titleText = document.createElement('span');
            titleText.textContent = names[i];
            title.appendChild(titleText);

            var values = document.createElement('span');
            values.className = 'vp-step-summary__values';

            main.appendChild(title);
            main.appendChild(values);

            var edit = document.createElement('button');
            edit.type = 'button';
            edit.className = 'ui-btn ui-btn--secondary ui-btn--sm vp-step-edit';
            edit.textContent = 'Editar';
            edit.setAttribute('aria-label', 'Editar ' + names[i]);
            edit.addEventListener('click', function () { onEdit(i); });

            summary.appendChild(main);
            summary.appendChild(edit);
            section.insertBefore(summary, section.firstChild);

            if (i < sections.length - 1) {
                var actions = document.createElement('div');
                actions.className = 'vp-step-actions';

                var cont = document.createElement('button');
                cont.type = 'button';
                cont.className = 'ui-btn ui-btn--primary vp-step-continue';
                cont.innerHTML = '<span>Continuar</span><i class="fas fa-arrow-right"></i>';
                cont.addEventListener('click', function () { onContinue(i); });

                actions.appendChild(cont);
                section.appendChild(actions);
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
        function updateSummary(section, index) {
            var values = section.querySelector('.vp-step-summary__values');
            if (!values) return;

            var parts = [];
            if (kinds[index] === 'cuenta') {
                var nombre = section.querySelector('[name="nombre"]');
                var email = section.querySelector('[name="email"]');
                parts.push(nombre && nombre.value.trim() ? nombre.value.trim() : 'Sin nombre');
                parts.push(email && email.value.trim() ? email.value.trim() : 'Sin correo');
            } else if (kinds[index] === 'negocio') {
                var razon = section.querySelector('[name="razonSocial"]');
                var ciudad = section.querySelector('[name="ciudad"]');
                parts.push(razon && razon.value.trim() ? razon.value.trim() : 'Sin razón social');
                parts.push(ciudad && ciudad.value ? ciudad.value : 'Sin municipio');
            } else {
                var selected = section.querySelectorAll('input[name="tipoProductos"]:checked');
                if (selected.length) {
                    var cats = Array.prototype.map.call(selected, function (c) { return c.value; });
                    parts.push(cats.join(', '));
                } else {
                    parts.push('Sin categorías');
                }
            }
            values.textContent = parts.join(' · ');
            values.title = values.textContent;
        }

        function setSectionState(index, state) {
            var section = sections[index];
            section.setAttribute('data-state', state);
            section.hidden = state === 'pending';
            if (state === 'done') updateSummary(section, index);
        }

        function updateSteps() {
            sections.forEach(function (section, i) {
                var done = section.getAttribute('data-state') === 'done';
                var item = stepButtons[i];
                item.li.classList.toggle('is-current', i === current);
                item.li.classList.toggle('is-done', done);
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

            var targetHeight = section.scrollHeight;
            section.style.height = '0px';
            section.style.overflow = 'hidden';
            section.style.opacity = '0';
            section.style.transform = 'translateY(12px)';
            void section.offsetHeight;

            section.style.transition = 'height 300ms cubic-bezier(0.4, 0, 0.2, 1), opacity 240ms ease, transform 240ms ease';
            section.style.height = targetHeight + 'px';
            section.style.opacity = '1';
            section.style.transform = 'translateY(0)';

            var finished = false;
            var timer;
            function clean() {
                if (finished) return;
                finished = true;
                clearTimeout(timer);
                section.style.transition = '';
                section.style.height = '';
                section.style.overflow = '';
                section.style.opacity = '';
                section.style.transform = '';
                section.removeEventListener('transitionend', onEnd);
            }
            function onEnd(ev) {
                if (ev.target === section && ev.propertyName === 'height') clean();
            }
            section.addEventListener('transitionend', onEnd);
            timer = setTimeout(clean, 380);
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
            if (index === current || index > current) return;
            if (sections[index].getAttribute('data-state') !== 'done') return;
            if (!validateSection(sections[current])) return;

            // La sección activa se pliega como completada y se reabre la elegida.
            setSectionState(current, 'done');
            setSectionState(index, 'active');
            var catErr = sections[index].querySelector('#catError');
            if (catErr) catErr.textContent = '';
            current = index;
            updateSteps();
            announce();
            animateIn(sections[index]);
            scrollToStep(sections[index]);
            focusFirst(sections[index]);
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
