/**
 * carrito-ajax.js
 * Intercepta formularios con action "/carrito/agregar" y los convierte en AJAX.
 * Feedback: botón con spinner/✓, toast compacto único, contador unificado.
 */
(function () {
  'use strict';

  var toastEl = null;
  var hideTimer = null;
  var lastAddTime = 0;
  var paused = false;

  function crearContenedor() {
    var c = document.getElementById('cultivus-toast-container');
    if (c) return c;
    c = document.createElement('div');
    c.id = 'cultivus-toast-container';
    c.className = 'cultivus-toast-container';
    document.body.appendChild(c);
    return c;
  }

  function actualizarPosicionToast() {
    var mobile = window.innerWidth <= 640;
    var offset = mobile ? 8 : 12;
    var maxBottom = 0;
    var headers = document.querySelectorAll('header, .site-header, .header-premium');
    headers.forEach(function (h) {
      var r = h.getBoundingClientRect();
      if (r.height > 0 && r.bottom > 0 && r.top < window.innerHeight) {
        if (r.bottom > maxBottom) maxBottom = r.bottom;
      }
    });
    var top = (maxBottom > 0 ? maxBottom : 0) + offset;
    if (top < offset) top = offset;
    document.documentElement.style.setProperty('--cv-toast-top', Math.round(top) + 'px');
  }

  function limpiarTimer() {
    if (hideTimer) { clearTimeout(hideTimer); hideTimer = null; }
  }

  function programarCierre(ms) {
    limpiarTimer();
    if (paused) return;
    hideTimer = setTimeout(cerrarToast, ms);
  }

  function cerrarToast() {
    if (!toastEl) return;
    toastEl.classList.remove('is-visible');
    toastEl.classList.add('is-leaving');
    var el = toastEl;
    toastEl = null;
    setTimeout(function () { el.remove(); }, 160);
  }

  function renderToast(texto, tipo, duracion) {
    actualizarPosicionToast();
    var container = crearContenedor();
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.className = 'cultivus-toast cultivus-toast--' + tipo;
      toastEl.setAttribute('role', 'status');
      toastEl.setAttribute('aria-live', 'polite');
      toastEl.innerHTML =
        '<span class="cultivus-toast__icon">' + (tipo === 'error' ? '!' : '✓') + '</span>' +
        '<span class="cultivus-toast__text"></span>';
      container.appendChild(toastEl);
      requestAnimationFrame(function () {
        requestAnimationFrame(function () {
          if (toastEl) toastEl.classList.add('is-visible');
        });
      });
      toastEl.addEventListener('mouseenter', function () { paused = true; limpiarTimer(); });
      toastEl.addEventListener('mouseleave', function () { paused = false; programarCierre(duracion); });
      toastEl.addEventListener('focusin', function () { paused = true; limpiarTimer(); });
      toastEl.addEventListener('focusout', function () { paused = false; programarCierre(duracion); });
    } else {
      toastEl.className = 'cultivus-toast cultivus-toast--' + tipo;
      toastEl.classList.add('is-visible');
      toastEl.classList.remove('is-leaving');
    }

    var textEl = toastEl.querySelector('.cultivus-toast__text');
    if (textEl) textEl.textContent = texto;

    programarCierre(duracion);
  }

  // actualizar posición si cambia el encabezado mientras el toast está visible
  window.addEventListener('scroll', function () {
    if (toastEl) actualizarPosicionToast();
  }, { passive: true });
  window.addEventListener('resize', function () {
    if (toastEl) actualizarPosicionToast();
  });

  // ── Contador unificado ─────────────────────────────────────────────────────
  function crearBadgeEn(enlace, clase) {
    var el = document.createElement(clase === 'cart-badge' ? 'span' : 'em');
    el.className = clase;
    el.textContent = '1';
    enlace.appendChild(el);
    return el;
  }

  function actualizarContadorCarrito(totalItems) {
    document.querySelectorAll('.cart-badge, .mobile-quick-badge').forEach(function (el) {
      if (totalItems > 0) {
        el.textContent = totalItems;
      } else {
        el.remove();
      }
    });

    if (totalItems > 0) {
      document.querySelectorAll('a[href*="/carrito"]').forEach(function (a) {
        if (a.classList.contains('mobile-quick-card')) return;
        if (!a.querySelector('.cart-badge')) crearBadgeEn(a, 'cart-badge');
        var b = a.querySelector('.cart-badge');
        if (b) b.textContent = totalItems;
      });
      document.querySelectorAll('a.mobile-quick-card[href*="/carrito"]').forEach(function (a) {
        if (!a.querySelector('.mobile-quick-badge')) crearBadgeEn(a, 'mobile-quick-badge');
        var b = a.querySelector('.mobile-quick-badge');
        if (b) b.textContent = totalItems;
      });
    }

    document.querySelectorAll('.cart-badge, .mobile-quick-badge').forEach(function (el) {
      el.classList.remove('updated');
      void el.offsetWidth;
      el.classList.add('updated');
      var onEnd = function () { el.classList.remove('updated'); el.removeEventListener('animationend', onEnd); };
      el.addEventListener('animationend', onEnd);
    });

    document.querySelectorAll('a[href*="/carrito"]').forEach(function (a) {
      a.setAttribute('aria-label', 'Carrito, ' + totalItems + ' productos');
    });
  }

  // ── Interceptor ────────────────────────────────────────────────────────────
  document.addEventListener('submit', function (e) {
    var form = e.target;
    var action = form.getAttribute('action') || '';
    if (!action.includes('/carrito/agregar')) return;
    if (action.includes('ajax')) return;

    e.preventDefault();

    var btnSubmit = form.querySelector('[type="submit"]');
    var originalHTML = btnSubmit ? btnSubmit.innerHTML : '';
    var originalMinWidth = '';
    if (btnSubmit) {
      btnSubmit.disabled = true;
      btnSubmit.setAttribute('aria-busy', 'true');
      originalMinWidth = btnSubmit.style.minWidth;
      btnSubmit.style.minWidth = btnSubmit.offsetWidth + 'px';
      btnSubmit.innerHTML = '<span class="cultivus-spinner" aria-hidden="true"></span>';
    }

    var formData = new FormData(form);
    var t0 = (typeof performance !== 'undefined') ? performance.now() : Date.now();
    var headers = {
      'X-Requested-With': 'XMLHttpRequest'
    };
    var csrfToken = formData.get('_csrf');
    if (csrfToken) {
      headers['X-CSRF-TOKEN'] = csrfToken;
    }

    fetch('/carrito/agregar-ajax', {
      method: 'POST',
      body: formData,
      headers: headers
    })
      .then(async function (res) {
        var text = await res.text();
        var data;
        try { data = JSON.parse(text); } catch (err) { data = { success: false, error: 'Respuesta no válida del servidor' }; }
        return { status: res.status, ok: res.ok, data: data };
      })
      .then(function (result) {
        var ms = ((typeof performance !== 'undefined') ? performance.now() : Date.now()) - t0;
        console.log('agregar-ajax ms:', Math.round(ms));
        if (ms > 300) {
          console.warn('agregar-ajax > 300ms. Optimización propuesta (sin aplicar): evitar usuarioRepository.findById en cada llamada de obtenerUsuarioActual.');
        }

        var data = result.data;
        if (result.ok && data && data.success) {
          lastAddTime = Date.now();

          var texto = (data.nombreProducto) ? data.nombreProducto : 'Producto';

          renderToast(texto, 'success', 2000);

          if (typeof data.totalItems === 'number') {
            actualizarContadorCarrito(data.totalItems);
          }

          if (btnSubmit) {
            btnSubmit.innerHTML = '<i class="fas fa-check"></i> Agregado';
            btnSubmit.style.background = 'var(--c-green, #367A45)';
            btnSubmit.style.opacity = '1';
            setTimeout(function () {
              btnSubmit.innerHTML = originalHTML;
              btnSubmit.disabled = false;
              btnSubmit.removeAttribute('aria-busy');
              btnSubmit.style.minWidth = originalMinWidth;
              btnSubmit.style.background = '';
            }, 1200);
          }
        } else {
          if (data && data.redirectLogin) {
            var currentUrl = encodeURIComponent(window.location.pathname + window.location.search);
            window.location.href = '/usuario/login?redirect=' + currentUrl + '&mensaje=' + encodeURIComponent('Inicia sesión para comprar');
            return;
          }
          var msg = (data && data.error) ? data.error : 'Error al agregar al carrito';
          renderToast(msg, 'error', 3500);
          if (btnSubmit) {
            btnSubmit.innerHTML = originalHTML;
            btnSubmit.disabled = false;
            btnSubmit.removeAttribute('aria-busy');
            btnSubmit.style.minWidth = originalMinWidth;
          }
        }
      })
      .catch(function () {
        renderToast('Error de conexión. Intenta de nuevo.', 'error', 3500);
        if (btnSubmit) {
          btnSubmit.innerHTML = originalHTML;
          btnSubmit.disabled = false;
          btnSubmit.removeAttribute('aria-busy');
          btnSubmit.style.minWidth = originalMinWidth;
        }
      });
  });
})();
