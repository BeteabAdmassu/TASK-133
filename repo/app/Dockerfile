# Multi-stage build: compile with full JDK, run with slim JRE.
# Build context is the repo root so we can include ../tests alongside ./app.
# Stage 1 — Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy the app Maven project and the sibling tests directory.
# The pom.xml declares <testSourceDirectory> as ../tests/java, so the layout
# inside the image must mirror the repo layout.
COPY app ./app
COPY tests ./tests

WORKDIR /build/app

# Download dependencies first (cached layer)
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q

# Build headless fat JAR (excludes JavaFX)
RUN mvn package -DskipTests -q -P headless

# Test stage — runs mvn test when invoked via `docker build --target tests`.
# The seed key matches the Base64 AES-256 key used to generate the ciphertext
# baked into V2__seed_admin_user.sql so staff_id_encrypted rows decrypt cleanly
# on first boot.  Must stay in sync with docker-compose.yml and
# BaseIntegrationTest.
FROM builder AS tests
ENV APP_HEADLESS=true \
    APP_TEST_ENC_KEY=ZWFnbGVwb2ludC1jb25zb2xlLXNlZWQta2V5LXYxISE=
CMD ["mvn", "test", "-q", "--no-transfer-progress"]

# Stage 2 — Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Create data and log directories
RUN mkdir -p /app/data/backups /app/logs

# Copy headless JAR
COPY --from=builder /build/app/target/console-headless.jar /app/console-headless.jar

# Non-root user for security
RUN addgroup -S eaglepoint && adduser -S eaglepoint -G eaglepoint && \
    chown -R eaglepoint:eaglepoint /app
USER eaglepoint

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/api/health || exit 1

ENV APP_HEADLESS=true \
    API_PORT=8080 \
    API_BIND=0.0.0.0 \
    DB_PATH=/app/data/console.db \
    BACKUP_DIR=/app/data/backups \
    LOG_DIR=/app/logs

ENTRYPOINT ["java", "-Xmx512m", "-Dapp.headless=true", "-jar", "/app/console-headless.jar"]
