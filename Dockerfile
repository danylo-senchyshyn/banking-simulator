FROM eclipse-temurin:21-jre-alpine AS runtime

# ── Stage 1: extract layers from the fat JAR ──────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 2: assemble the final image layer by layer ──────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Each COPY is a separate Docker layer — cached independently
COPY --from=builder /app/dependencies/           ./
COPY --from=builder /app/spring-boot-loader/     ./
COPY --from=builder /app/snapshot-dependencies/  ./
COPY --from=builder /app/application/            ./

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
