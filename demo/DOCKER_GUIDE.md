# 🐳 Guía Completa de Docker para Cultivus

## ❌ Problema de CSS - Causa y Solución

### ¿Por qué no cargaba el CSS?
Spring Boot necesita configuración explícita para servir archivos estáticos (CSS, JS, imágenes). El problema ocurría porque:

1. **Falta de `application.properties`**: Solo existía `application.properties.example`
2. **Sin caché de recursos**: Los assets no estaban configurados para servirse correctamente
3. **Sin perfil de configuración**: No había separación entre desarrollo y producción

### ✅ Solución Implementada

He reorganizado la estructura de configuración en 3 archivos:

```
src/main/resources/
├── application.properties          # Base (perfil: dev)
├── application-dev.properties      # Desarrollo (sin OAuth2, caché deshabilitado)
└── application-prod.properties     # Producción (OAuth2 requerido, caché habilitado)
```

**Configuración clave para CSS:**
```properties
spring.resources.static-locations=classpath:/static/
spring.thymeleaf.mode=HTML
spring.web.resources.cache.period=0          # Dev: sin caché
spring.web.resources.cache.period=31536000   # Prod: 1 año de caché
```

---

## 📦 Dos Imágenes Docker Optimizadas

### 1. **Dockerfile** (Producción)
```bash
docker build -t cultivusco:latest .
```
- **Perfil**: `prod`
- **Requiere**: Variables de entorno (MongoDB Atlas, Google OAuth2)
- **Caché CSS**: 1 año (optimizado)
- **Logging**: Solo INFO/WARN
- **Uso**: Render, AWS, production

### 2. **Dockerfile.dev** (Desarrollo/Testing)
```bash
docker build -f Dockerfile.dev -t cultivusco:dev .
```
- **Perfil**: `dev`
- **No requiere**: OAuth2 (usa defaults)
- **Caché CSS**: Deshabilitado (cambios en tiempo real)
- **Logging**: DEBUG para troubleshooting
- **Uso**: Testing local, desarrollo

---

## 🚀 Ejecutar en Docker

### Opción 1: Producción (con variables requeridas)
```bash
docker run -d -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATA_MONGODB_URI="mongodb+srv://user:[REDACTED]@cluster.mongodb.net/cultivus" \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID="your-client-id" \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET="your-secret" \
  cultivusco:latest
```

### Opción 2: Desarrollo (sin dependencias externas)
```bash
docker run -d -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  cultivusco:dev
```

### Opción 3: docker-compose (recomendado para desarrollo)
```bash
docker-compose up
```

---

## 🐳 docker-compose.yml (Nuevo)

```yaml
version: '3.8'

services:
  cultivus-app:
    image: cultivusco:dev
    build:
      context: .
      dockerfile: Dockerfile.dev
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      PORT: 8080
      SPRING_DATA_MONGODB_URI: mongodb://localhost:27017/cultivus
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID: dev-client-id
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET: dev-secret
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 40s

  # MongoDB local para desarrollo (comentar si usas Atlas)
  # mongodb:
  #   image: mongo:7.0-alpine
  #   ports:
  #     - "27017:27017"
  #   environment:
  #     MONGO_INITDB_ROOT_USERNAME: root
  #     MONGO_INITDB_ROOT_PASSWORD: example
  #   volumes:
  #     - mongo_data:/data/db
  # volumes:
  #   mongo_data:
```

---

## 📋 Archivos Creados/Modificados

| Archivo | Propósito |
|---------|-----------|
| `Dockerfile` | Imagen producción (Spring prof: prod) |
| `Dockerfile.dev` | Imagen desarrollo (Spring prof: dev) |
| `application.properties` | Configuración base (por defecto dev) |
| `application-dev.properties` | Configuración desarrollo (OAuth2 deshabilitado) |
| `application-prod.properties` | Configuración producción (OAuth2 requerido) |
| `.dockerignore` | Optimización de build (excluye archivos innecesarios) |
| `docker-compose.yml` | Orquestación local |

---

## 🔍 Verificar que CSS carga correctamente

### 1. **En local (sin Docker)**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
# Accede a http://localhost:8080
# Los CSS deben estar en DevTools cache (sin caché)
```

### 2. **En Docker Dev**
```bash
docker run -p 8080:8080 cultivusco:dev
# Accede a http://localhost:8080
# Los CSS se sirven sin caché (spring.web.resources.cache.period=0)
```

### 3. **Ver archivos estáticos empacados en JAR**
```bash
jar tf target/demo-0.0.1-SNAPSHOT.jar | grep -E "\.css|\.js"
```
Deberías ver:
```
BOOT-INF/classes/static/style.css
BOOT-INF/classes/static/header.css
BOOT-INF/classes/static/footer.css
BOOT-INF/classes/static/login.css
... (20+ más)
```

---

## 📊 Especificaciones de las Imágenes

### Tamaño y Performance

| Métrica | Producción | Desarrollo |
|---------|-----------|-----------|
| Base Image | eclipse-temurin:17-jre | eclipse-temurin:17-jre |
| Build Time | ~50 seg | ~50 seg |
| Image Size | ~420 MB | ~420 MB |
| Startup Time | ~15 seg | ~15 seg |
| Java Heap | Auto (75% RAM) | Auto (75% RAM) |
| CSS Caché | 1 año (31536000s) | 0 seg (sin caché) |

---

## ⚙️ Variables de Entorno

### Development (cultivusco:dev)
```bash
SPRING_PROFILES_ACTIVE=dev
PORT=8080
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/cultivus
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=dev-client-id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=dev-secret
```

### Production (cultivusco:latest)
```bash
SPRING_PROFILES_ACTIVE=prod
PORT=8080
SPRING_DATA_MONGODB_URI=mongodb+srv://user:[REDACTED]@cluster.6drrcqp.mongodb.net/cultivus
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=<real-id>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=<real-secret>
```

---

## 🎯 Próximos Pasos

### 1. **Build & Test Local**
```bash
docker build -t cultivusco:latest .
docker build -f Dockerfile.dev -t cultivusco:dev .
docker-compose up
```

### 2. **Subir a Render**
```bash
docker tag cultivusco:latest your-docker-hub/cultivusco:latest
docker push your-docker-hub/cultivusco:latest
```

En Render:
- Image: `your-docker-hub/cultivusco:latest`
- Variables: MongoDB URI + Google OAuth2 secrets

### 3. **Verificar CSS en Navegador**
```
DevTools → Network → XHR/CSS
Deberías ver: 200 OK para todos los .css
```

---

## 🐛 Troubleshooting

### CSS no carga
✅ **Solución**: Todos los CSS están en `src/main/resources/static/` y se empacan automáticamente en el JAR.

### OAuth2 error en dev
✅ **Solución**: Usa `cultivusco:dev` que tiene defaults en application-dev.properties.

### MongoDB no conecta
✅ **Solución**: 
- Dev: Usa MongoDB local en docker-compose
- Prod: Asegura SPRING_DATA_MONGODB_URI válida en Render

### App arranca lentamente
✅ **Normal**: Java + Spring Boot startup ~15 seg en contenedor. Health check espera 40 seg.

---

## 📌 Comandos Útiles

```bash
# Build imagenes
docker build -t cultivusco:latest .
docker build -f Dockerfile.dev -t cultivusco:dev .

# Ver capas
docker history cultivusco:latest

# Ejecutar con logs
docker run -it cultivusco:dev

# Limpiar
docker system prune -a

# Ver tamaño
docker images cultivusco

# Inspeccionar JAR
docker run --rm cultivusco:latest jar tf /app/app.jar | grep "static/"
```
