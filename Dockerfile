# syntax=docker/dockerfile:1

# --- Build stage: compile and package the fat jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Cache dependencies first: copy the POM and go offline, then copy sources.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage: a slim JRE with just the jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as a non-root user.
RUN useradd --system --no-create-home --uid 10001 fraudguard
COPY --from=build /build/target/fraudguard-*.jar app.jar
USER fraudguard
EXPOSE 8080
# Virtual threads are enabled via application.yml (spring.threads.virtual.enabled).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
