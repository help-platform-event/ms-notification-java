# syntax=docker/dockerfile:1

# --- Build stage: compile and extract the Spring Boot layers ---------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, in their own layer: only re-downloaded when the pom changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

# Tests are skipped here: they need a Docker daemon (Testcontainers). Run `./mvnw test` instead.
COPY src src
RUN ./mvnw -q package -DskipTests \
    && java -Djarmode=tools -jar target/ms-notification-*.jar extract --layers --launcher --destination /extracted

# --- Runtime stage: JRE only, non-root -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app

# Least- to most-frequently changing, so a code change only rebuilds the last layer.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8085

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8085/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
