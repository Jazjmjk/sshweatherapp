# Etapa 1: Construcción del .jar
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copia el código fuente
COPY . .

# Instala Maven y genera el .jar (salida en /app/target/)
RUN ./mvnw clean package -DskipTests

# Etapa 2: Imagen final
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copia el .jar desde la etapa anterior
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
