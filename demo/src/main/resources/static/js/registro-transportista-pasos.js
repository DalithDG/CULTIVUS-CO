/* ==========================================================================
   CULTIVUS - Registro de transportista: asistente progresivo
   Solo actúa si existe #formRegistroTransportista. Sin JS, el formulario
   queda intacto: todas las secciones visibles y el envío funciona como hoy.
   ========================================================================== */
(function () {
    'use strict';

    function init() {
        var form = document.getElementById('formRegistroTransportista');
        if (!form) return;

        var sections = Array.prototype.slice.call(form.querySelectorAll('.tp-section'));
        if (!sections.length) return;

        var container = form.closest('.tp-reg-container');
        var reduceMotion = window.matchMedia
            ? window.matchMedia('(prefers-reduced-motion: reduce)')
            : { matches: false };

        var names = sections.map(function (section) {
            var title = section.querySelector('.tp-section__title');
            if (!title) return 'Paso';
            var clone = title.cloneNode(true);
            var num = clone.querySelector('.tp-section__num');
            if (num && num.parentNode) num.parentNode.removeChild(num);
            return clone.textContent.replace(/\s+/g, ' ').trim();
        });

        function sectionKind(section) {
            if (section.querySelector('#todasLasZonas') || section.querySelector('input[name="zonasCoberturaIds"]')) return 'zonas';
            if (section.querySelector('#placa') || section.querySelector('#capacidadCargaKg')) return 'vehiculo';
            if (section.querySelector('#email') || section.querySelector('#contrasena')) return 'cuenta';
            if (section.querySelector('input[name="tipo"]')) return 'cuenta';
            return 'otra';
        }
        var kinds = sections.map(sectionKind);

        // La numeración visible de las tarjetas se ajusta a los pasos presentes
        // (por ejemplo, con sesión iniciada el paso 1 solo tiene la modalidad).
        sections.forEach(function (section, i) {
            var num = section.querySelector('.tp-section__num');
            if (num) num.textContent = String(i + 1);
        });

        var current = 0;
        var stepButtons = [];

        // ── Indicador de pasos (usa el nav estático del template) ───────────
        var nav = document.querySelector('.tp-steps');
        if (!nav && container && container.parentNode) {
            nav = document.createElement('nav');
            nav.className = 'tp-steps';
            nav.setAttribute('aria-label', 'Progreso de registro');
            var newList = document.createElement('ol');
            newList.className = 'checkout-steps';
            sections.forEach(function (section, i) {
                var newLi = document.createElement('li');
                newLi.className = 'checkout-steps__item';
                var newNum = document.createElement('span');
                newNum.className = 'checkout-steps__num';
                newNum.textContent = String(i + 1);
                var newName = document.createElement('span');
                newName.className = 'tp-step-name';
                newName.textContent = names[i];
                newLi.appendChild(newNum);
                newLi.appendChild(newName);
                newList.appendChild(newLi);
            });
            nav.appendChild(newList);
            container.parentNode.insertBefore(nav, container);
        }

        var items = nav
            ? Array.prototype.slice.call(nav.querySelectorAll('.checkout-steps__item'))
            : [];

        items.forEach(function (li, i) {
            if (i >= sections.length) {
                li.parentNode.removeChild(li);
                return;
            }

            var num = li.querySelector('.checkout-steps__num');
            var label = li.querySelector('.tp-step-name');

            if (!num) {
                num = document.createElement('span');
                num.className = 'checkout-steps__num';
                li.appendChild(num);
            }
            num.textContent = String(i + 1);

            if (!label) {
                label = document.createElement('span');
                label.className = 'tp-step-name';
                li.appendChild(label);
            }
            label.textContent = names[i];

            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'tp-step-btn';
            btn.disabled = true;
            btn.setAttribute('aria-label', 'Paso ' + (i + 1) + ': ' + names[i]);

            li.insertBefore(btn, num);
            btn.appendChild(num);
            btn.appendChild(label);

            btn.addEventListener('click', function () { onStepClick(i); });

            stepButtons.push({ li: li, btn: btn });
        });

        if (nav && !nav.querySelector('.tp-step-live')) {
            var live = document.createElement('p');
            live.className = 'tp-step-live';
            live.setAttribute('aria-live', 'polite');
            live.setAttribute('aria-atomic', 'true');
            nav.appendChild(live);
        }
        var live = nav ? nav.querySelector('.tp-step-live') : null;
        if (nav) nav.classList.add('tp-steps--enhanced');

        // ── Resúmenes y botones Continuar ───────────────────────────────────
        sections.forEach(function (section, i) {
            var summary = document.createElement('div');
            summary.className = 'tp-step-summary';

            var main = document.createElement('div');
            main.className = 'tp-step-summary__main';

            var title = document.createElement('span');
            title.className = 'tp-step-summary__title';
            var check = document.createElement('i');
            check.className = 'fas fa-circle-check';
            title.appendChild(check);
            var titleText = document.createElement('span');
            titleText.textContent = names[i];
            title.appendChild(titleText);

            var values = document.createElement('span');
            values.className = 'tp-step-summary__values';

            main.appendChild(title);
            main.appendChild(values);

            var edit = document.createElement('button');
            edit.type = 'button';
            edit.className = 'ui-btn ui-btn--secondary ui-btn--sm tp-step-edit';
            edit.textContent = 'Editar';
            edit.setAttribute('aria-label', 'Editar ' + names[i]);
            edit.addEventListener('click', function () { onEdit(i); });

            summary.appendChild(main);
            summary.appendChild(edit);
            section.insertBefore(summary, section.firstChild);

            if (i < sections.length - 1) {
                var actions = document.createElement('div');
                actions.className = 'tp-step-actions';

                var cont = document.createElement('button');
                cont.type = 'button';
                cont.className = 'ui-btn ui-btn--primary tp-step-continue';
                cont.innerHTML = '<span>Continuar</span><i class="fas fa-arrow-right"></i>';
                cont.addEventListener('click', function () { onContinue(i); });

                actions.appendChild(cont);
                section.appendChild(actions);
            }
        });

        // ── Utilidades de validación ────────────────────────────────────────
        function isVisible(el) {
            return !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
        }

        function getFields(section) {
            return Array.prototype.filter.call(
                section.querySelectorAll('input, select, textarea'),
                function (el) {
                    if (el.type === 'hidden' || el.disabled || !el.willValidate) return false;
                    // Los radios de modalidad/vehículo están ocultos por diseño;
                    // solo validamos los campos realmente visibles del paso.
                    return isVisible(el);
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
            // Si el foco está en un campo, al salir se ejecutan validaciones
            // que vivan en el JS propio de esta vista.
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

        // ── Resúmenes ───────────────────────────────────────────────────────
        function textOf(el) {
            return el && el.value ? String(el.value).trim() : '';
        }

        function modalidadLabel() {
            var checked = form.querySelector('input[name="tipo"]:checked');
            if (!checked) return 'Sin modalidad';
            return checked.value === 'EMPRESA' ? 'Empresa Logística' : 'Independiente';
        }

        function updateSummary(section, index) {
            var values = section.querySelector('.tp-step-summary__values');
            if (!values) return;

            var parts = [];
            var kind = kinds[index];

            if (kind === 'cuenta') {
                parts.push(modalidadLabel());
                var nombre = section.querySelector('[name="nombre"]');
                var email = section.querySelector('[name="email"]');
                if (textOf(nombre)) parts.push(textOf(nombre));
                if (textOf(email)) parts.push(textOf(email));
            } else if (kind === 'vehiculo') {
                var checkedVehicle = section.querySelector('input[name="tipoVehiculo"]:checked');
                parts.push(checkedVehicle ? checkedVehicle.value : 'Sin vehículo');

                var esEmpresa = form.querySelector('input[name="tipo"]:checked')
                    && form.querySelector('input[name="tipo"]:checked').value === 'EMPRESA';
                var razon = section.querySelector('[name="razonSocial"]');
                var placa = section.querySelector('[name="placa"]');
                if (esEmpresa && textOf(razon)) {
                    parts.push(textOf(razon));
                } else if (textOf(placa)) {
                    parts.push(textOf(placa));
                } else if (textOf(razon)) {
                    parts.push(textOf(razon));
                }

                var capacidad = section.querySelector('[name="capacidadCargaKg"]');
                if (textOf(capacidad)) parts.push(textOf(capacidad) + ' kg');
            } else {
                var total = section.querySelectorAll('input[name="zonasCoberturaIds"]:checked').length;
                var todas = section.querySelector('#todasLasZonas');
                if (todas && todas.checked) parts.push('Todas las zonas');
                else if (total) parts.push(total + (total === 1 ? ' zona' : ' zonas'));
                else parts.push('Sin zonas');
            }

            values.textContent = parts.join(' · ');
            values.title = values.textContent;
        }

        // ── Estados ─────────────────────────────────────────────────────────
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
                if (!item) return;
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
            if (!live) return;
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
            var fields = section.querySelectorAll(
                'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled])'
            );
            for (var i = 0; i < fields.length; i++) {
                if (isVisible(fields[i])) {
                    try {
                        fields[i].focus({ preventScroll: true });
                    } catch (e) {
                        fields[i].focus();
                    }
                    return;
                }
            }
            // Sin campos visibles (por ejemplo, solo radios ocultos):
            // el foco va a la tarjeta para no perder el contexto.
            section.setAttribute('tabindex', '-1');
            try {
                section.focus({ preventScroll: true });
            } catch (e2) {
                section.focus();
            }
        }

        function showStep(index, opts) {
            opts = opts || {};
            applyStates(index);
            var section = sections[index];
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

        // ── Cambio de modalidad: invalida el paso 2 si ya estaba completado ──
        Array.prototype.forEach.call(form.querySelectorAll('input[name="tipo"]'), function (radio) {
            radio.addEventListener('change', function () {
                if (!sections[1]) return;
                if (sections[1].getAttribute('data-state') !== 'done') return;
                // Al cambiar los campos exigidos, el paso del vehículo vuelve a
                // quedar activo (con sus required recalculados por toggleTipoTransportista).
                showStep(1, { animate: true, scroll: true, focus: true });
            });
        });

        // ── Enter = Continuar ───────────────────────────────────────────────
        form.addEventListener('keydown', function (e) {
            if (e.key !== 'Enter' || e.shiftKey || e.altKey || e.ctrlKey || e.metaKey) return;
            var el = e.target;
            if (!el || !/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)) return;
            if (el.tagName === 'TEXTAREA' || el.tagName === 'SELECT') return;
            if (el.type === 'checkbox' || el.type === 'radio' || el.type === 'button' || el.type === 'submit') return;

            var section = el.closest('.tp-section');
            if (!section) return;
            var index = sections.indexOf(section);
            if (index < 0 || index !== current) return;

            var cont = section.querySelector('.tp-step-continue');
            if (!cont) return; // última sección: el envío lo maneja el formulario
            e.preventDefault();
            cont.click();
        });

        // ── Zonas (sin duplicar la validación existente) ────────────────────
        function hasZones() {
            var todas = form.querySelector('#todasLasZonas');
            if (todas && todas.checked) return true;
            return !!form.querySelector('input[name="zonasCoberturaIds"]:checked');
        }

        function zoneIndex() {
            for (var i = 0; i < kinds.length; i++) {
                if (kinds[i] === 'zonas') return i;
            }
            return sections.length - 1;
        }

        function openForError(index) {
            showStep(index, { animate: false, scroll: true, focus: false });
            reportFirstInvalid(sections[index]);
        }

        // ── Envío ───────────────────────────────────────────────────────────
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

            // Los campos nativos están bien pero faltan zonas. Se muestra el
            // último paso para que el listener existente muestre el banner y
            // cancele el envío (no duplicamos esa validación).
            if (!hasZones()) {
                var zi = zoneIndex();
                if (zi !== current) {
                    showStep(zi, { animate: false, scroll: false, focus: false });
                }
                return;
            }
        }, true);

        document.addEventListener('submit', function (e) {
            if (e.target !== form || e.defaultPrevented) return;
            var submitBtn = form.querySelector('button[type="submit"]');
            if (submitBtn && !submitBtn.disabled) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Registrando...';
            }
        }, false);

        // ── Error del servidor: abrir el paso del campo con problema ────────
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
            var alerts = Array.prototype.slice.call(form.querySelectorAll('.ui-alert--danger'));
            var alert = null;
            for (var a = 0; a < alerts.length; a++) {
                if (alerts[a].id === 'errorZonasBanner') continue;
                if (normalizeText(alerts[a].textContent).trim()) {
                    alert = alerts[a];
                    break;
                }
            }
            if (!alert) return -1;

            var text = normalizeText(alert.textContent);

            // Si el error habla de datos corporativos, el formulario no
            // repuebla la modalidad: la restauramos para que el bloque NIT /
            // razón social esté visible y el usuario pueda corregirlo.
            if ((text.indexOf('nit') !== -1 || text.indexOf('razon') !== -1)
                && document.getElementById('tipoEmpresa')) {
                document.getElementById('tipoEmpresa').checked = true;
                if (typeof window.toggleTipoTransportista === 'function') {
                    window.toggleTipoTransportista();
                }
            }

            var map = [
                { kind: 'cuenta', words: ['contrasena', 'correo', 'email', 'nombre'] },
                { kind: 'vehiculo', words: ['placa', 'licencia', 'telefono', 'celular', 'vehiculo', 'nit', 'razon', 'capacidad', 'empresa'] },
                { kind: 'zonas', words: ['zona', 'zonas', 'cobertura', 'municipio'] }
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
        form.classList.add('tp-steps-enabled');

        announce();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
