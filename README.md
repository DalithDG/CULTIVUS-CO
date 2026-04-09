# 🌱 CULTIVUS-CO

<div align="center">

**Plataforma E-Commerce para Productos Agrícolas Locales**

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 📋 Tabla de Contenidos

- [Descripción](#-descripción)
- [Características](#-características)
- [Tecnologías](#️-tecnologías)
- [Arquitectura](#-arquitectura)
- [Instalación](#-instalación)
- [Configuración](#️-configuración)
- [Uso](#-uso)
- [API Endpoints](#-api-endpoints)
- [Seguridad](#-seguridad)
- [Problemas Conocidos](#-problemas-conocidos)
- [Roadmap](#-roadmap)
- [Contribuir](#-contribuir)

---

## 📖 Descripción

**CULTIVUS-CO** es una plataforma web completa de e-commerce diseñada para conectar **productores agrícolas locales** con **consumidores**, facilitando la venta directa de productos frescos y artesanales. La aplicación implementa un sistema multi-rol que permite a usuarios actuar como compradores, vendedores, o administradores.

### 🎯 Objetivo

Crear un marketplace digital que apoye la economía local, reduzca intermediarios, y proporcione acceso directo a productos agrícolas de calidad.

---

## ✨ Características

### 👤 Gestión de Usuarios

- ✅ Registro de usuarios con ubicación (departamento/ciudad)
- ✅ Sistema de autenticación (login/logout)
- ✅ Gestión de perfiles
- ✅ Sistema de roles: **Comprador**, **Vendedor**, **Administrador**

### 📦 Gestión de Productos

- ✅ CRUD completo de productos
- ✅ Categorización (Frutas, Verduras, Lácteos, Café & Cacao)
- ✅ Unidades de medida configurables
- ✅ Gestión de stock y precios
- ✅ Imágenes de productos
- ✅ Sistema de búsqueda y filtrado

### 🛒 Carrito y Compras

- ✅ Carrito de compras funcional
- ✅ Modificación de cantidades
- ✅ Proceso de checkout
- ✅ Generación de pedidos
- ✅ Sistema de pagos

### 🏪 Panel de Vendedor

- ✅ Gestión de productos propios
- ✅ Visualización de ventas
- ✅ Detalles de pedidos recibidos
- ✅ Estadísticas de ventas

### 👨‍💼 Panel de Administración

- ✅ Gestión de usuarios
- ✅ Gestión de productos
- ✅ Moderación de reseñas
- ✅ Dashboard administrativo

### ⭐ Características Adicionales

- ✅ Sistema de reseñas de productos
- ✅ Búsqueda avanzada
- ✅ Selección dinámica de ubicación
- ✅ Interfaz responsiva

---

## 🛠️ Tecnologías

### Backend

```
├── Java 17
├── Spring Boot 3.5.6
│   ├── Spring Web
│   ├── Spring Data JPA
│   └── Thymeleaf
├── MySQL 8.0
└── Maven
```

### Frontend

```
├── HTML5
├── CSS3 (17 archivos personalizados)
├── JavaScript (Vanilla)
└── Thymeleaf Templates (34 plantillas)
```

### Dependencias Principales

```xml
<!-- Spring Boot Starters -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>

<!-- Database -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## 🏗️ Arquitectura

### Estructura del Proyecto

```
CULTIVIS-CO/
└── demo/
    ├── src/main/java/com/example/demo/
    │   ├── DemoApplication.java          # Punto de entrada
    │   ├── Config/
    │   │   └── DataLoader.java           # Inicialización de datos
    │   ├── Controller/                   # 11 controladores
    │   │   ├── UsuarioController.java
    │   │   ├── ProductoController.java
    │   │   ├── CarritoController.java
    │   │   ├── CatalogoController.java
    │   │   ├── PagoController.java
    │   │   ├── VendedorController.java
    │   │   ├── AdminController.java
    │   │   ├── BusquedaController.java
    │   │   ├── ResenaController.java
    │   │   └── RoutesController.java
    │   ├── Model/                        # 12 entidades JPA
    │   │   ├── Usuario.java
    │   │   ├── Producto.java
    │   │   ├── Carrito.java
    │   │   ├── Pedido.java
    │   │   ├── Pago.java
    │   │   ├── Categoria.java
    │   │   ├── Ciudad.java
    │   │   ├── Departamento.java
    │   │   └── ... (otros)
    │   ├── repository/                   # 12 repositorios
    │   └── services/                     # Capa de servicios
    │       ├── UsuarioService.java
    │       ├── ProductoService.java
    │       └── ... (otros)
    └── src/main/resources/
        ├── application.properties
        ├── templates/                    # 34 plantillas HTML
        │   ├── registro.html
        │   ├── login.html
        │   ├── carrito.html
        │   ├── admin-dashboard.html
        │   └── fragments/
        │       ├── header.html
        │       ├── header-admin.html
        │       ├── header-comprador.html
        │       ├── header-vendedor.html
        │       └── footer.html
        └── static/
            ├── *.css                     # 17 archivos CSS
            └── images/
```

### Modelo de Datos (Simplificado)

```
Usuario (1) ──── (N) Producto
   │                   │
   │                   │
   ├── (1:1) PerfilVendedor
   ├── (1:N) Carrito
   ├── (1:N) Pedido
   └── (N:1) Roles

Producto (N) ──── (1) Categoria
         (N) ──── (1) UnidadMedida
         (1) ──── (N) Resena
```

---

## 🚀 Instalación

### Prerrequisitos

- **JDK 17 o superior** ([Descargar](https://www.oracle.com/java/technologies/downloads/))
- **MySQL 8.0+** ([Descargar](https://dev.mysql.com/downloads/))
- **Maven** (incluido con wrapper `./mvnw`)

### Pasos de Instalación

1. **Clonar el repositorio**

```bash
git clone https://github.com/tu-usuario/CULTIVIS-CO.git
cd CULTIVIS-CO/demo
```

2. **Configurar base de datos**

```sql
-- Crear base de datos
CREATE DATABASE cultivus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Crear usuario (opcional)
CREATE USER 'cultivus_user'@'localhost' IDENTIFIED BY 'tu_password';
GRANT ALL PRIVILEGES ON cultivus.* TO 'cultivus_user'@'localhost';
FLUSH PRIVILEGES;

-- Fix para columna ciudad (si es necesario)
USE cultivus;
ALTER TABLE ciudad MODIFY nombre VARCHAR(100);
```

3. **Configurar variables de entorno**

**Linux/Mac:**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

**Windows:**

```cmd
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
```

4. **Compilar el proyecto**

```bash
./mvnw clean package -DskipTests
```

5. **Ejecutar la aplicación**

```bash
./mvnw spring-boot:run
```

6. **Acceder a la aplicación**

```
http://localhost:8080
```

---

## ⚙️ Configuración

### application.properties

Editar `src/main/resources/application.properties`:

```properties
# Servidor
server.port=8080

# Base de datos
spring.datasource.url=jdbc:mysql://localhost:3306/cultivus
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# Thymeleaf
spring.thymeleaf.cache=false
```

### Configuración de Producción

Para producción, cambiar:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.thymeleaf.cache=true
```

---

## 💻 Uso

### Rutas Principales

#### Públicas

```
GET  /                    → Página de inicio pública
GET  /registro            → Formulario de registro
GET  /login               → Formulario de login
GET  /productos-listado   → Catálogo de productos
GET  /producto-detalle    → Detalle de producto
```

#### Comprador

```
GET  /inicio              → Dashboard del comprador
GET  /carrito             → Carrito de compras
GET  /pago                → Proceso de pago
GET  /perfil              → Perfil de usuario
POST /carrito/agregar     → Agregar producto al carrito
```

#### Vendedor

```
GET  /vendedor/inicio                  → Dashboard del vendedor
GET  /vendedor/productos               → Mis productos
GET  /vendedor/productos/nuevo         → Agregar producto
GET  /vendedor/productos/editar/{id}   → Editar producto
POST /vendedor/productos/guardar       → Guardar producto
DELETE /vendedor/productos/{id}        → Eliminar producto
GET  /vendedor/ventas                  → Historial de ventas
```

#### Administrador

```
GET  /admin/dashboard     → Panel de administración
GET  /admin/usuarios      → Gestión de usuarios
GET  /admin/productos     → Gestión de productos
GET  /admin/resenas       → Moderación de reseñas
```

### Aliases (Redirects)

```
/newregister  → /registro
/loginnew     → /login
/categorias   → /productos-listado
```

---

## 🔌 API Endpoints

### Ubicación

```http
GET /api/ubicacion/departamentos
Response: [{"id": 1, "nombre": "Antioquia"}, ...]

GET /api/ubicacion/ciudades/{idDepartamento}
Response: [{"id": 1, "nombre": "Medellín", "departamento": {...}}, ...]
```

### Productos (REST API)

```http
GET    /api/productos           # Listar todos
GET    /api/productos/{id}      # Obtener por ID
POST   /api/productos           # Crear nuevo
PUT    /api/productos/{id}      # Actualizar
DELETE /api/productos/{id}      # Eliminar
```

### Ejemplo con cURL

```bash
# Obtener departamentos
curl http://localhost:8080/api/ubicacion/departamentos

# Obtener ciudades de un departamento
curl http://localhost:8080/api/ubicacion/ciudades/1
```

---

## 🔒 Seguridad

### ⚠️ ADVERTENCIAS IMPORTANTES

> **🔴 CRÍTICO**: Este proyecto actualmente tiene las siguientes vulnerabilidades de seguridad:

1. **Contraseñas sin encriptar**: Las contraseñas se almacenan en texto plano en la base de datos
2. **Sin Spring Security**: No hay protección CSRF ni autenticación robusta
3. **Sesiones manuales**: Sistema de sesiones implementado manualmente con `HttpSession`

### 🛡️ Mejoras de Seguridad Recomendadas (URGENTE)

#### 1. Implementar BCrypt para contraseñas

```xml
<!-- Agregar a pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

```java
// En UsuarioService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

// Al registrar
String hashedPassword = encoder.encode(plainPassword);
usuario.setContrasena(hashedPassword);

// Al validar login
if (encoder.matches(plainPassword, usuario.getContrasena())) {
    // Login exitoso
}
```

#### 2. Migrar a Spring Security

Ver documentación oficial: [Spring Security Reference](https://docs.spring.io/spring-security/reference/)

---

## 🐛 Problemas Conocidos

### 🔴 Críticos

1. **Contraseñas en texto plano**

   - **Impacto**: Alto riesgo de seguridad
   - **Solución**: Implementar BCrypt (ver sección Seguridad)

2. **Sin Spring Security**
   - **Impacto**: Vulnerabilidades CSRF, XSS
   - **Solución**: Migrar a Spring Security

### 🟡 Importantes

3. **Columna `ciudad.nombre` muy corta**

   - **Error**: `Data truncation: Data too long for column 'nombre'`
   - **Solución**:

   ```sql
   ALTER TABLE ciudad MODIFY nombre VARCHAR(100);
   ```

4. **Sin validación robusta**

   - **Impacto**: Datos inconsistentes
   - **Solución**: Implementar Bean Validation

   ```java
   @NotBlank(message = "El nombre es obligatorio")
   @Size(min = 3, max = 50)
   private String nombre;
   ```

5. **Sin tests**
   - **Impacto**: Dificulta mantenimiento
   - **Solución**: Agregar JUnit y Mockito

### 🟢 Menores

6. **Dependencia innecesaria**: `scala-library` en pom.xml
7. **Sin documentación API**: Falta Swagger/OpenAPI

---

## 🗺️ Roadmap

### Versión 1.1 (Próxima Release) - Seguridad

- [ ] Implementar BCrypt para contraseñas
- [ ] Migrar a Spring Security
- [ ] Agregar protección CSRF
- [ ] Implementar rate limiting
- [ ] Validación robusta de inputs

### Versión 1.2 - Calidad

- [ ] Tests unitarios (>50% coverage)
- [ ] Tests de integración
- [ ] Documentación Swagger/OpenAPI
- [ ] Manejo centralizado de errores (@ControllerAdvice)
- [ ] Logging estructurado

### Versión 1.3 - Optimización

- [ ] Índices en base de datos
- [ ] Caché con Redis
- [ ] Optimización de consultas JPA
- [ ] Compresión de imágenes
- [ ] CDN para assets estáticos

### Versión 2.0 - Nuevas Características

- [ ] Sistema de notificaciones (email)
- [ ] Chat vendedor-comprador
- [ ] Sistema de favoritos
- [ ] Dashboard con gráficos
- [ ] API REST completa
- [ ] App móvil (React Native)

### DevOps

- [ ] Dockerización
- [ ] CI/CD con GitHub Actions
- [ ] Monitoreo con Prometheus
- [ ] Logs centralizados (ELK Stack)

---

## 🤝 Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

### Prioridades de Contribución

🔥 **Alta prioridad**: Seguridad (BCrypt, Spring Security)  
📈 **Media prioridad**: Tests, validación, documentación  
🔮 **Baja prioridad**: Nuevas features

---

## 📞 Soporte

Si encuentras algún problema o tienes preguntas:

- 📧 Email: soporte@cultivus.co
- 🐛 Issues: [GitHub Issues](https://github.com/tu-usuario/CULTIVIS-CO/issues)
- 📖 Wiki: [Documentación completa](https://github.com/tu-usuario/CULTIVIS-CO/wiki)

---

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Ver archivo `LICENSE` para más detalles.

---

## 👥 Autores

- **Alex Bryant** - _Desarrollo inicial_ - [@Alexbryant7](https://github.com/Alexbryant7)
- **Dalith Garcia** - _Desarrollo inicial_ - [@DalithDG](https://github.com/DalithDG)
- **Andres Cervantes** - _Desarrollo inicial_ - [@andrescervantes6](https://github.com/andrescervantes6)

---

## 🙏 Agradecimientos

- Spring Boot Team
- Comunidad de desarrolladores Java
- Productores locales que inspiraron este proyecto

---

<div align="center">

**Hecho con ❤️ para apoyar la agricultura local**

[⬆ Volver arriba](#-cultivus-co)

</div>
