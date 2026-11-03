/**
 * carrito-ajax.js
 * Intercepta todos los formularios con action "/carrito/agregar"
 * y los convierte en llamadas AJAX para evitar el refresco de página.
 * Muestra un toast Apple-grade de confirmación o error sin navegar.
 */

(function () {
  'use strict';

  // ── Toast System ──────────────────────────────────────────────────────────

  function crearContenedorToast() {
    var existente = document.getElementById('cultivus-toast-container');
    if (existente) return existente;

    var container = document.createElement('div');
    container.id = 'cultivus-toast-container';
    container.style.cssText = 'position:fixed;top:24px;right:24px;z-index:99999;display:flex;flex-direction:column;gap:10px;pointer-events:none;';
    document.body.appendChild(container);
    return container;
  }

  function mostrarToast(mensaje, tipo) {
    var container = crearContenedorToast();
    var toast = document.createElement('div');
    var esExito = tipo === 'success';

    toast.style.cssText =
      'background:' + (esExito ? '#367A45' : '#8A2D25') + ';' +
      'color:white;padding:14px 20px;border-radius:12px;' +
      'font-family:-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", sans-serif;' +
      'font-size:14px;font-weight:500;box-shadow:0 8px 24px rgba(0,0,0,0.18);' +
      'display:flex;align-items:center;gap:12px;max-width:380px;pointer-events:all;' +
      'transform:translateX(120%);transition:transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);cursor:pointer;';

    var icono = esExito
      ? '<i class="fas fa-check-circle" style="font-size:18px;"></i>'
      : '<i class="fas fa-exclamation-circle" style="font-size:18px;"></i>';
    toast.innerHTML = icono + '<span>' + mensaje + '</span>';

    container.appendChild(toast);

    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        toast.style.transform = 'translateX(0)';
      });
    });

    var cerrado = false;
    function cerrar() {
      if (cerrado) return;
      cerrado = true;
      toast.style.transform = 'translateX(120%)';
      setTimeout(function () { toast.remove(); }, 350);
    }

    setTimeout(cerrar, 3500);
    toast.addEventListener('click', cerrar);
  }

  // ── Cart Counter ──────────────────────────────────────────────────────────

  function actualizarContadorCarrito(totalItems) {
    var selectores = [
      '.cart-count',
      '.carrito-count',
      '.cart-badge',
      '#cart-count',
      '#cartCount',
      '[data-cart-count]'
    ];

    selectores.forEach(function (sel) {
      var elementos = document.querySelectorAll(sel);
      elementos.forEach(function (el) {
        el.textContent = totalItems;
        el.style.transform = 'scale(1.35)';
        el.style.transition = 'transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1)';
        setTimeout(function () { el.style.transform = 'scale(1)'; }, 200);
      });
    });
  }

  // ── Interceptor de Formularios ───────────────────────────────────────────

  document.addEventListener('submit', function (e) {
    var form = e.target;
    var action = form.getAttribute('action') || '';
    if (!action.includes('/carrito/agregar')) return;
    if (action.includes('ajax')) return;

    e.preventDefault();

    var btnSubmit = form.querySelector('[type="submit"]');
    if (btnSubmit) {
      btnSubmit.disabled = true;
      btnSubmit.style.opacity = '0.6';
    }

    var formData = new FormData(form);

    fetch('/carrito/agregar-ajax', {
      method: 'POST',
      body: formData,
      headers: {
        'X-Requested-With': 'XMLHttpRequest'
      }
    })
      .then(async function (res) {
        var data;
        var text = await res.text();
        try {
          data = JSON.parse(text);
        } catch (err) {
          data = { success: false, error: 'Respuesta no válida del servidor' };
        }
        return { status: res.status, ok: res.ok, data: data };
      })
      .then(function (result) {
        var data = result.data;
        if (result.ok && data && data.success) {
          mostrarToast(data.mensaje || '¡Producto agregado al carrito!', 'success');
          if (typeof data.totalItems === 'number') {
            actualizarContadorCarrito(data.totalItems);
          }
        } else {
          if (data && data.redirectLogin) {
            var currentUrl = encodeURIComponent(window.location.pathname + window.location.search);
            window.location.href = '/usuario/login?redirect=' + currentUrl + '&mensaje=' + encodeURIComponent('Inicia sesión para comprar');
            return;
          }
          mostrarToast((data && data.error) ? data.error : 'Error al agregar al carrito', 'error');
        }
      })
      .catch(function () {
        mostrarToast('Error de conexión. Intenta de nuevo.', 'error');
      })
      .finally(function () {
        if (btnSubmit) {
          btnSubmit.disabled = false;
          btnSubmit.style.opacity = '';
        }
      });
  });

})();