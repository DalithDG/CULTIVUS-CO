(function () {
    'use strict';
    function maskPhone(input) {
        var digits = input.value.replace(/\D/g, '').slice(0, 10);
        if (digits.length > 6) digits = digits.slice(0,3) + ' ' + digits.slice(3,6) + ' ' + digits.slice(6);
        else if (digits.length > 3) digits = digits.slice(0,3) + ' ' + digits.slice(3);
        input.value = digits;
    }
    function validatePhone(input, err) {
        var digits = input.value.replace(/\D/g, '');
        if (digits.length !== 10 || digits.charAt(0) !== '3') {
            if (err) err.textContent = 'Ingresa un celular válido de 10 dígitos que empiece por 3';
            input.setCustomValidity('teléfono inválido');
            return false;
        }
        if (err) err.textContent = '';
        input.setCustomValidity('');
        return true;
    }
    document.addEventListener('DOMContentLoaded', function () {
        var phone = document.getElementById('telefonoContacto');
        var err = document.getElementById('telefonoError');
        var errCat = document.getElementById('catError');
        var checkboxes = [];
        var catMultiRef = null;
        var catTouched = false;
        if (phone) {
            phone.addEventListener('input', function () { maskPhone(phone); });
            phone.addEventListener('blur', function () { validatePhone(phone, err); });
            var form = phone.closest('form');
            if (form) form.addEventListener('submit', function (e) {
                var digits = phone.value.replace(/\D/g, '');
                if (!validatePhone(phone, err)) { e.preventDefault(); phone.focus(); return; }
                if (checkboxes.length) {
                    var chosen = checkboxes.filter(function (c) { return c.checked; });
                    if (chosen.length === 0) {
                        catTouched = true;
                        if (errCat) errCat.textContent = 'Elige al menos una categoría';
                        e.preventDefault();
                        var catBtn = catMultiRef && catMultiRef.querySelector('.cat-multi__btn');
                        if (catBtn) catBtn.focus();
                        return;
                    }
                }
                phone.value = digits;
                if (form.id !== 'registroVendedorForm') return;
                var btn = form.querySelector('button[type="submit"]');
                if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Registrando...'; }
            });
        }
        // municipios fallback
        var ciudad = document.getElementById('ciudad');
        if (ciudad && ciudad.options.length <= 1 && window.MUNICIPIOS_BOLIVAR) {
            ciudad.innerHTML = '<option value="">Selecciona tu municipio</option>';
            window.MUNICIPIOS_BOLIVAR.forEach(function (m) {
                var opt = document.createElement('option');
                opt.value = m; opt.textContent = m;
                ciudad.appendChild(opt);
            });
        }
        var selected = ciudad && ciudad.getAttribute('data-selected');
        if (selected && ciudad) {
            Array.prototype.forEach.call(ciudad.options, function (o) { if (o.value === selected) o.selected = true; });
        }

        // Category multi-select
        var catMulti = document.querySelector('[data-cat-multi]');
        if (catMulti) {
            catMulti.classList.add('cat-enhanced');
            catMultiRef = catMulti;
            var btn = catMulti.querySelector('.cat-multi__btn');
            var panel = catMulti.querySelector('.cat-multi__panel');
            var text = catMulti.querySelector('.cat-multi__text');
            var countEl = catMulti.querySelector('.cat-multi__count');
            var max = parseInt(catMulti.getAttribute('data-cat-max'), 10) || 4;
            checkboxes = Array.prototype.slice.call(panel.querySelectorAll('input[type="checkbox"]'));

            function update() {
                var selected = checkboxes.filter(function (c) { return c.checked; }).map(function (c) { return c.value; });
                if (selected.length === 0) {
                    text.textContent = 'Selecciona tus categorías';
                } else if (selected.length <= 2) {
                    text.textContent = selected.join(', ');
                } else {
                    text.textContent = selected.slice(0, 2).join(', ') + ' +' + (selected.length - 2);
                }
                if (countEl) countEl.textContent = selected.length + ' de ' + max;
                checkboxes.forEach(function (c) {
                    var block = !c.checked && selected.length >= max;
                    c.disabled = block;
                    if (c.parentElement) c.parentElement.classList.toggle('disabled', block);
                });
                if (selected.length === 0) {
                    if (errCat) errCat.textContent = catTouched ? 'Elige al menos una categoría' : '';
                    btn.setCustomValidity(catTouched ? 'Elige al menos una categoría' : '');
                } else {
                    if (errCat) errCat.textContent = '';
                    btn.setCustomValidity('');
                }
            }
            checkboxes.forEach(function (c) { c.addEventListener('change', update); });
            update();

            // Al intentar enviar, avisa si no hay categorías (aunque otros campos
            // aún no sean válidos y el navegador bloquee el submit).
            var submitters = document.querySelectorAll('button[type="submit"][form="registroVendedorForm"], #registroVendedorForm button[type="submit"]');
            Array.prototype.forEach.call(submitters, function (s) {
                s.addEventListener('click', function () {
                    if (checkboxes.some(function (c) { return c.checked; })) return;
                    catTouched = true;
                    update();
                });
            });

            function open() {
                panel.classList.add('is-open');
                btn.setAttribute('aria-expanded', 'true');
                var rect = panel.getBoundingClientRect();
                if (rect.bottom > window.innerHeight) {
                    var delta = Math.min(rect.bottom - window.innerHeight + 16, Math.max(0, rect.top - 16));
                    if (delta > 0) window.scrollBy(0, delta);
                }
            }
            function close(markTouched) {
                var wasOpen = panel.classList.contains('is-open');
                panel.classList.remove('is-open');
                btn.setAttribute('aria-expanded', 'false');
                if (wasOpen && markTouched) { catTouched = true; update(); }
            }
            btn.addEventListener('click', function () {
                if (panel.classList.contains('is-open')) { close(true); btn.focus(); }
                else open();
            });
            btn.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') { e.preventDefault(); open(); }
            });
            catMulti.addEventListener('keydown', function (e) {
                if (e.key === 'Escape') { e.preventDefault(); close(true); btn.focus(); }
            });
            catMulti.addEventListener('focusout', function (e) {
                if (catMulti.contains(e.relatedTarget)) return;
                // Al pulsar una etiqueta del panel el foco puede salir antes de
                // que se procese el clic. Esperamos un instante para no cerrar
                // el desplegable encima de la propia selección.
                setTimeout(function () {
                    if (!catMulti.contains(document.activeElement)) close(true);
                }, 120);
            });
            document.addEventListener('click', function (e) {
                if (!catMulti.contains(e.target) && panel.classList.contains('is-open')) close(true);
            });
        }
    });
})();