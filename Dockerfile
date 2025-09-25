FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY .git .git
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*-with-deps.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar","--download"]
