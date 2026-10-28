# 🚀 Guía de Despliegue en Render - CULTIVUS-CO

Esta guía te guiará paso a paso para desplegar el proyecto **CULTIVUS-CO** de manera gratuita en la nube usando **Render** para la aplicación web y **MongoDB Atlas** para la base de datos.

---

## 📋 Requisitos Previos

1. Una cuenta en [GitHub](https://github.com/).
2. Una cuenta en [MongoDB Atlas](https://www.mongodb.com/cloud/atlas) (gratuita).
3. Una cuenta en [Render](https://render.com/) (gratuita, te puedes registrar con tu cuenta de GitHub).
4. El proyecto subido a tu repositorio de GitHub.

---

## 🛠️ Paso 1: Configurar la Base de Datos en MongoDB Atlas (Gratis)

Render no ofrece alojamiento de base de datos MongoDB nativo en su capa gratuita. Por tanto, utilizaremos MongoDB Atlas (la nube oficial de MongoDB).

1. Inicia sesión en **MongoDB Atlas**.
2. Crea un nuevo proyecto (ej. `CultivusProject`).
3. Haz clic en **Create a Deployment** y selecciona **M0 (Free)**.
4. Elige un proveedor (como AWS) y una región cercana (ej. `us-east-1` o `us-east-2`).
5. Configura las credenciales de base de datos:
   - Crea un usuario (ej. `cultivus_user`).
   - Genera una contraseña segura y **guárdala** (la necesitarás más adelante).
6. Configura el acceso por red (Network Access):
   - En la sección **IP Access List**, haz clic en **Add IP Address**.
   - Selecciona **Allow Access from Anywhere** (`0.0.0.0/0`) para que el servidor de Render pueda conectarse. *Nota: Esto es necesario porque Render utiliza direcciones IP dinámicas.*
7. Obtén tu cadena de conexión:
   - Ve a **Database** -> **Connect** -> **Drivers**.
   - Copia la cadena de conexión (URI) que se parece a esto:
     ```
     mongodb+srv://cultivus_user:<db_password>@cluster0.xxxx.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0
     ```
   - Reemplaza `<db_password>` con la contraseña del usuario que creaste.

---

## 📦 Paso 2: Subir tus Cambios a GitHub

Antes de ir a Render, asegúrate de subir los cambios que hemos realizado (la parametrización de `application.properties` y el `Dockerfile`).

Abre una terminal en la raíz del proyecto y ejecuta:

```bash
git add .
git commit -m "chore: preparar proyecto para despliegue en Render con Docker"
git push origin master
```
*(Reemplaza `master` por el nombre de tu rama si es diferente, como `main`).*

---

## ☁️ Paso 3: Crear el Web Service en Render

1. Ve a tu panel de **Render** y haz clic en **New +** -> **Web Service**.
2. Conecta tu repositorio de GitHub:
   - Si no has conectado GitHub, haz clic en **Connect GitHub** y dale permisos para acceder al repositorio `CULTIVUS-CO`.
   - Una vez conectado, busca tu repositorio y haz clic en **Connect**.
3. Configura los detalles del Web Service:
   - **Name**: `cultivus-co` (o el nombre que prefieras).
   - **Region**: Selecciona la misma región o una cercana a la de tu base de datos MongoDB Atlas (ej. `Ohio (us-east-2)` o `Oregon (us-west-2)`).
   - **Branch**: Selecciona tu rama principal (ej. `master` o `main`).
   - **Root Directory**: `demo` (¡Muy importante! Ya que tu proyecto Spring Boot está dentro de la carpeta `demo`).
   - **Runtime**: Selecciona **Docker** (Render detectará automáticamente el archivo `Dockerfile` dentro del directorio `demo`).
   - **Instance Type**: Selecciona **Free** ($0/month).

---

## 🔑 Paso 4: Configurar Variables de Entorno en Render

Render necesita conocer las credenciales para conectar la base de datos y Google OAuth2 de forma segura.

1. En la misma pantalla de creación de Render (o en la pestaña **Variables** del servicio una vez creado), haz clic en **Add Environment Variable**.
2. Agrega las siguientes variables:

| Key | Value | Descripción |
| :--- | :--- | :--- |
| `SPRING_DATA_MONGODB_URI` | `mongodb+srv://cultivus_user:TU_PASSWORD@cluster0.xxxx.mongodb.net/cultivus?retryWrites=true&w=majority` | La URI completa de MongoDB Atlas (asegúrate de incluir el nombre de la base de datos al final, ej. `/cultivus`). |
| `SPRING_PROFILES_ACTIVE` | `prod` | Perfil de producción de Spring Boot. |
| `APP_BASE_URL` | `https://cultivus-co.onrender.com` | **Obligatoria para recuperación de contraseña.** URL pública de tu app en Render (sin barra `/` al final). Se usa para generar los enlaces de restablecimiento enviados por correo. |
| `MAIL_HOST` | `smtp.gmail.com` | Servidor SMTP de correo (ej. `smtp.gmail.com`). Si se deja vacío, la app arranca pero no enviará correos. |
| `MAIL_PORT` | `587` | Puerto SMTP con STARTTLS (recomendado `587`). |
| `MAIL_USERNAME` | `tu-cuenta@gmail.com` | Correo remitente desde el cual se enviarán los enlaces de recuperación. |
| `MAIL_PASSWORD` | `abcdefghijklmnop` | **Contraseña de aplicación** de 16 letras de Google (sin espacios). ¡Nunca tu contraseña normal de Gmail! |
| `MAIL_FROM` | `tu-cuenta@gmail.com` | Dirección que aparecerá en el remitente `From`. |
| `MAIL_SMTP_AUTH` | `true` | Requiere autenticación SMTP (en Gmail es obligatoria). |
| `MAIL_STARTTLS` | `true` | Habilita cifrado STARTTLS en puerto 587. |
| `GOOGLE_CLIENT_ID` | `TU_GOOGLE_CLIENT_ID` | (Opcional) Tu ID de cliente de Google Cloud Console si usas login social. |
| `GOOGLE_CLIENT_SECRET` | `TU_GOOGLE_CLIENT_SECRET` | (Opcional) Tu Secreto de cliente de Google Cloud Console si usas login social. |

> ⚠️ **Nota de Seguridad**: Nunca subas `MAIL_PASSWORD` ni secretos al repositorio Git. Configúralos exclusivamente en el panel de variables de entorno de Render o en archivos locales ignorados por Git (`application-local.properties`).

3. Haz clic en **Create Web Service** (o **Save Changes**).

Render comenzará automáticamente a construir la imagen de Docker y a desplegar tu aplicación. Esto puede tomar entre 3 y 7 minutos la primera vez.

---

## 📧 Paso 5: Configurar Contraseña de Aplicación de Gmail

Si usas Gmail como servidor SMTP remitente, Google no permite autenticar con la contraseña común de la cuenta:

1. Ve a tu cuenta de Google: [Seguridad de Google](https://myaccount.google.com/security).
2. Asegúrate de tener activada la **Verificación en 2 pasos**.
3. En el buscador de la cuenta de Google, escribe **"Contraseñas de aplicaciones"** (o ve a Seguridad -> Verificación en 2 pasos -> Contraseñas de aplicaciones).
4. Asigna un nombre a la app (ej. `Cultivus Co`) y presiona **Crear**.
5. Se generará un código de 16 caracteres (ej. `abcd efgh ijkl mnop`).
6. Copia ese código **sin espacios** (`abcdefghijklmnop`) y pégalo en la variable de entorno `MAIL_PASSWORD` en Render.

---

## ⚠️ Paso 6: Política de Puertos SMTP en Render

> **IMPORTANTE**: Desde septiembre de 2025, Render bloquea el tráfico de salida hacia puertos SMTP (`25`, `465`, `587`) en **servicios web de capa gratuita (Free tier)** para prevenir spam.
>
> - En el archivo `demo/render.yaml` el servicio está configurado como `plan: starter` (plan de pago), lo cual **permite tráfico SMTP saliente sin bloqueos**.
> - Si decides desplegar en el plan **Free** de Render, las conexiones directas a `smtp.gmail.com:587` fallarán por timeout. En ese caso, la alternativa para capa gratuita es utilizar una API HTTP de correo (como Brevo, SendGrid o Resend) o mantener el plan Starter.

---

## 🛡️ Paso 7: Actualizar Google Cloud Console (OAuth 2.0)

Debido a que tu sitio ahora estará en internet, debes registrar el nuevo dominio en Google Cloud Console para que el botón de "Iniciar sesión con Google" funcione correctamente:

1. Ve a [Google Cloud Console](https://console.cloud.google.com/).
2. Selecciona tu proyecto y ve a **APIs & Services** -> **Credentials**.
3. En la sección **OAuth 2.0 Client IDs**, edita las credenciales de tu aplicación.
4. En **Authorized JavaScript origins**, agrega la URL principal de tu servicio en Render (ej. `https://cultivus-co.onrender.com`).
5. En **Authorized redirect URIs**, agrega la URL de redirección específica:
   ```
   https://cultivus-co.onrender.com/login/oauth2/code/google
   ```
   *(Reemplaza `cultivus-co.onrender.com` con el dominio real que Render te asigne).*
6. Guarda los cambios. *Nota: Google puede tardar unos minutos en propagar esta configuración.*

---

## 🔍 Paso 8: Verificar y Probar en Producción

1. En el panel de Render, podrás ver los logs en tiempo real haciendo clic en **Logs**. Espera a ver un log de Spring Boot similar a:
   ```
   Tomcat started on port(s): 8080 (http) with context path ''
   Started DemoApplication in X seconds (JVM running for Y)
   ```
2. Una vez que el estado del servicio cambie a **Live**, haz clic en el enlace público proporcionado por Render (en la parte superior izquierda, ej. `https://cultivus-co.onrender.com`).
3. Prueba el flujo de recuperación de contraseña:
   - Dirígete a `https://cultivus-co.onrender.com/usuario/recuperar` (o pulsa "¿Olvidaste tu contraseña?" en el Login).
   - Ingresa el correo de un usuario existente.
   - Revisa tu bandeja de entrada o spam.
   - Abre el enlace recibido (debe comenzar con tu dominio de Render `https://cultivus-co.onrender.com/usuario/restablecer?token=...`).
   - Define tu nueva contraseña cumpliendo los requisitos (mínimo 8 caracteres, mayúscula, minúscula, número y símbolo).
   - Inicia sesión con la nueva credencial.

---

## 🧪 Pruebas Locales del Flujo de Recuperación

### Opción A: Pruebas locales con Mailpit (Sin enviar correos reales)

Mailpit simula un servidor SMTP local y captura todos los correos en una interfaz web visual, ideal para pruebas de desarrollo:

1. **Iniciar Mailpit con Docker:**
   ```powershell
   docker run -d --name cultivus-mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
   ```

2. **Configurar variables en PowerShell:**
   ```powershell
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
   ```

3. **Ejecutar la aplicación:**
   ```powershell
   cd demo
   .\mvnw.cmd spring-boot:run
   ```

4. Abre `http://localhost:8080/usuario/recuperar`, solicita el restablecimiento y visualiza el correo capturado en `http://localhost:8025`.

---

### Opción B: Ejecución de la Suite de Pruebas Automatizadas

Para validar que toda la lógica de seguridad, hashes, tokens y servicios funciona correctamente:

1. **Pruebas unitarias de recuperación (sin dependencias externas):**
   ```powershell
   cd demo
   .\mvnw.cmd -Dtest=PasswordResetServiceTest,PasswordResetMailServiceTest test
   ```
   *Valida:* generación criptográfica de tokens (256 bits), almacenamiento exclusivo de SHA-256, expiración a los 15 minutos, un solo uso por token, invalidación de sesiones activas y normalización de URLs.

2. **Pruebas de integración HTTP completas:**
   ```powershell
   cd demo
   .\mvnw.cmd -Dtest=PasswordResetFlowIntegrationTest test
   ```
   *Requiere MongoDB local.* Valida: Spring Security, protección CSRF, validación de contraseñas débiles, respuestas genéricas anti-enumeración y consumo atómico concurrente (`findAndModify`).

3. **Compilación y verificación general del proyecto:**
   ```powershell
   cd demo
   .\mvnw.cmd clean test-compile
   ```

