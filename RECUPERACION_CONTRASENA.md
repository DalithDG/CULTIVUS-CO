# Recuperación de contraseña — Cultivus Co

Implementación del flujo **«¿Olvidaste tu contraseña?»** sobre la autenticación existente
(Spring Security + MongoDB + Thymeleaf + BCrypt), sin duplicar la colección de usuarios.

---

## 1. Diagnóstico del proyecto real

| Elemento | Implementación encontrada |
|---|---|
| Spring Boot | `3.3.6` (`demo/pom.xml`) |
| Java | `17` |
| Usuarios | `com.example.demo.Model.Usuario`, colección `usuarios` |
| Campo correo | `email` (`@Field("email")`, índice único) |
| Campo contraseña | `contrasena` (`@Field("contrasena")`) |
| Repositorio | `UsuarioRepository.findByEmail(String)` |
| Login | `SecurityConfig`: `loginPage`/`loginProcessingUrl` = `/usuario/login`, parámetros `email` y `contrasena` |
| PasswordEncoder | `BCryptPasswordEncoder` en `Config/PasswordConfig` |
| Detalle de usuarios | `CustomUserDetailsService` + `DaoAuthenticationProvider` |
| Plantilla login | `templates/auth/login.html` + `static/login_new.css` |
| Éxito de login | `CustomAuthenticationSuccessHandler` (sesión `usuarioLogueado`, redirección por rol) |
| CSRF | Activo globalmente; solo se exime `/api/**` (Wompi) |
| Sesiones | `HttpSession` + Spring Security; no existía `SessionRegistry` |

El enlace «Recuperar contrasena?» ya existía en el login, pero apuntaba a `#`.
Ahora apunta al flujo real y usa el texto solicitado.

---

## 2. Archivos nuevos

### Código

- `demo/src/main/java/com/example/demo/Model/PasswordResetToken.java`
- `demo/src/main/java/com/example/demo/repository/PasswordResetTokenRepository.java`
- `demo/src/main/java/com/example/demo/services/PasswordResetTokenStore.java`
- `demo/src/main/java/com/example/demo/services/MongoPasswordResetTokenStore.java`
- `demo/src/main/java/com/example/demo/services/PasswordResetService.java`
- `demo/src/main/java/com/example/demo/services/PasswordResetMailService.java`
- `demo/src/main/java/com/example/demo/services/PasswordResetRateLimiter.java`
- `demo/src/main/java/com/example/demo/services/PasswordPolicy.java`
- `demo/src/main/java/com/example/demo/Config/PasswordResetProperties.java`
- `demo/src/main/java/com/example/demo/Config/PasswordResetIndexConfig.java`
- `demo/src/main/java/com/example/demo/Config/AsyncConfig.java`
- `demo/src/main/java/com/example/demo/Config/ClockConfig.java`
- `demo/src/main/java/com/example/demo/Controller/PasswordResetController.java`

### Vistas y estilos

- `demo/src/main/resources/templates/auth/recuperar-password.html`
- `demo/src/main/resources/templates/auth/restablecer-password.html`
- `demo/src/main/resources/static/recuperar-password.css`

### Pruebas

- `demo/src/test/java/com/example/demo/services/PasswordResetServiceTest.java`
- `demo/src/test/java/com/example/demo/services/PasswordResetMailServiceTest.java`
- `demo/src/test/java/com/example/demo/Controller/PasswordResetFlowIntegrationTest.java`

---

## 3. Archivos modificados

| Archivo | Cambio |
|---|---|
| `demo/pom.xml` | `spring-boot-starter-mail` y `spring-security-test` (esta última solo `test`). Sin duplicados. |
| `demo/src/main/java/com/example/demo/security/SecurityConfig.java` | Rutas públicas exactas `/usuario/recuperar` y `/usuario/restablecer`; `SessionRegistry`; `HttpSessionEventPublisher`; control de concurrencia con `maximumSessions(-1)` para registrar sesiones sin limitarlas. |
| `demo/src/main/java/com/example/demo/Controller/UsuarioController.java` | `validarContrasena` delega en `PasswordPolicy` (misma política en registro, perfil y recuperación). |
| `demo/src/main/resources/templates/auth/login.html` | Enlace real «¿Olvidaste tu contraseña?» y aviso de sesión expirada. |
| `demo/src/main/resources/application.properties` | URL base, vencimiento, límites y SMTP por variables de entorno. |
| `demo/src/main/resources/application.properties.example` | Mismas variables documentadas. |
| `demo/render.yaml` | Variables `APP_BASE_URL`, `MAIL_*` con `sync: false` para secretos. |

No se modificó la colección `usuarios`, ni el `PasswordEncoder`, ni el
`DaoAuthenticationProvider`, ni el resto de reglas de autorización.

---

## 4. Cómo funciona

### 4.1 Solicitud

1. `GET /usuario/recuperar` muestra el formulario (público, CSRF activo).
2. `POST /usuario/recuperar` valida formato de correo en servidor.
3. Se aplica rate limit por correo y por IP.
4. Se busca al usuario en `usuarios` por `email` normalizado.
5. Si existe:
   - se genera un token de **32 bytes = 256 bits** con `SecureRandom`;
   - se codifica en Base64URL (43 caracteres);
   - se calcula `SHA-256` y **solo el hash** se guarda;
   - se guarda un único documento por usuario (`_id = usuarioId`), reemplazando
     cualquier token anterior;
   - se envía el enlace en segundo plano.
6. La respuesta HTTP es siempre la misma:
   > Si el correo está registrado en Cultivus, recibirás un enlace...

No se revela si la cuenta existe, ni siquiera cuando el rate limit descarta el envío.

> **Importante:** que la página no diga “correo no registrado” es intencional.
> El backend sí consulta `usuarios` por `email`; para saber qué ocurrió revisa
> los logs (`cuenta localizada` / `no hay cuenta asociada`), nunca la respuesta
> visible al usuario. Así se evita la enumeración de cuentas.

### 4.2 Enlace

```
${APP_BASE_URL}/usuario/restablecer?token=<token>
```

- `APP_BASE_URL` se configura por entorno y se valida (solo `http`/`https`, sin
  usuario/contraseña, sin query ni fragmento).
- Vigencia: **15 minutos** (`PASSWORD_RESET_TOKEN_TTL`, por defecto `15m`).
- Cada token se usa una sola vez.
- Pedir otro enlace invalida el anterior.
- El correo no contiene contraseñas.

### 4.3 Cambio de contraseña

1. `GET /usuario/restablecer?token=...` valida el token y muestra el formulario.
2. `POST /usuario/restablecer` vuelve a validar en servidor:
   - token no vacío, formato correcto, no usado y no vencido;
   - contraseña y confirmación presentes e iguales;
   - política de contraseñas.
3. El token se consume **atómicamente** con `findAndModify`:
   ```js
   { token_hash: <hash>, usado: false, expira_en: { $gt: ahora } }
   ```
   Solo una petición concurrente puede obtener el documento.
4. Se guarda la nueva contraseña con el **mismo `BCryptPasswordEncoder`** del login.
5. Se invalidan tokens pendientes (defensivo).
6. Se expiran las sesiones activas del usuario mediante `SessionRegistry`
   (login por formulario y OAuth2).
7. Se redirige a `/usuario/login` con un mensaje de éxito.

Si el guardado de la contraseña fallara después de consumir el token, el enlace
queda inutilizado (fail-closed) y el usuario debe pedir uno nuevo. Es preferible
a permitir una reutilización.

---

## 5. MongoDB

### Colección

`password_reset_tokens`

| Campo | Tipo | Descripción |
|---|---|---|
| `_id` | String | `usuarioId`; un token vigente por usuario |
| `token_hash` | String | SHA-256 hexadecimal del token (nunca el token original) |
| `email` | String | Correo normalizado (referencia) |
| `expira_en` | Date | Expiración exacta |
| `creado_en` | Date | Creación |
| `usado` | Boolean | Consumido o no |
| `usado_en` | Date | Momento del consumo |

### Índices creados al arrancar

`PasswordResetIndexConfig` crea, de forma idempotente:

- `idx_password_reset_token_hash`: único sobre `token_hash`.
- `ttl_password_reset_expira_en`: TTL (`expireAfterSeconds: 0`) sobre `expira_en`.

**Importante:** el TTL solo limpia documentos vencidos. La expiración se
comprueba siempre en código/consulta; no se depende del monitor TTL de MongoDB.

Verificación local:

```powershell
mongosh "mongodb://localhost:27017/cultivus" --eval "db.password_reset_tokens.getIndexes()"
```

---

## 6. Dependencias Maven

Añadidas a `demo/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

No se añadió `spring-boot-starter-validation` porque el proyecto ya valida
manualmente en los controladores; así se evita una dependencia innecesaria.

---

## 7. Variables de entorno

### Aplicación

| Variable | Obligatoria en Render | Ejemplo | Uso |
|---|---|---|---|
| `APP_BASE_URL` | Sí | `https://cultivus-co.onrender.com` | Base del enlace del correo. Sin `/` final. |
| `SPRING_DATA_MONGODB_URI` | Sí | `mongodb+srv://.../cultivus` | Ya existente. |
| `SPRING_PROFILES_ACTIVE` | Sí | `prod` | Ya existente. |

### SMTP (Gmail)

| Variable | Ejemplo | Notas |
|---|---|---|
| `MAIL_HOST` | `smtp.gmail.com` | Si está vacío, la app arranca pero no envía correos. |
| `MAIL_PORT` | `587` | STARTTLS. Para SSL sería `465` + `MAIL_SSL=true`. |
| `MAIL_USERNAME` | `cultivus.co@gmail.com` | Cuenta remitente. |
| `MAIL_PASSWORD` | `abcdefghijklmnop` | **Contraseña de aplicación** de Gmail, no la del correo. Sin espacios. |
| `MAIL_FROM` | `cultivus.co@gmail.com` | Opcional; si falta se usa `MAIL_USERNAME`. |
| `MAIL_SMTP_AUTH` | `true` | Gmail requiere autenticación. |
| `MAIL_STARTTLS` | `true` | Obligatorio con puerto 587. |
| `MAIL_STARTTLS_REQUIRED` | `true` | Opcional; recomendado. |
| `MAIL_SSL` | `false` | `true` solo si se usa puerto 465. |

### Límites y vigencia (opcionales)

| Variable | Por defecto | Uso |
|---|---|---|
| `PASSWORD_RESET_TOKEN_TTL` | `15m` | Vigencia del enlace. |
| `PASSWORD_RESET_WINDOW` | `15m` | Ventana de los límites. |
| `PASSWORD_RESET_MAX_EMAIL` | `3` | Solicitudes por correo en la ventana. |
| `PASSWORD_RESET_MAX_IP` | `10` | Solicitudes por IP en la ventana. |
| `PASSWORD_RESET_MAX_ATTEMPTS` | `20` | Intentos de cambio por IP en la ventana. |

### Gmail: contraseña de aplicación

1. Activa la verificación en dos pasos en la cuenta de Google.
2. Google Account → Seguridad → Contraseñas de aplicaciones.
3. Crea una para «Correo» y copia los 16 caracteres **sin espacios**.
4. Úsala solo como `MAIL_PASSWORD` en Render o en tu entorno local.
5. Si se filtra, revócala y genera otra.

---

## 8. Configuración añadida a `application.properties`

```properties
app.base-url=${APP_BASE_URL:http://localhost:8080}

cultivus.password-reset.token-ttl=${PASSWORD_RESET_TOKEN_TTL:15m}
cultivus.password-reset.ventana=${PASSWORD_RESET_WINDOW:15m}
cultivus.password-reset.max-solicitudes-por-email=${PASSWORD_RESET_MAX_EMAIL:3}
cultivus.password-reset.max-solicitudes-por-ip=${PASSWORD_RESET_MAX_IP:10}
cultivus.password-reset.max-intentos-por-ip=${PASSWORD_RESET_MAX_ATTEMPTS:20}

spring.mail.host=${MAIL_HOST:}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.default-encoding=UTF-8
spring.mail.properties.mail.smtp.auth=${MAIL_SMTP_AUTH:true}
spring.mail.properties.mail.smtp.starttls.enable=${MAIL_STARTTLS:true}
spring.mail.properties.mail.smtp.starttls.required=${MAIL_STARTTLS_REQUIRED:true}
spring.mail.properties.mail.smtp.ssl.enable=${MAIL_SSL:false}
spring.mail.properties.mail.smtp.connectiontimeout=${MAIL_CONNECTION_TIMEOUT_MS:5000}
spring.mail.properties.mail.smtp.timeout=${MAIL_TIMEOUT_MS:5000}
spring.mail.properties.mail.smtp.writetimeout=${MAIL_WRITE_TIMEOUT_MS:5000}
cultivus.mail.from=${MAIL_FROM:}
```

No hay credenciales SMTP en el código ni en `render.yaml`.

---

## 9. Prueba local

### 9.1 Configuración rápida con `.env` (recomendada)

El proyecto carga un archivo `.env` local (ignorado por Git). Créalo en
`demo/.env` o en la raíz y completa:

```properties
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/cultivus
APP_BASE_URL=http://localhost:8080
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=tu-cuenta@gmail.com
MAIL_PASSWORD=contraseña-de-aplicación-sin-espacios
MAIL_FROM=tu-cuenta@gmail.com
MAIL_SMTP_AUTH=true
MAIL_STARTTLS=true
```

Spring Boot lo carga automáticamente gracias a
`spring.config.import=optional:file:./.env[.properties],optional:file:../.env[.properties],optional:file:./demo/.env[.properties]`.
Después:

```powershell
cd demo
.\mvnw.cmd spring-boot:run
```

Al arrancar debe aparecer:

```text
SMTP de recuperación configurado: host=smtp.gmail.com, usuarioConfigurado=true, passwordConfigurado=true
```

Si aparece `SMTP de recuperación NO configurado`, el `.env` no se está leyendo
o las claves están mal escritas. Si tu IDE ejecuta desde la raíz, coloca el
`.env` en la raíz del proyecto; el import busca `./`, `../` y `./demo/`.

> Si tus usuarios están en MongoDB Atlas, cambia `SPRING_DATA_MONGODB_URI` por
> la cadena de Atlas. Si los usuarios están en Atlas y la app local apunta a
> `localhost`, el correo no se encontrará y no se enviará nada.

### 9.2 Con MongoDB local y Mailpit (sin enviar correos reales)

Requisitos: Java 17+, MongoDB escuchando en `localhost:27017` y Docker (o Mailpit instalado).

```powershell
# 1. Bandeja de entrada de pruebas
docker run -d --name cultivus-mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit

# 2. Variables de entorno (sesión de PowerShell)
$env:SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/cultivus"
$env:APP_BASE_URL="http://localhost:8080"
$env:MAIL_HOST="localhost"
$env:MAIL_PORT="1025"
$env:MAIL_USERNAME=""
$env:MAIL_PASSWORD=""
$env:MAIL_SMTP_AUTH="false"
$env:MAIL_STARTTLS="false"
$env:MAIL_STARTTLS_REQUIRED="false"
$env:MAIL_FROM="no-reply@cultivus.local"

# 3. Arrancar
cd demo
.\mvnw.cmd spring-boot:run
```

1. Abre `http://localhost:8080/usuario/registro` y crea una cuenta de prueba.
2. Cierra sesión y entra a `http://localhost:8080/usuario/recuperar`.
3. Introduce el correo. Verás el mensaje genérico.
4. Abre `http://localhost:8025` (Mailpit), abre el correo y pulsa el enlace.
5. Crea la nueva contraseña y vuelve a iniciar sesión.
6. Repite el enlace: debe mostrar «Enlace no válido».
7. Para probar la expiración sin esperar 15 minutos:
   ```powershell
   $env:PASSWORD_RESET_TOKEN_TTL="1m"
   ```
   reinicia la app y espera algo más de un minuto.

### 9.3 Con Gmail real (alternativa por variables de entorno)

```powershell
$env:SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/cultivus"
$env:APP_BASE_URL="http://localhost:8080"
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="tu-cuenta@gmail.com"
$env:MAIL_PASSWORD="contraseña-de-aplicación-sin-espacios"
$env:MAIL_FROM="tu-cuenta@gmail.com"
$env:MAIL_SMTP_AUTH="true"
$env:MAIL_STARTTLS="true"

cd demo
.\mvnw.cmd spring-boot:run
```

Si Gmail rechaza el acceso, revisa que sea una contraseña de aplicación y que
la verificación en dos pasos esté activa. Los errores del proveedor se registran
sin mostrar el token ni la contraseña.

### 9.4 Verificar la base de datos

```powershell
mongosh "mongodb://localhost:27017/cultivus" --eval "db.password_reset_tokens.findOne()"
mongosh "mongodb://localhost:27017/cultivus" --eval "db.password_reset_tokens.getIndexes()"
```

El documento debe contener `token_hash` (64 caracteres hexadecimales) y **no**
el token original.

### 9.5 Limpiar datos de prueba

```powershell
mongosh "mongodb://localhost:27017/cultivus" --eval "db.password_reset_tokens.deleteMany({})"
```

---

## 10. Pruebas automatizadas

### Unitarias

```powershell
cd demo
.\mvnw.cmd -Dtest=PasswordResetServiceTest,PasswordResetMailServiceTest test
```

Cubren:

- token de 256 bits y solo hash persistido;
- expiración a los 15 minutos;
- un solo uso;
- invalidación del token anterior al pedir otro;
- contraseña débil sin consumir el token;
- límites de frecuencia;
- invalidación de sesiones de formulario y OAuth2;
- construcción del enlace y rechazo de `APP_BASE_URL` peligrosa.

### Integración HTTP

```powershell
cd demo
.\mvnw.cmd -Dtest=PasswordResetFlowIntegrationTest test
```

Requiere MongoDB local. Usa una base separada
`cultivus_test_recuperacion` y la limpia antes/después de cada prueba.
Se puede apuntar a otro servidor:

```powershell
$env:TEST_MONGODB_URI="mongodb://localhost:27017"
$env:TEST_MONGODB_DATABASE="cultivus_test_recuperacion"
```

Cubre el flujo real: Spring Security + CSRF + controlador + servicio + MongoDB,
incluido el login con la nueva contraseña, el rechazo de la anterior, el enlace
vencido y dos peticiones concurrentes con el mismo token (solo una gana).

> Estado de esta entrega: el código principal compila. La ejecución completa de
> la suite quedó pendiente por indicación expresa del usuario en esta sesión.
> Ejecuta los comandos anteriores antes de desplegar.

---

## 11. Render

### 11.1 Variables en el panel de Render

En **Dashboard → tu servicio → Environment**, añade:

```
APP_BASE_URL = https://cultivus-co.onrender.com
MAIL_HOST = smtp.gmail.com
MAIL_PORT = 587
MAIL_USERNAME = tu-cuenta@gmail.com
MAIL_PASSWORD = contraseña-de-aplicación
MAIL_FROM = tu-cuenta@gmail.com
MAIL_SMTP_AUTH = true
MAIL_STARTTLS = true
```

Mantén las existentes:

```
SPRING_DATA_MONGODB_URI = mongodb+srv://...
SPRING_PROFILES_ACTIVE = prod
```

`render.yaml` ya declara las nuevas variables. Las que usan `sync: false` se
piden al crear el blueprint y **no se guardan en Git**.

### 11.2 Despliegue

1. Haz commit y push a la rama conectada a Render.
2. Render reconstruye la imagen Docker (`demo/Dockerfile`).
3. Revisa **Logs**: no debe haber errores de SMTP ni de índices.
4. Abre `https://cultivus-co.onrender.com/usuario/recuperar`.
5. Solicita un enlace con una cuenta real y compruébalo.
6. Verifica que el enlace apunta al dominio de Render, no a `localhost`.

### 11.3 SMTP en Render

Render bloqueó el tráfico saliente a los puertos SMTP `25`, `465` y `587` en
los **servicios web gratuitos** desde septiembre de 2025. El `render.yaml` de
este proyecto usa `plan: starter` (de pago), por lo que el SMTP está permitido.
Si el servicio se baja al plan gratuito, Gmail por SMTP dejará de funcionar:
usa un proveedor por API (Resend, SendGrid, Brevo, etc.) o sube de plan.

Referencia: <https://render.com/changelog/free-web-services-will-no-longer-allow-outbound-traffic-to-smtp-ports>

### 11.4 Si el correo no llega

El mensaje de la página **siempre es genérico** por diseño: no se debe revelar si
el correo existe. Internamente el servicio sí consulta `usuarios` por `email`.
Los logs nuevos permiten distinguir los dos casos sin exponer datos:

| Log | Significado |
|---|---|
| `SMTP de recuperación NO configurado (MAIL_HOST vacío)` | No hay envío posible: faltan variables de entorno. |
| `SMTP de recuperación configurado: host=..., usuarioConfigurado=true, passwordConfigurado=true` | La configuración SMTP fue leída. |
| `Solicitud de recuperación: no hay cuenta asociada en la base de datos` | Ese correo no existe **en la base que está usando la app**. |
| `Solicitud de recuperación: cuenta localizada; se genera el token` | El correo sí existe; se creó el token. |
| `Solicitud de recuperación: token guardado y correo encolado` | Se intentó enviar el correo en segundo plano. |
| `Correo de recuperación enviado correctamente` | El proveedor SMTP aceptó el mensaje. |
| `No se pudo enviar el correo de recuperación (...)` | Falló SMTP; revisa la clase/causa indicada. |
| `APP_BASE_URL apunta a localhost` | El enlace no servirá fuera de tu máquina o en Render. |

Checklist:

1. **¿Dónde estás probando?** Si es local, las variables deben estar en el
   entorno del proceso (terminal o Run Configuration del IDE), no solo escritas
   en un archivo.
2. **¿La app usa la misma base donde está el usuario?** Si el login funciona con
   una cuenta de Atlas/Render pero la app local apunta a
   `mongodb://localhost:27017/cultivus`, la cuenta no existirá ahí. Revisa
   `SPRING_DATA_MONGODB_URI` y confirma el log de “cuenta localizada”.
3. **¿Hay token en Mongo?**
   ```powershell
   mongosh "mongodb://localhost:27017/cultivus" --eval "db.password_reset_tokens.countDocuments({})"
   ```
   Si hay token, la cuenta se encontró y el problema está en el correo.
4. **¿SMTP responde?**
   ```powershell
   Test-NetConnection smtp.gmail.com -Port 587
   ```
5. **¿La contraseña es de aplicación?** Gmail exige verificación en dos pasos y
   una contraseña de aplicación de 16 caracteres, sin espacios. La contraseña
   normal de la cuenta no funciona.
6. **¿El plan de Render permite SMTP?** Los servicios gratuitos bloquean los
   puertos 25, 465 y 587. El blueprint usa `plan: starter`; si estás en free,
   cambia de plan o usa un proveedor por API.
7. **¿Es la app nueva?** Reinicia el proceso después de cambiar variables de
   entorno. Spring Boot no recarga variables de entorno en caliente.
8. **Revisa spam** y la carpeta de promociones.

Si el log dice `Correo de recuperación enviado correctamente` pero no llega,
el problema está después de la app: filtro de Gmail, dirección `MAIL_FROM`
distinta a la cuenta autenticada, o correo de destino incorrecto. Prueba primero
con Mailpit para descartar el código.

---

## 12. GitHub y secretos

### Antes de subir

```powershell
git status
git diff
git grep -n -i "password\|secret\|token" -- "*.properties" "*.yml" "*.yaml"
```

- No subas `.env` ni `application-local.properties` (ya están en `.gitignore`).
- No pongas `MAIL_PASSWORD` en `application.properties`, `render.yaml` ni en el README.
- Usa variables de entorno de Render.

### Hallazgo importante (fuera del alcance de esta funcionalidad)

`demo/src/main/resources/application.properties` contiene credenciales de
Facebook OAuth2 escritas en el archivo (y claves de prueba de Wompi). Eso no es
seguro en un repositorio público. Se recomienda:

1. **Rotar** el secreto de Facebook y las claves de Wompi que hayan estado en Git.
2. Moverlas a variables de entorno (`FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`,
   `WOMPI_*`).
3. Confirmar que `.gitignore` cubre los archivos locales.
4. Si el repositorio es público, asumir que los secretos ya están comprometidos.

No se cambiaron esas credenciales en esta entrega para no romper el login social
sin que las variables estén configuradas en Render.

---

## 13. Decisiones de seguridad y límites

- **Token**: 256 bits, `SecureRandom`, Base64URL; solo SHA-256 en MongoDB.
- **Un solo token por usuario**: el documento se reemplaza al pedir otro.
- **Consumo atómico**: `findAndModify`; impide doble uso concurrente.
- **Expiración**: comprobada en cada operación; TTL solo limpia.
- **CSRF**: se mantiene activo; los formularios Thymeleaf incluyen el token
  automáticamente y las rutas nuevas son públicas solo para lectura/POST con CSRF.
- **Enumeración**: mensaje genérico; el formato inválido sí se señala (no revela
  si la cuenta existe).
- **Rate limit**: en memoria, adecuado para una instancia. Si se escala a varias
  instancias, moverlo a Redis para que sea global.
- **Sesiones**: `SessionRegistry` en memoria por instancia. En un despliegue
  multi-instancia habría que compartir el registro (Spring Session/Redis).
- **Correo**: envío asíncrono; errores del proveedor se registran sin token,
  correo ni credenciales.
- **Enlace en logs solo para desarrollo**: con `PASSWORD_RESET_LOG_LINK=true`
  el enlace se imprime en consola cuando no hay SMTP. Por defecto está en
  `false`; nunca debe activarse en Render.
- **Cabeceras**: `Cache-Control: no-store`, `Referrer-Policy: no-referrer`,
  `X-Robots-Tag: noindex` en las páginas del flujo.
- **Redirecciones**: siempre internas y fijas; no se acepta un parámetro de
  retorno, evitando open redirects.
- **Inyección**: consultas derivadas/parametrizadas y hash; no se concatenan
  datos del usuario en queries.
- **Errores globales**: el proyecto ya tenía
  `server.error.include-message=always`. Para producción conviene cambiarlo a
  `never` y revisar los logs del servidor; el flujo de recuperación, por su
  parte, ya devuelve mensajes genéricos.

---

## 14. Rutas nuevas

| Método | Ruta | Acceso |
|---|---|---|
| GET | `/usuario/recuperar` | Público |
| POST | `/usuario/recuperar` | Público + CSRF |
| GET | `/usuario/restablecer?token=...` | Público |
| POST | `/usuario/restablecer` | Público + CSRF |

El resto de rutas y roles no cambia.
