# Datos de demostración (`DemoDataSeeder`)

Sembrador de datos ficticios para la expo de CULTIVUS. Se ejecuta **solo** si las
variables de entorno lo activan; por defecto la aplicación arranca igual que siempre.

- Clase: `com.example.demo.Config.DemoDataSeeder`
- Orden: `@Order(2)`, después del `DataLoader` (`@Order(1)`) y del catálogo inicial.
- Ejecución: en un hilo daemon en segundo plano, con captura total de errores. Un fallo
  del sembrador **nunca** impide el arranque de la aplicación.

## Variables de entorno

| Variable | Efecto | Valor por defecto |
|---|---|---|
| `SEED_DEMO_DATA` | `true` siembra usuarios, ofertas, compras, pedidos, envíos, billeteras y reseñas demo. | apagado |
| `SEED_DEMO_CLEANUP` | `true` borra únicamente los datos con marcadores demo. | apagado |
| `SEED_DEMO_PASSWORD` | Contraseña compartida por todas las cuentas demo. Obligatoria si `SEED_DEMO_DATA=true`. No se versiona ni se imprime en logs. | vacío |

Valores aceptados como verdadero: `true` o `1` (sin distinguir mayúsculas).

Si `SEED_DEMO_DATA=true` y falta `SEED_DEMO_PASSWORD`, o si la contraseña no cumple
`PasswordPolicy`, el sembrador registra el motivo y **no crea datos**, pero la
aplicación continúa arrancando.

## Activación local

PowerShell:

```powershell
$env:SEED_DEMO_DATA   = "true"
$env:SEED_DEMO_CLEANUP = "false"
$env:SEED_DEMO_PASSWORD = "<clave-fuerte-no-versionada>"
.\mvnw.cmd spring-boot:run
```

Bash:

```bash
SEED_DEMO_DATA=true SEED_DEMO_CLEANUP=false \
SEED_DEMO_PASSWORD='<clave-fuerte-no-versionada>' \
./mvnw spring-boot:run
```

Para regenerar desde cero en un solo arranque:

```powershell
$env:SEED_DEMO_DATA = "true"
$env:SEED_DEMO_CLEANUP = "true"
```

El orden interno es limpieza y luego siembra.

## Activación en Render

`demo/render.yaml` ya declara las tres variables con `sync: false`. En el panel de
Render, define los valores del servicio:

1. `SEED_DEMO_DATA=true` para el despliegue de la expo.
2. `SEED_DEMO_PASSWORD` con la clave que usarán las cuentas demo.
3. `SEED_DEMO_CLEANUP=false` (o `true` solo si quieres regenerar).

Después de sembrar, puedes volver `SEED_DEMO_DATA` a `false` para que los reinicios
no intenten sembrar de nuevo. El sembrador es idempotente: si ya encuentra correos
`@demo.cultivus.co`, no duplica nada.

> Render vuelve a desplegar en cada push a la rama configurada (`autoDeploy: true`).
> No hagas push a `main` para probar el sembrador.

## Cuentas de demostración

Todas usan la contraseña definida en `SEED_DEMO_PASSWORD`.

| Rol | Correo | Uso en la expo |
|---|---|---|
| Comprador | `comprador@demo.cultivus.co` | Historial variado: 9 compras, 13 pedidos, 5 vendedores. |
| Vendedor | `vendedor@demo.cultivus.co` | Vitrina de frutas con 14 ofertas y 10 pedidos (2 en `CONFIRMADO`). |
| Transportista | `transportista@demo.cultivus.co` | 5 entregas y envíos disponibles en su zona. |
| Administrador | `admin@demo.cultivus.co` | Solo consulta/moderación. `SecurityConfig` lo aísla de `/usuario/**`; no compra ni vende. |

Cuentas regulares:

- `vendedor01@demo.cultivus.co` … `vendedor40@demo.cultivus.co`
- `comprador01@demo.cultivus.co` … `comprador30@demo.cultivus.co`
- `transportista01@demo.cultivus.co` … `transportista20@demo.cultivus.co`

Las cuentas `admin1@demo.com` y `transportista@demo.com` creadas por `DataLoader`
**no se tocan**.

## Qué genera

- 94 usuarios demo: 41 vendedores, 31 compradores, 21 transportistas y 1 administrador.
  - 40 vendedores regulares + 1 destacado.
  - 30 compradores regulares + 1 destacado.
  - 10 transportistas `TIPO_INDEPENDIENTE`, 10 `TIPO_EMPRESA` + 1 destacado.
  - Perfiles completos; ~85 % verificados.
- 273 ofertas: 259 `PUBLICADO` y 14 `PENDIENTE` (~5 %).
  - Los 57 productos del catálogo tienen al menos 3 vendedores.
  - 6–7 ofertas por vendedor regular; 14 frutas en el vendedor destacado.
  - Sin producto duplicado por vendedor, sin stock negativo y sin subirlas a Cloudinary.
- 40 compras y 50 pedidos, todos `DOMICILIO`, con fechas de los últimos ~57 días.
  - 30 compras de un pedido y 10 de dos pedidos.
  - Distribución: 25 `ENTREGADO`, 4 `ENVIADO`, 4 `LISTO_PARA_RECOGER`, 3 `CONFIRMADO`,
    4 `EN_PROCESO`, 5 `PENDIENTE`, 3 `CANCELADO` y 2 `EN_REVISION`.
  - Totales consistentes con ítems, envío y pago; stock descontado solo en ventas confirmadas.
- 33 envíos con paradas y `SnapshotTransportista`: 19 entregados, 6 en entrega,
  6 asignados y 2 pendientes de asignación en la zona del transportista destacado.
- 31 billeteras (18 vendedores y 13 transportistas), movimientos de venta/flete,
  saldos retenidos/disponibles y 3 solicitudes de retiro pendientes.
- 60–100 reseñas (en la última corrida: 78, de las cuales 53 son de producto y 25 de
  vendedor), solo sobre pedidos entregados, con sesgo 4–5 y comentarios costeños
  naturales. Se actualiza `calificacionPromedio` en el snapshot del vendedor dentro
  de sus ofertas.
- Métricas de `ProductoCatalogo` recalculadas al final (ofertas disponibles y publicadas).

## Marcadores y datos ficticios

- Correos: terminan en `@demo.cultivus.co`.
- Referencias de pago: prefijo `DEMO-`.
- Transacciones: prefijo `DEMO-TX-`.
- Teléfonos: `300000xxxx`.
- Cuentas bancarias legadas: `0000-DEMO-xxxx`.
- Placas: `ABC123`.
- Contraseña: solo en `SEED_DEMO_PASSWORD`; nunca en código, logs ni Git.

## Limpieza

```powershell
$env:SEED_DEMO_DATA = "false"
$env:SEED_DEMO_CLEANUP = "true"
.\mvnw.cmd spring-boot:run
```

La limpieza borra solo registros demo, en este orden seguro:

1. `resenas` y `resenas_vendedor`
2. `notificaciones`
3. `movimientos_billetera`
4. `billeteras`
5. `solicitudes_retiro`
6. `envios`
7. `pedidos`
8. `compras`
9. `ofertas`
10. `usuarios` con dominio demo

Al final recalcula las métricas de `ProductoCatalogo` y deja intactos el catálogo,
configuración, zonas, tarifas, `admin1@demo.com`, `transportista@demo.com` y cualquier
dato sin marcadores demo.

## Advertencias

- **No usar en producción.** Son datos ficticios y todas las cuentas comparten clave.
- El administrador demo se crea con `Role.ADMIN` y `PerfilAdmin`. `SecurityConfig`
  aísla `/admin/**`; aun así, es una cuenta de exposición, no de operación.
- El sembrador no llama a Wompi, no envía correos ni notificaciones y no usa Cloudinary.
- Los pedidos `PENDIENTE` no tienen pago aprobado. Los `CANCELADO` y `EN_REVISION`
  conservan su estado para que la demo muestre los flujos de revisión.
- `RECOGE_EN_TIENDA` está deshabilitado en el código actual; por eso todos los pedidos
  demo son a domicilio.
- Algunos productos pueden mostrar imagen de referencia si el archivo físico no existe.
  El seeder guarda `imagenReferencia` sin subir archivos.
- El scheduler de reconciliación de envíos puede crear envíos para compras pendientes
  después del arranque. No son visibles como disponibles hasta que sus paradas estén listas.
