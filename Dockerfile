# --- Etapa de Compilación ---
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copiar el archivo pom.xml desde la carpeta demo
COPY demo/pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente desde demo/src
COPY demo/src ./src
RUN mvn clean package -DskipTests

# --- Etapa de Ejecución ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copiar el JAR generado en la etapa anterior
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar

# Exponer el puerto estándar
EXPOSE 8080

# Iniciar la aplicación
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
