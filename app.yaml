FROM maven:3.9.3-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Usa una imagen más ligera para ejecutar la app
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/target/sshweatherapp-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
