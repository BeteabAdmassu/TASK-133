# Multi-stage build: compile with full JDK, run with slim JRE
# Stage 1 — Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src

# Download dependencies first (cached layer)
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q

# Build headless fat JAR (excludes JavaFX)
RUN mvn package -DskipTests -q -P headless

# Stage 2 — Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Create data and log directories
RUN mkdir -p /app/data/backups /app/logs

# Copy headless JAR
COPY --from=builder /build/target/console-headless.jar /app/console-headless.jar

# Non-root user for security
RUN addgroup -S eaglepoint && adduser -S eaglepoint -G eaglepoint && \
    chown -R eaglepoint:eaglepoint /app
USER eaglepoint

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/api/health || exit 1

ENV APP_HEADLESS=true \
    API_PORT=8080 \
    DB_PATH=/app/data/console.db \
    BACKUP_DIR=/app/data/backups \
    LOG_DIR=/app/logs

ENTRYPOINT ["java", "-Xmx512m", "-Dapp.headless=true", "-jar", "/app/console-headless.jar"]
