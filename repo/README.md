# Eagle Point — Hospital Operations Performance & Service-Area Management Console

A fully offline Windows 11 desktop application for managing hospital operations: KPI tracking, pickup point management, bed board state machine, service area leadership, and evaluation workflows. Powered by a JavaFX GUI with an embedded Javalin HTTP API and SQLite database.

## Architecture & Tech Stack

- **Frontend:** JavaFX 21 (desktop UI — multi-window, system tray, inactivity lock)
- **Backend (embedded):** Javalin 6 HTTP server bound to `127.0.0.1` only
- **Database:** SQLite 3 via `sqlite-jdbc` + Flyway 10 migrations + HikariCP connection pool
- **Auth:** bcrypt (jBCrypt cost=12) passwords; SHA-256-hashed Bearer tokens (24h expiry)
- **Encryption:** AES-256-GCM for sensitive fields (staffId, residentId, address)
- **Key storage:** Windows DPAPI (production); `APP_TEST_ENC_KEY` env var (headless/Docker/CI)
- **Exports:** Apache POI (Excel), PDFBox (PDF), OpenCSV (CSV) + SHA-256 sidecar
- **Scheduler:** Quartz (in-process): daily backup at 02:00, monthly archive at 03:00, daily consistency check at 04:00
- **Logging:** SLF4J + Logback with logstash-logback-encoder; separate BUSINESS and SYSTEM appenders
- **Testing:** JUnit 5 + Mockito (unit); REST-Assured (integration)
- **Build:** Maven + maven-shade-plugin (headless fat JAR excludes JavaFX)
- **Containerization:** Docker (headless mode) + Docker Compose for CI

## Project Structure

```text
repo/
├── backend/                    # Java 21 Maven project
│   ├── src/
│   │   ├── main/java/com/eaglepoint/console/
│   │   │   ├── App.java                    # JavaFX Application entry point
│   │   │   ├── HeadlessEntryPoint.java     # Docker/CI headless entry point
│   │   │   ├── api/                        # Javalin HTTP layer
│   │   │   │   ├── ApiServer.java
│   │   │   │   ├── dto/                    # Request/Response DTOs
│   │   │   │   ├── middleware/             # Auth, RateLimiter, ErrorHandler
│   │   │   │   └── routes/                 # Route registration classes
│   │   │   ├── config/                     # App, DB, Security, Logging config
│   │   │   ├── exception/                  # AppException hierarchy
│   │   │   ├── export/                     # Excel, PDF, CSV exporters
│   │   │   ├── model/                      # Domain POJOs
│   │   │   ├── repository/                 # JDBC repositories (BaseRepository)
│   │   │   ├── scheduler/                  # Quartz jobs + JobScheduler
│   │   │   ├── security/                   # EncryptionUtil, PasswordUtil, MaskingUtil, TokenService
│   │   │   ├── service/                    # Business logic services
│   │   │   └── ui/                         # JavaFX controllers and windows
│   │   └── main/resources/
│   │       ├── app.properties
│   │       ├── css/application.css
│   │       ├── db/migrations/              # Flyway V1 schema + V2 seed
│   │       └── fxml/                       # FXML layout files
│   ├── src/test/java/...
│   │   ├── unit/security/                  # EncryptionUtil, MaskingUtil tests
│   │   ├── unit/service/                   # Service unit tests
│   │   └── integration/                    # REST-Assured API integration tests
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml
├── run_tests.sh
└── README.md
```

## Prerequisites

- **Docker** 24+ and **Docker Compose** v2
- No other local dependencies required — everything runs inside containers

## Running the Application

### Headless / Server mode (Docker)

```bash
# Build and start the headless API server
docker compose up --build -d

# Verify it's healthy
curl http://localhost:8080/api/health

# View logs
docker compose logs -f backend
```

The API is available at `http://localhost:8080`.

### Desktop mode (Windows only)

1. Ensure **Java 21** with JavaFX is installed
2. Build with Maven: `mvn package`
3. Run: `java -jar target/console-headless.jar` (headless) or launch `App.java` with JavaFX runtime

### Stop

```bash
docker compose down -v
```

## Environment Variables

All variables have defaults in `docker-compose.yml` — no `.env` file needed.

| Variable | Default | Description |
|---|---|---|
| `APP_HEADLESS` | `true` | Disables JavaFX UI when `true` |
| `API_PORT` | `8080` | HTTP server port |
| `DB_PATH` | `/app/data/console.db` | SQLite database file path |
| `BACKUP_DIR` | `/app/data/backups` | Directory for DB backups |
| `LOG_DIR` | `/app/logs` | Log output directory |
| `APP_TEST_ENC_KEY` | *(set in compose)* | Base64 AES-256 key for headless mode |

## Database Migrations

Flyway runs automatically on startup. Two migrations are applied:

- `V1__initial_schema.sql` — creates all 27 tables
- `V2__seed_admin_user.sql` — creates default admin user and scheduled job configs

## Seeded Credentials

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin1234!` | `SYSTEM_ADMIN` |

## API Overview

All endpoints require `Authorization: Bearer <token>` except:
- `GET /api/health` — unauthenticated health check
- `POST /api/auth/login` — issues token

Key endpoint groups:

| Path | Description |
|---|---|
| `/api/auth/*` | Login, logout, current user |
| `/api/users/*` | User management (SYSTEM_ADMIN only) |
| `/api/communities/*` | Community CRUD |
| `/api/service-areas/*` | Service area management |
| `/api/pickup-points/*` | Pickup point management + pause/resume/match |
| `/api/leader-assignments/*` | Leader assignment workflow |
| `/api/cycles/*` | Evaluation cycle lifecycle |
| `/api/scorecards/*` | Scorecard CRUD, submit, recuse |
| `/api/reviews/*` | Review approve/reject/flag-conflict/assign-second |
| `/api/appeals/*` | Appeal filing and resolution |
| `/api/bed-buildings/*` | Building management |
| `/api/rooms/*` | Room management |
| `/api/beds/*` | Bed management + state machine transitions |
| `/api/route-imports/*` | CSV route checkpoint imports |
| `/api/kpis/*` | KPI definition management |
| `/api/kpi-scores/*` | KPI score recording |
| `/api/exports/*` | Async export jobs (Excel/PDF/CSV) |
| `/api/geozones/*` | Geographic zone management |
| `/api/jobs/*` | Scheduled job control (SYSTEM_ADMIN) |
| `/api/audit-trail` | Audit trail (SYSTEM_ADMIN, AUDITOR) |
| `/api/logs` | System logs (SYSTEM_ADMIN, AUDITOR) |

## Testing

Tests run inside Docker via Maven Surefire. The `run_tests.sh` orchestrator:
1. Waits for the backend to be healthy
2. Runs API smoke tests via `curl`
3. Executes the Maven test suite inside the container

```bash
chmod +x run_tests.sh
./run_tests.sh
```

Test types:
- **Unit tests** — `src/test/java/.../unit/` — service and security logic (JUnit 5 + Mockito)
- **Integration tests** — `src/test/java/.../integration/` — full API tests (REST-Assured)

## Key Business Rules

- **One ACTIVE pickup point per community** at any time
- **Bed state machine**: OCCUPIED → OUT_OF_SERVICE is explicitly forbidden
- **Appeal deadline**: 7 days from scorecard `submittedAt`; evaluatee only
- **Token expiry**: 24 hours; one active token per user
- **Rate limiting**: 60 requests/minute per token (sliding window)
- **Coordinate masking**: rounded to 0.00145° grid (~0.1 mile)
- **Archival**: evaluation cycles with `end_date < NOW() - 24 months` archived monthly
- **Encryption**: AES-256-GCM for staffId, residentId, address fields at rest
