/**
 * CULTIVUS - Lógica de Animaciones y Navegación para la Vista "Nosotros"
 * Desarrollado con GSAP 3 + ScrollTrigger (Apple Design & High Performance)
 *
 * Arquitectura de Tolerancia a Fallos y Accesibilidad:
 * 1. Todos los elementos tienen opacity: 1 por defecto en CSS.
 * 2. Las animaciones solo ocultan elementos con gsap.set inmediatamente antes de su ScrollTrigger,
 *    y SOLO si el elemento está actualmente debajo del viewport.
 * 3. Cada sección se inicializa en una función independiente envuelta en try/catch.
 * 4. IntersectionObserver + Timed Sweeps garantizan que ningún elemento quede atascado con opacidad 0.
 * 5. Se refresca ScrollTrigger al cargar fuentes, imágenes, resize, load y pageshow.
 */

document.addEventListener("DOMContentLoaded", () => {
  const isGsapAvailable = typeof window.gsap !== "undefined" && typeof window.ScrollTrigger !== "undefined";

  if (!isGsapAvailable) {
    // Si GSAP no está disponible, asegurar visibilidad completa de respaldo
    document.documentElement.classList.remove("js-gsap");
    initFallbackNavigation();
    return;
  }

  // Activar bandera de inicialización
  document.documentElement.classList.add("js-gsap");
  gsap.registerPlugin(ScrollTrigger);

  try {
    initNosotrosApp();
  } catch (err) {
    console.error("[Cultivus] Error crítico al inicializar Nosotros:", err);
    forceRevealAllStuckElements();
    initFallbackNavigation();
  }
});

function initNosotrosApp() {
  const isReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // 1. Red de Seguridad y Observador de Visibilidad (Se activa de inmediato)
  initSafetyNet();

  // 2. Manejo de navegación por URL (hash #productores o /productores)
  try {
    checkInitialNavigation();
  } catch (e) {
    console.warn("[Cultivus] Error en checkInitialNavigation:", e);
  }

  // 3. Barra Segmentada Sticky Glass y Scrollspy
  try {
    initStickySegmentedNav();
  } catch (e) {
    console.warn("[Cultivus] Error en initStickySegmentedNav:", e);
  }

  if (isReducedMotion) {
    // Si el usuario prefiere movimiento reducido, asegurar visibilidad total y ejecutar solo contador estático
    forceRevealAllStuckElements();
    try {
      initCountUp(false);
    } catch (e) {
      console.warn("[Cultivus] Error en initCountUp reducido:", e);
    }
    return;
  }

  // 4. Inicializadores independientes por sección protegidos con try/catch

  // 4.1 Hero
  try {
    initHeroSection();
  } catch (e) {
    console.warn("[Cultivus] Error en initHeroSection:", e);
  }

  // 4.2 Manifiesto ("¿Qué es Cultivus?")
  try {
    initManifestoSection();
  } catch (e) {
    console.warn("[Cultivus] Error en initManifestoSection:", e);
  }

  // 4.3 Cómo Funciona (Crossfade en escritorio y pasos en móvil)
  try {
    initStepsSection();
  } catch (e) {
    console.warn("[Cultivus] Error en initStepsSection:", e);
  }

  // 4.4 Para Quién es Cultivus (Tarjetas de Audiencia)
  try {
    initAudiencesSection();
  } catch (e) {
    console.warn("[Cultivus] Error en initAudiencesSection:", e);
  }

  // 4.5 Nuestros Compromisos (4 Tarjetas)
  try {
    initCommitmentsSection();
  } catch (e) {
    console.warn("[Cultivus] Error en initCommitmentsSection:", e);
  }

  // 4.6 Cifras Honestas (Contador Numérico)
  try {
    initStatsCountUpSection();
  } catch (e) {
    console.warn("[Cultivus] Error en initStatsCountUpSection:", e);
  }

  // 4.7 Directorio de Productores
  try {
    initProducersSection();
  } catch (e) {
    console.warn("[Cultivus] Error en initProducersSection:", e);
  }

  // 4.8 Cierre Editorial / CTA
  try {
    initCtaSection();
  } catch (e) {
    console.warn("[Cultivus] Error en initCtaSection:", e);
  }

  // 5. Gestión del Ciclo de Vida: Refrescos de ScrollTrigger
  bindLifecycleRefreshes();
}

/**
 * 1. Red de Seguridad y Observador de Visibilidad Fail-Safe
 */
function initSafetyNet() {
  // IntersectionObserver: Revela cualquier elemento que esté o entre al viewport
  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting || entry.boundingClientRect.top < window.innerHeight) {
          const el = entry.target;
          const compOpacity = parseFloat(window.getComputedStyle(el).opacity);
          if (compOpacity < 0.95) {
            el.style.opacity = "1";
            el.style.transform = "none";
            if (typeof gsap !== "undefined") {
              gsap.set(el, { opacity: 1, y: 0, clearProps: "all" });
            }
          }
          observer.unobserve(el);
        }
      });
    }, {
      root: null,
      rootMargin: "150px 0px 150px 0px",
      threshold: 0.01
    });

    const targets = document.querySelectorAll(
      ".commitment-card, .stat-box, .audience-card, .producer-card, .about-step-item, .about-cta-section, .about-manifesto__card, .js-reveal"
    );
    targets.forEach((el) => observer.observe(el));
  }

  // Comprobaciones temporizadas (500ms, 1500ms, 3000ms)
  const sweeps = [500, 1500, 3000];
  sweeps.forEach((ms) => {
    setTimeout(() => {
      forceRevealVisibleStuckElements();
      if (typeof ScrollTrigger !== "undefined") {
        ScrollTrigger.refresh();
      }
    }, ms);
  });

  // Al hacer scroll hacia el final de la página (o presionar tecla End)
  window.addEventListener("scroll", () => {
    const scrollBottom = window.innerHeight + window.pageYOffset;
    const docHeight = document.documentElement.scrollHeight;
    if (scrollBottom >= docHeight - 350) {
      forceRevealAllStuckElements();
    }
  }, { passive: true });
}

function forceRevealVisibleStuckElements() {
  const vh = window.innerHeight || document.documentElement.clientHeight;
  const elements = document.querySelectorAll(
    ".commitment-card, .stat-box, .audience-card, .producer-card, .about-step-item, .about-cta-section, .about-manifesto__card, .js-reveal"
  );

  elements.forEach((el) => {
    const rect = el.getBoundingClientRect();
    if (rect.top <= vh + 120) {
      const compOpacity = parseFloat(window.getComputedStyle(el).opacity);
      if (compOpacity < 0.95) {
        el.style.opacity = "1";
        el.style.transform = "none";
        if (typeof gsap !== "undefined") {
          gsap.set(el, { opacity: 1, y: 0, clearProps: "all" });
        }
      }
    }
  });
}

function forceRevealAllStuckElements() {
  const elements = document.querySelectorAll(
    ".commitment-card, .stat-box, .audience-card, .producer-card, .about-step-item, .about-cta-section, .about-manifesto__card, .js-reveal, .about-hero__title-word, .about-manifesto__word"
  );

  elements.forEach((el) => {
    el.style.opacity = "1";
    el.style.transform = "none";
    if (typeof gsap !== "undefined") {
      gsap.set(el, { opacity: 1, y: 0, clearProps: "all" });
    }
  });
}

/**
 * Helper reutilizable para animar grupos de tarjetas de forma robusta
 */
function animateCardGroup(containerSelector, cardSelector) {
  const container = document.querySelector(containerSelector);
  if (!container) return;

  const cards = Array.from(container.querySelectorAll(cardSelector));
  if (!cards.length) return;

  const isReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (isReducedMotion) {
    cards.forEach((card) => {
      card.style.opacity = "1";
      card.style.transform = "none";
    });
    return;
  }

  const vh = window.innerHeight || document.documentElement.clientHeight;

  cards.forEach((card, index) => {
    const rect = card.getBoundingClientRect();

    // Si ya está en pantalla o por encima, mantener visible inmediatamente
    if (rect.top < vh * 0.92) {
      card.style.opacity = "1";
      card.style.transform = "none";
      return;
    }

    // Ocultar sólo inmediatamente antes de asociar el ScrollTrigger
    gsap.set(card, {
      opacity: 0,
      y: 28,
      willChange: "transform, opacity"
    });

    ScrollTrigger.create({
      trigger: card,
      start: "top 90%",
      once: true,
      onEnter: () => {
        gsap.to(card, {
          opacity: 1,
          y: 0,
          duration: 0.65,
          ease: "power3.out",
          delay: (index % 4) * 0.08,
          clearProps: "transform,opacity,willChange"
        });
      }
    });
  });
}

/**
 * 4.1 Hero Section
 */
function initHeroSection() {
  const isDesktop = window.matchMedia("(min-width: 861px)").matches;

  if (isDesktop) {
    const heroTl = gsap.timeline({ defaults: { ease: "power3.out" } });

    heroTl
      .fromTo(".about-hero__bg", 
        { scale: 1.12 }, 
        { scale: 1.0, duration: 1.6, ease: "power2.out" }
      )
      .fromTo(".about-hero__title-word",
        { opacity: 0, y: 36 },
        { opacity: 1, y: 0, stagger: 0.05, duration: 0.8, clearProps: "all" },
        "-=1.2"
      )
      .fromTo(".about-hero__lead, .about-hero__badge, .about-hero__actions",
        { opacity: 0, y: 20 },
        { opacity: 1, y: 0, stagger: 0.12, duration: 0.7, clearProps: "all" },
        "-=0.4"
      );

    // Parallax suave del fondo del hero
    gsap.to(".about-hero__bg", {
      yPercent: 18,
      ease: "none",
      scrollTrigger: {
        trigger: ".about-hero",
        start: "top top",
        end: "bottom top",
        scrub: true
      }
    });
  } else {
    // Móvil
    gsap.fromTo(".about-hero__title-word",
      { opacity: 0, y: 24 },
      { opacity: 1, y: 0, stagger: 0.04, duration: 0.7, ease: "power2.out", clearProps: "all" }
    );

    gsap.fromTo(".about-hero__lead, .about-hero__actions",
      { opacity: 0, y: 16 },
      { opacity: 1, y: 0, stagger: 0.1, duration: 0.6, ease: "power2.out", delay: 0.25, clearProps: "all" }
    );
  }
}

/**
 * 4.2 Manifiesto ("¿Qué es Cultivus?")
 */
function initManifestoSection() {
  const manifesto = document.querySelector(".about-manifesto");
  if (!manifesto) return;

  const words = manifesto.querySelectorAll(".about-manifesto__word");
  const card = manifesto.querySelector(".about-manifesto__card");

  if (words.length) {
    gsap.fromTo(words,
      { opacity: 0.25 },
      {
        opacity: 1,
        stagger: 0.03,
        ease: "none",
        scrollTrigger: {
          trigger: manifesto,
          start: "top 75%",
          end: "bottom 55%",
          scrub: 0.4
        }
      }
    );
  }

  if (card) {
    const rect = card.getBoundingClientRect();
    if (rect.top < window.innerHeight * 0.95) {
      card.style.opacity = "1";
      card.style.transform = "none";
    } else {
      gsap.set(card, { opacity: 0, y: 28, scale: 0.97 });
      ScrollTrigger.create({
        trigger: card,
        start: "top 88%",
        once: true,
        onEnter: () => {
          gsap.to(card, {
            opacity: 1,
            y: 0,
            scale: 1,
            duration: 0.85,
            ease: "power3.out",
            clearProps: "all"
          });
        }
      });
    }
  }
}

/**
 * 4.3 Cómo Funciona (Crossfade en escritorio y pasos en móvil)
 */
function initStepsSection() {
  const stepItems = document.querySelectorAll(".about-step-item");
  const mediaSlides = document.querySelectorAll(".about-steps-media__slide");
  const isDesktop = window.matchMedia("(min-width: 861px)").matches;

  if (isDesktop && stepItems.length && mediaSlides.length) {
    // Crossfade en escritorio sincronizado
    stepItems.forEach((item, index) => {
      ScrollTrigger.create({
        trigger: item,
        start: "top 60%",
        end: "bottom 60%",
        onEnter: () => activateStep(index),
        onEnterBack: () => activateStep(index)
      });
    });

    function activateStep(idx) {
      stepItems.forEach((el, i) => {
        el.classList.toggle("is-active", i === idx);
      });
      mediaSlides.forEach((slide, i) => {
        slide.classList.toggle("is-active", i === idx);
      });
    }
  }

  // Revelado seguro de los 3 pasos
  animateCardGroup(".about-steps-list", ".about-step-item");
}

/**
 * 4.4 Para Quién es Cultivus (3 Audiencias)
 */
function initAudiencesSection() {
  animateCardGroup(".about-audiences__grid", ".audience-card");
}

/**
 * 4.5 Nuestros Compromisos (4 Tarjetas)
 */
function initCommitmentsSection() {
  animateCardGroup(".commitments-grid", ".commitment-card");
}

/**
 * 4.6 Cifras Honestas (Contador Numérico)
 */
function initStatsCountUpSection() {
  animateCardGroup(".about-stats-grid", ".stat-box");
  initCountUp(true);
}

/**
 * 4.7 Directorio de Productores
 */
function initProducersSection() {
  animateCardGroup(".producers-grid", ".producer-card");
}

/**
 * 4.8 Cierre Editorial / CTA
 */
function initCtaSection() {
  const ctaSection = document.querySelector(".about-cta-section");
  if (!ctaSection) return;

  const rect = ctaSection.getBoundingClientRect();
  if (rect.top < window.innerHeight * 0.95) {
    ctaSection.style.opacity = "1";
    ctaSection.style.transform = "none";
  } else {
    gsap.set(ctaSection, { opacity: 0, y: 32 });
    ScrollTrigger.create({
      trigger: ctaSection,
      start: "top 88%",
      once: true,
      onEnter: () => {
        gsap.to(ctaSection, {
          opacity: 1,
          y: 0,
          duration: 0.8,
          ease: "power3.out",
          clearProps: "all"
        });
      }
    });
  }

  // Parallax suave del fondo del CTA
  const isDesktop = window.matchMedia("(min-width: 861px)").matches;
  if (isDesktop) {
    gsap.to(".about-cta-section__bg", {
      yPercent: 14,
      ease: "none",
      scrollTrigger: {
        trigger: ".about-cta-section",
        start: "top bottom",
        end: "bottom top",
        scrub: true
      }
    });
  }
}

/**
 * Contador numérico fluido para las estadísticas
 */
function initCountUp(animate = true) {
  const statElements = document.querySelectorAll(".js-count");
  if (!statElements.length) return;

  statElements.forEach((el) => {
    const target = parseInt(el.getAttribute("data-count") || el.textContent, 10);
    if (isNaN(target)) return;

    if (!animate) {
      el.textContent = target;
      return;
    }

    const obj = { val: 0 };
    ScrollTrigger.create({
      trigger: el,
      start: "top 92%",
      once: true,
      onEnter: () => {
        gsap.to(obj, {
          val: target,
          duration: 1.5,
          ease: "power2.out",
          onUpdate: () => {
            el.textContent = Math.floor(obj.val);
          },
          onComplete: () => {
            el.textContent = target;
          }
        });
      }
    });
  });
}

/**
 * Control Segmentado Sticky Glass con Scrollspy
 */
function initStickySegmentedNav() {
  const btnSobreNosotros = document.getElementById("tabSobreNosotros");
  const btnProductores = document.getElementById("tabProductores");
  const secSobreNosotros = document.getElementById("sobre-nosotros");
  const secProductores = document.getElementById("productores");

  if (!btnSobreNosotros || !btnProductores) return;

  btnSobreNosotros.addEventListener("click", (e) => {
    e.preventDefault();
    scrollToSection("sobre-nosotros");
  });

  btnProductores.addEventListener("click", (e) => {
    e.preventDefault();
    scrollToSection("productores");
  });

  // Scrollspy sincronizado con ScrollTrigger
  if (secSobreNosotros && secProductores) {
    ScrollTrigger.create({
      trigger: secSobreNosotros,
      start: "top 30%",
      end: "bottom 30%",
      onToggle: (self) => {
        if (self.isActive) setActiveTab("sobre-nosotros");
      }
    });

    ScrollTrigger.create({
      trigger: secProductores,
      start: "top 30%",
      end: "bottom 30%",
      onToggle: (self) => {
        if (self.isActive) setActiveTab("productores");
      }
    });
  }
}

function setActiveTab(id) {
  const btnSobreNosotros = document.getElementById("tabSobreNosotros");
  const btnProductores = document.getElementById("tabProductores");
  if (!btnSobreNosotros || !btnProductores) return;

  if (id === "productores") {
    btnProductores.classList.add("is-active");
    btnSobreNosotros.classList.remove("is-active");
  } else {
    btnSobreNosotros.classList.add("is-active");
    btnProductores.classList.remove("is-active");
  }
}

function scrollToSection(id) {
  const target = document.getElementById(id);
  if (!target) return;

  const headerOffset = 136; // 64px header + ~52px sticky bar + 20px margen Apple Design
  const targetPosition = target.getBoundingClientRect().top + window.pageYOffset - headerOffset;

  window.scrollTo({
    top: Math.max(0, targetPosition),
    behavior: "smooth"
  });

  setActiveTab(id);
  if (history.replaceState) {
    history.replaceState(null, "", "#" + id);
  }
}

/**
 * Manejo de rutas y hashes al cargar la página
 */
function checkInitialNavigation() {
  const hash = window.location.hash.replace("#", "");
  const path = window.location.pathname;

  const shouldGoToProductores = hash === "productores" || path.endsWith("/productores");

  if (shouldGoToProductores) {
    // Si viene directo a productores, forzar revelado inmediato de todas las tarjetas
    const producerCards = document.querySelectorAll("#productores .producer-card, #productores .js-reveal");
    producerCards.forEach((el) => {
      el.style.opacity = "1";
      el.style.transform = "none";
      if (typeof gsap !== "undefined") {
        gsap.set(el, { opacity: 1, y: 0, clearProps: "all" });
      }
    });

    setTimeout(() => {
      scrollToSection("productores");
    }, 250);
  } else if (hash && hash !== "") {
    setTimeout(() => {
      scrollToSection(hash);
    }, 250);
  }
}

/**
 * Refrescos y sincronización de ScrollTrigger con el ciclo de vida de la página
 */
function bindLifecycleRefreshes() {
  // Refrescar cuando se cargan las fuentes web
  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(() => {
      if (typeof ScrollTrigger !== "undefined") {
        ScrollTrigger.refresh();
      }
    });
  }

  // Refrescar cuando cada imagen termine de cargar
  const images = document.querySelectorAll(".about-page img");
  images.forEach((img) => {
    if (img.complete) return;
    img.addEventListener("load", () => {
      if (typeof ScrollTrigger !== "undefined") {
        ScrollTrigger.refresh();
      }
    }, { once: true });
  });

  // Refrescar en load y pageshow (soporte para historial hacia adelante/atrás en Safari)
  window.addEventListener("load", () => {
    forceRevealVisibleStuckElements();
    if (typeof ScrollTrigger !== "undefined") {
      ScrollTrigger.refresh();
    }
  });

  window.addEventListener("pageshow", () => {
    forceRevealVisibleStuckElements();
    if (typeof ScrollTrigger !== "undefined") {
      ScrollTrigger.refresh();
    }
  });

  // Debounced resize
  let resizeTimeout;
  window.addEventListener("resize", () => {
    clearTimeout(resizeTimeout);
    resizeTimeout = setTimeout(() => {
      forceRevealVisibleStuckElements();
      if (typeof ScrollTrigger !== "undefined") {
        ScrollTrigger.refresh();
      }
    }, 200);
  });
}

/**
 * Navegación de respaldo para cuando JS/GSAP esté desactivado o falle
 */
function initFallbackNavigation() {
  const btn1 = document.getElementById("tabSobreNosotros");
  const btn2 = document.getElementById("tabProductores");

  if (btn1) {
    btn1.addEventListener("click", () => {
      const el = document.getElementById("sobre-nosotros");
      if (el) el.scrollIntoView({ behavior: "smooth" });
    });
  }

  if (btn2) {
    btn2.addEventListener("click", () => {
      const el = document.getElementById("productores");
      if (el) el.scrollIntoView({ behavior: "smooth" });
    });
  }

  const hash = window.location.hash.replace("#", "");
  const path = window.location.pathname;
  if (hash === "productores" || path.endsWith("/productores")) {
    const el = document.getElementById("productores");
    if (el) el.scrollIntoView({ behavior: "smooth" });
  }
}
