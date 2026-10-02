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
| `GOOGLE_CLIENT_ID` | `TU_GOOGLE_CLIENT_ID` | Tu ID de cliente de Google Cloud Console. |
| `GOOGLE_CLIENT_SECRET` | `TU_GOOGLE_CLIENT_SECRET` | Tu Secreto de cliente de Google Cloud Console. |

3. Haz clic en **Create Web Service** (o **Save Changes**).

Render comenzará automáticamente a construir la imagen de Docker y a desplegar tu aplicación. Esto puede tomar entre 3 y 7 minutos la primera vez.

---

## 🛡️ Paso 5: Actualizar Google Cloud Console (OAuth 2.0)

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

## 🔍 Paso 6: Verificar y Probar

1. En el panel de Render, podrás ver los logs en tiempo real haciendo clic en **Logs**. Espera a ver un log de Spring Boot similar a:
   ```
   Tomcat started on port(s): 8080 (http) with context path ''
   Started DemoApplication in X seconds (JVM running for Y)
   ```
2. Una vez que el estado del servicio cambie a **Live**, haz clic en el enlace público proporcionado por Render (en la parte superior izquierda, ej. `https://cultivus-co.onrender.com`).
3. ¡Tu aplicación CULTIVUS-CO estará activa en internet!
