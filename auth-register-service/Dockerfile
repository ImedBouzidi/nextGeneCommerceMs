# Stage 1: build the jar
ARG REGISTRY=
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: run the jar
FROM eclipse-temurin:17-jre
VOLUME /tmp
VOLUME /uploads
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]