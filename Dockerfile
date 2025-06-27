# Etapa 1: Compilación usando Maven instalado
FROM maven:3.9.5-eclipse-temurin-17 AS build

WORKDIR /app

# Copia el código fuente y el archivo pom.xml
COPY . .

# Ejecuta la compilación (esto genera el .jar en target/)
RUN mvn clean package -DskipTests

# Etapa 2: Imagen liviana para correr el .jar
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copia solo el .jar desde la etapa anterior
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
