/**
 * carrito-ajax.js
 * Intercepta todos los formularios con action "/carrito/agregar"
 * y los convierte en llamadas AJAX para evitar el refresco de página.
 * Muestra un toast de confirmación o error sin navegar.
 */

(function () {
  'use strict';

  // ── Toast System ──────────────────────────────────────────────────────────

  function crearContenedorToast() {
    const existente = document.getElementById('cultivus-toast-container');
    if (existente) return existente;

    const container = document.createElement('div');
    container.id = 'cultivus-toast-container';
    container.style.cssText = `
      position: fixed;
      top: 24px;
      right: 24px;
      z-index: 99999;
      display: flex;
      flex-direction: column;
      gap: 10px;
      pointer-events: none;
    `;
    document.body.appendChild(container);
    return container;
  }

  function mostrarToast(mensaje, tipo) {
    const container = crearContenedorToast();

    const toast = document.createElement('div');
    const esExito = tipo === 'success';

    toast.style.cssText = `
      background: ${esExito ? '#27ae60' : '#dc3545'};
      color: white;
      padding: 14px 20px;
      border-radius: 10px;
      font-family: 'Segoe UI', sans-serif;
      font-size: 14px;
      font-weight: 500;
      box-shadow: 0 4px 20px rgba(0,0,0,0.2);
      display: flex;
      align-items: center;
      gap: 10px;
      max-width: 340px;
      pointer-events: all;
      transform: translateX(120%);
      transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
      cursor: pointer;
    `;

    const icono = esExito ? '🛒' : '⚠️';
    toast.innerHTML = `<span style="font-size:18px">${icono}</span><span>${mensaje}</span>`;

    container.appendChild(toast);

    // Animar entrada
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        toast.style.transform = 'translateX(0)';
      });
    });

    // Auto-cerrar a los 3.5 segundos
    const cerrar = () => {
      toast.style.transform = 'translateX(120%)';
      setTimeout(() => toast.remove(), 350);
    };

    setTimeout(cerrar, 3500);
    toast.addEventListener('click', cerrar);
  }

  // ── Cart Counter ──────────────────────────────────────────────────────────

  function actualizarContadorCarrito(totalItems) {
    // Busca cualquier elemento con clase o id que indique el contador del carrito
    const selectores = [
      '.cart-count',
      '.carrito-count',
      '#cart-count',
      '#cartCount',
      '[data-cart-count]'
    ];

    selectores.forEach(sel => {
      const el = document.querySelector(sel);
      if (el) {
        el.textContent = totalItems;
        // Pequeña animación de rebote para dar feedback visual
        el.style.transform = 'scale(1.4)';
        setTimeout(() => { el.style.transform = 'scale(1)'; }, 200);
      }
    });
  }

  // ── Interceptor de Formularios ────────────────────────────────────────────

  document.addEventListener('submit', function (e) {
    const form = e.target;

    // Solo interceptar formularios que vayan a /carrito/agregar
    const action = form.getAttribute('action') || '';
    if (!action.includes('/carrito/agregar')) return;
    // No interceptar si ya termina en -ajax (seguridad)
    if (action.includes('ajax')) return;

    e.preventDefault();

    // Deshabilitar el botón temporalmente para evitar doble submit
    const btnSubmit = form.querySelector('[type="submit"]');
    if (btnSubmit) {
      btnSubmit.disabled = true;
      btnSubmit.style.opacity = '0.6';
    }

    const formData = new FormData(form);

    fetch('/carrito/agregar-ajax', {
      method: 'POST',
      body: formData,
      headers: {
        'X-Requested-With': 'XMLHttpRequest'
      }
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        if (data.success) {
          mostrarToast(data.mensaje || '¡Producto agregado al carrito!', 'success');
          if (typeof data.totalItems === 'number') {
            actualizarContadorCarrito(data.totalItems);
          }
        } else {
          // Si el servidor pide redirigir al login
          if (data.redirectLogin) {
            window.location.href = '/usuario/login';
            return;
          }
          mostrarToast(data.error || 'Error al agregar al carrito', 'error');
        }
      })
      .catch(function () {
        mostrarToast('Error de conexión. Intenta de nuevo.', 'error');
      })
      .finally(function () {
        // Re-habilitar el botón
        if (btnSubmit) {
          btnSubmit.disabled = false;
          btnSubmit.style.opacity = '';
        }
      });
  });

})();
