FROM maven:3.9.5-eclipse-temurin-17 AS build

WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app
COPY --from=build /app/target/sshweatherapp-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE ${PORT}

CMD java -jar app.jar --server.port=${PORT}
