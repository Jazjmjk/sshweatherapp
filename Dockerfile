# Etapa 1: Build (opcional si ya tienes el .jar)
# FROM eclipse-temurin:17-jdk-alpine as builder
# WORKDIR /app
# COPY . .
# RUN ./mvnw clean package -DskipTests

# Etapa 2: Imagen final
FROM eclipse-temurin:17-jdk-alpine

# Crea un directorio dentro del contenedor
WORKDIR /app

# Copia el .jar generado en tu máquina local (carpeta target/) al contenedor
COPY target/*.jar app.jar

# Expone el puerto de tu app (por defecto 8080 o el que uses)
EXPOSE 8081

# Ejecuta la app
ENTRYPOINT ["java", "-jar", "app.jar"]
