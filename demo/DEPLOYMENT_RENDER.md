# Guía de Deployment a Render

## ✅ Docker Validado
Tu imagen Docker se construyó exitosamente y corre en el puerto 8080.
- Build time: ~44 segundos
- Tamaño final: ~400MB (compresado)
- Spring Boot v3.3.6 con Java 17
- MongoDB Atlas conectado exitosamente

## 📋 Paso a Paso para Render

### 1. Preparar Render
- Ve a https://dashboard.render.com
- Crea una cuenta o inicia sesión
- Conecta tu repositorio GitHub (CULTIVUS-CO)
- Otorga permisos a Render para acceder a tus repos

### 2. Crear Variable de Entorno (MongoDB)
En Dashboard → Environment → New Environment:

```
SPRING_DATA_MONGODB_URI=mongodb+srv://dalithg29_db_user:[REDACTED]@cultivus.6drrcqp.mongodb.net/cultivus?retryWrites=true&w=majority&appName=Cultivus&compressors=zlib
```

### 3. Crear Variable de Entorno (Google OAuth2)
```
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=124461081154-4pdk1umnua2006n1p17ui1nn8r44lnrr.apps.googleusercontent.com
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=[Tu secret aquí]
```

### 4. Crear Nuevo Servicio Web en Render
- Click en "New +"
- Selecciona "Web Service"
- Conecta repo CULTIVUS-CO
- Configuración:
  * Name: `cultivus-app`
  * Root Directory: `.` (o déjalo vacío)
  * Runtime: `Docker`
  * Build Command: (dejar vacío, usa Dockerfile)
  * Start Command: (dejar vacío, usa ENTRYPOINT)
  * Plan: Starter ($7/mes)
  * Region: Ohio o US East
  
### 5. Agregar Variables de Entorno
En el formulario de creación, ve a la sección "Environment" y pega:
- SPRING_DATA_MONGODB_URI
- SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID
- SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET

### 6. Deploy
- Click en "Create Web Service"
- Render construirá automáticamente desde tu Dockerfile
- El build tarda ~2-3 minutos
- Una vez completado, tendrás una URL como: `https://cultivus-app.onrender.com`

## 🔧 Configurar Redirect URI de Google OAuth2

Una vez tengas tu URL de Render, actualiza en Google Cloud Console:
- Ve a https://console.cloud.google.com
- Proyecto → Cultivus (o tu proyecto)
- OAuth 2.0 Client IDs
- Autorized redirect URIs → Agrega:
  ```
  https://cultivus-app.onrender.com/login/oauth2/code/google
  ```

## 🚀 Comandos Útiles Locales

```bash
# Construir imagen
docker build -t cultivus:latest .

# Ejecutar localmente
docker run -p 8080:8080 --env-file .env cultivus:latest

# Ver logs
docker logs <container_id>

# Detener contenedor
docker stop <container_id>
```

## ⚠️ Consideraciones

- **Primera carga lenta**: Starter Plan tiene ~30 segundos de startup. Es normal.
- **Subidas ≤100 MB**: Recomendado. Tu imagen es ~150MB sin comprimir.
- **Renovación de créditos**: El plan Starter de Render es de pago ($7/mes), no gratuito.
- **MongoDB Atlas**: Asegúrate que tu IP esté whitelisted (en Atlas, IP Access List).

## ✅ Checklist Final

- [x] Docker build exitoso
- [x] Container corre sin errores
- [x] render.yaml creado
- [x] Cambios pusheados a GitHub
- [ ] Render conectado con GitHub
- [ ] Variables de entorno configuradas en Render
- [ ] Servicio web creado y deployado
- [ ] URL de Render funcionando
- [ ] Google OAuth2 redirect URI actualizado
