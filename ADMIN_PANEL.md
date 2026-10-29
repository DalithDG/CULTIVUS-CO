# Panel de administración · Cultivus

Refactor del panel de administrador para separar cada sección en su propia
plantilla y alinear la identidad visual con el panel de vendedor.

## Estructura nueva

```
templates/admin/
├── dashboard.html
├── powerbi.html
├── usuarios.html
├── resenas.html
├── productos.html
├── ofertas.html
├── pedidos.html
├── verificacion-tiendas.html
├── transportistas.html
├── sesiones.html
├── notificaciones.html
├── mensajes.html
├── configuracion.html
├── moderacion.html
├── fragments/
│   ├── head.html
│   ├── topbar.html
│   ├── sidebar.html
│   └── alerts.html
└── admin-dashboard.legacy.html   (respaldo no usado)
```

Cada página incluye:

```html
<head th:replace="~{admin/fragments/head :: head('Título')}"></head>
<header th:replace="~{admin/fragments/topbar :: topbar('Título')}"></header>
<aside th:replace="~{admin/fragments/sidebar :: sidebar}"></aside>
```

El layout vive en `fragments/`; el contenido de cada sección está en su propio
archivo. Ya no se cargan las 25 secciones ocultas en una sola página.

## Rutas y plantilla

| Ruta | Plantilla | Título |
|---|---|---|
| `/admin/dashboard` | `dashboard.html` | Dashboard |
| `/admin/powerbi` | `powerbi.html` | Inteligencia de Negocios |
| `/admin/usuarios` | `usuarios.html` | Gestionar Usuarios |
| `/admin/resenas` | `resenas.html` | Actividad y Reseñas |
| `/admin/productos` | `productos.html` | Catálogo de Productos |
| `/admin/ofertas` | `ofertas.html` | Ofertas Globales |
| `/admin/pedidos` | `pedidos.html` | Gestión de Pedidos |
| `/admin/verificacion-tiendas` | `verificacion-tiendas.html` | Verificación de Tiendas |
| `/admin/transportistas` | `transportistas.html` | Gestión de Transportistas |
| `/admin/sesiones` | `sesiones.html` | Sesiones Activas |
| `/admin/notificaciones` | `notificaciones.html` | Gestión de Notificaciones |
| `/admin/mensajes` | `mensajes.html` | Centro de Mensajes |
| `/admin/configuracion` | `configuracion.html` | Configuración del Sistema |
| `/admin/moderacion` | `moderacion.html` | Moderar Ofertas y Catálogo |

`AdminController` conserva toda la lógica de permisos, paginación y acciones
POST; solo cambió el nombre de la vista retornada.

## Paleta

`static/admin-panel-v2.css` usa las variables del panel de vendedor:

| Variable | Valor | Uso |
|---|---|---|
| `--adm-green` | `#1f7a3a` | Color principal |
| `--adm-green-dark` | `#14552a` | Hover / énfasis |
| `--adm-green-deep` | `#0a351a` | Degradados |
| `--adm-green-light` | `#e9f6ed` | Fondos activos |
| `--adm-green-soft` | `#f4fbf6` | Fondos suaves |
| `--adm-lime` | `#b8e986` | Acento |
| `--adm-ink` | `#111e15` | Texto principal |
| `--adm-muted` | `#5e7264` | Texto secundario |
| `--adm-line` | `#e2ece4` | Bordes |
| `--adm-bg` | `#f5f9f6` | Fondo general |
| `--adm-amber` | `#b45309` | Advertencias |
| `--adm-red` | `#b91c1c` | Errores / eliminar |
| `--adm-blue` | `#1d4ed8` | Información |

También se reemplazaron los azules y morados inline del contenido anterior por
variables de esta paleta.

## Layout y responsive

- Topbar fijo de 64 px con logo, breadcrumb, notificaciones, mensajes, perfil y
  cierre de sesión.
- Sidebar blanco de 252 px con grupos: Principal, Gestión y Sistema.
- En escritorio, el botón de menú colapsa el sidebar (se recuerda en
  `localStorage`).
- En móvil (≤1024 px), el sidebar se abre como drawer con overlay; se cierra con
  el overlay, Escape o al navegar.
- `admin-panel.js` ya no oculta/muestra páginas: cada enlace recarga su ruta.

## Cómo añadir una sección

1. Crea `templates/admin/<nombre>.html` usando los fragments de `head`,
   `topbar`, `sidebar` y `alerts`.
2. Añade el método en `AdminController` y retorna
   `"admin/<nombre>"`.
3. Agrega el enlace en `fragments/sidebar.html` con
   `th:classappend` basado en `currentPath`.
4. Si la sección necesita datos, cárgalos en `cargarDatosDashboard` o en el
   método del controlador.
5. Verifica con una sesión de administrador que la ruta responde 200.

## Verificación realizada

- Compilación: `BUILD SUCCESS`.
- Las 14 rutas de administrador respondieron HTTP 200 con sesión de admin.
- Se comprobó que el HTML renderizado usa `admin-sidebar`, `admin-topbar`,
  `admin-hero` y `admin-panel-v2.css`; ya no aparece el wrapper antiguo
  `<div class="page">`.
- Suite completa: **156 pruebas, 0 fallos, 0 errores**.

## Archivo legado

`admin-dashboard.legacy.html` se conservó como referencia de las secciones
antiguas que no tienen ruta activa (roles, inventario, reportes, respaldos,
etc.). Ninguna plantilla ni controlador lo referencia; se puede eliminar cuando
se confirme que no se reutilizará.
