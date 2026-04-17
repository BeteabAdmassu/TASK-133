# Eagle Point — Hospital Operations Performance & Service-Area Management Console

**Project Type: desktop**

A fully offline Windows 11 desktop application for managing hospital operations:
KPI tracking, pickup-point management, bed-board state machine, service-area
leadership, and evaluation workflows. The primary deliverable is a JavaFX
desktop GUI; it embeds a Javalin HTTP API (bound to `127.0.0.1` in production)
so other local desktop modules can integrate with it. This README documents how
to run the **headless API server** in Docker for CI/verification. The JavaFX UI
is optional and runs only on a Windows/macOS/Linux desktop with a display.

---

## Quick Start

```bash
docker-compose up --build -d     # classic Docker Compose v1 syntax
# or, equivalently, with Compose v2:
docker compose up --build -d

# Verify the app is healthy (wait ~15 s for migrations + boot):
curl http://localhost:8080/api/health
# => {"status":"UP","db":"OK","version":"1.0.0","uptime":...}

# Run the full test suite (unit + integration + API smoke tests):
./run_tests.sh
```

The API is served at **`http://localhost:8080`** (the container binds to
`0.0.0.0` inside Docker; the host port is mapped in `docker-compose.yml`).

Stop and clean up:

```bash
docker compose down -v
```

---

## Architecture & Tech Stack

- **Frontend (desktop):** JavaFX 21 — multi-window operator console with system
  tray, inactivity lock, keyboard shortcuts, right-click context menus
- **Backend (embedded in the desktop app):** Javalin 6 HTTP server bound to
  `127.0.0.1` by default; exposed on `0.0.0.0` only inside the Docker image so
  the port forward works
- **Database:** SQLite 3 via `sqlite-jdbc` + Flyway 10 + HikariCP
- **Auth:** bcrypt (`org.mindrot:jbcrypt` cost=12) passwords; SHA-256-hashed
  Bearer tokens (24 h expiry, one active token per user)
- **Encryption at rest:** AES-256-GCM for `staffId`, `residentId`, `address`
  fields
- **Key storage:** Windows DPAPI (production); `APP_TEST_ENC_KEY` env var
  (headless/Docker/CI)
- **Exports:** Apache POI (Excel), PDFBox (PDF), OpenCSV (CSV), with SHA-256
  sidecar for tamper-evident archives
- **Scheduler:** Quartz in-process — daily backup 02:00, monthly archive 03:00,
  daily consistency check 04:00
- **Logging:** SLF4J + Logback with `logstash-logback-encoder`; separate
  `BUSINESS` and `SYSTEM` appenders
- **Testing:** JUnit 5 + Mockito (unit); REST-Assured (integration)
- **Build:** Maven + `maven-shade-plugin` (headless fat JAR excludes JavaFX)
- **Containerization:** Docker (headless only) + Docker Compose

---

## Project Structure

```text
repo/
├── app/                         # Java 21 Maven project (JavaFX UI + headless Javalin API)
│   ├── src/main/java/com/eaglepoint/console/
│   │   ├── App.java                    # JavaFX Application entry point
│   │   ├── HeadlessEntryPoint.java     # Docker/CI headless entry point
│   │   ├── api/                        # Javalin HTTP layer (routes, middleware, DTOs)
│   │   ├── config/                     # App, DB, Security, Logging config
│   │   ├── exception/                  # AppException hierarchy
│   │   ├── export/                     # Excel, PDF, CSV exporters
│   │   ├── model/                      # Domain POJOs
│   │   ├── repository/                 # JDBC repositories
│   │   ├── scheduler/                  # Quartz jobs + JobScheduler
│   │   ├── security/                   # Encryption, password, masking, token utils
│   │   ├── service/                    # Business logic services
│   │   └── ui/                         # JavaFX controllers and windows (desktop only)
│   ├── src/main/resources/
│   │   ├── app.properties
│   │   ├── css/application.css
│   │   ├── db/migrations/              # Flyway V1 schema + V2 seed
│   │   └── fxml/                       # FXML layout files
│   ├── Dockerfile
│   └── pom.xml                         # testSourceDirectory points to ../tests/java
├── tests/
│   └── java/com/eaglepoint/console/
│       ├── integration/                # REST-Assured API integration tests
│       └── unit/                       # JUnit 5 + Mockito unit tests
├── .dockerignore
├── docker-compose.yml
├── run_tests.sh
└── README.md
```

---

## Prerequisites

Only these host tools are required to run, verify, and test the deliverable:

- **Docker** 24+ and **Docker Compose** v2 (or the classic `docker-compose` v1)
- **bash**, **curl**, **jq**

No host-level Java, Maven, or Node installation is needed for normal
operation — every build and test step happens inside containers.

> **Optional — desktop development workflow.** If you want to run the JavaFX UI
> locally (not required for CI or grading), you will additionally need Java 21
> with JavaFX modules installed. Build with `mvn package` inside `app/` and
> launch `App.java` with the JavaFX runtime. This workflow is **not** exercised
> by `run_tests.sh` and is **not** required to satisfy the deliverable.

---

## Running the Application

### Headless API (Docker — the default, used by CI)

```bash
docker-compose up --build -d      # or: docker compose up --build -d
```

That single command:

1. Builds the multi-stage image in `app/Dockerfile` from the repo root context.
2. Starts the container `task-133-app` with port `8080` mapped to the host.
3. Runs Flyway migrations against the `/app/data/console.db` SQLite file.
4. Seeds the default users and scheduled job rows (`V2__seed_admin_user.sql`).
5. Exposes the Javalin HTTP API on `http://localhost:8080`.

**Deterministic verification**

```bash
# 1. Health probe — should return HTTP 200 with status=UP, db=OK
curl -s http://localhost:8080/api/health | jq
# Expected: { "status":"UP", "db":"OK", "version":"1.0.0", "uptime": <number> }

# 2. Login — should return a bearer token
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin1234!"}' | jq
# Expected: { "token":"...", "user":{"username":"admin","role":"SYSTEM_ADMIN",...}, "expiresAt":"..." }

# 3. Use the token — should return a paged community list
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin1234!"}' | jq -r .token)
curl -s http://localhost:8080/api/communities \
  -H "Authorization: Bearer $TOKEN" | jq
# Expected: { "data":[...], "page":1, "pageSize":50, "totalPages":... }
```

View live logs:

```bash
docker compose logs -f app
```

### Desktop UI (optional; non-CI workflow)

Only run this on a machine with a physical display and Java 21 + JavaFX:

1. Install Java 21 with JavaFX (e.g., Azul Zulu FX or bundled JDK+JavaFX).
2. `cd app && mvn -Pfull package`
3. Launch the JavaFX entry point: `java --module-path $PATH_TO_JAVAFX_LIB --add-modules javafx.controls,javafx.fxml -jar target/console-full.jar` (or run `App.java` from your IDE with the JavaFX run config).
4. On first launch, log in using any demo credential below.

**Verifying the desktop UI**

| Action | Expected outcome |
|---|---|
| Launch the app and log in as `admin` / `Admin1234!` | Main window opens with the operator console and the system-tray icon appears |
| Open **KPI Reviews** from the menu | A new parallel window opens listing KPI definitions |
| Right-click a pickup point row | Context menu shows "Pause service", "Open audit trail" |
| Press **Ctrl+F** | Global search field receives focus |
| Press **Ctrl+E** on a table | Export dialog opens (Excel/PDF/CSV) |
| Leave idle 10 minutes | Inactivity lock screen replaces the main window |

---

## Environment Variables

All variables have defaults inside `docker-compose.yml` — **no `.env` file is
required**. Override them only when you need to (e.g., custom ports).

| Variable | Default (compose) | Description |
|---|---|---|
| `APP_HEADLESS` | `true` | Disables JavaFX UI when `true` |
| `API_PORT` | `8080` | HTTP server port inside the container |
| `API_BIND` | `0.0.0.0` | Bind address (inside the container). Defaults to `127.0.0.1` outside Docker |
| `DB_PATH` | `/app/data/console.db` | SQLite database file path |
| `BACKUP_DIR` | `/app/data/backups` | Directory for DB backups |
| `LOG_DIR` | `/app/logs` | Log output directory |
| `APP_TEST_ENC_KEY` | *(seeded in compose)* | Base64 AES-256 key used in headless mode |

---

## Database Migrations & Seed Data

Flyway runs automatically on startup. Two migrations are applied:

- `V1__initial_schema.sql` — creates all 27 tables with constraints and indexes.
- `V2__seed_admin_user.sql` — creates one seeded user per role (see below) plus
  default scheduled job configurations (`BACKUP`, `ARCHIVE`,
  `CONSISTENCY_CHECK`).

---

## Demo Credentials

All five roles used by the authorization logic are seeded at startup. Every
password is verified against `org.mindrot:jbcrypt:0.4` at cost 12.

| Username          | Password           | Role              | Capabilities |
|-------------------|--------------------|-------------------|--------------|
| `admin`           | `Admin1234!`       | `SYSTEM_ADMIN`    | Full access — all CRUD, user management, jobs, audit, unmasked fields |
| `ops_manager`     | `Manager1234!`     | `OPS_MANAGER`     | Service-area & staffing decisions — communities, service areas, pickup points, beds, leader assignments, KPIs |
| `reviewer`        | `Reviewer1234!`    | `REVIEWER`        | Peer/expert evaluations — read most, approve/reject reviews, recuse, file appeals as evaluatee |
| `auditor`         | `Auditor1234!`     | `AUDITOR`         | Read-only compliance — cannot initiate exports, cannot modify data |
| `data_integrator` | `Integrator1234!`  | `DATA_INTEGRATOR` | Consumes local APIs — read access to published views, rate-limited to 60 req/min |

> Change the passwords before shipping: regenerate the bcrypt hashes inside the
> `tests` stage and update `app/src/main/resources/db/migrations/V2__seed_admin_user.sql`.

---

## API Overview

All endpoints require `Authorization: Bearer <token>` except:

- `GET /api/health` — unauthenticated health probe
- `POST /api/auth/login` — issues a token

Error responses are JSON-shaped:

```json
{ "error": { "code": "CONFLICT", "message": "..." } }
```

Key endpoint groups:

| Path | Description | Required role(s) |
|---|---|---|
| `/api/auth/*` | Login, logout, current user | any authenticated |
| `/api/users/*` | User management | `SYSTEM_ADMIN` |
| `/api/communities/*` | Community CRUD | read: any auth; write: `SYSTEM_ADMIN`, `OPS_MANAGER`; delete: `SYSTEM_ADMIN` |
| `/api/service-areas/*` | Service-area management | read: any auth; write: `SYSTEM_ADMIN`, `OPS_MANAGER`; delete: `SYSTEM_ADMIN` |
| `/api/pickup-points/*` | Pickup points + pause/resume/match | read: any auth; write: `SYSTEM_ADMIN`, `OPS_MANAGER` |
| `/api/leader-assignments/*` | Leader assignment workflow | read: any auth; write: `SYSTEM_ADMIN`, `OPS_MANAGER` |
| `/api/cycles/*` | Evaluation cycle lifecycle (DRAFT → ACTIVE → CLOSED via `/activate` and `/close`) | read: any auth; write/activate/close: `SYSTEM_ADMIN`, `OPS_MANAGER`; delete: `SYSTEM_ADMIN`, `OPS_MANAGER` |
| `/api/scorecards/*` | Scorecard CRUD, submit, recuse | `SYSTEM_ADMIN`, `OPS_MANAGER`, `REVIEWER` |
| `/api/reviews/*` | Review approve/reject/flag-conflict/assign-second | `REVIEWER`, `SYSTEM_ADMIN`, `OPS_MANAGER` |
| `/api/appeals/*` | Appeal file/resolve/reject | file: evaluatee only; resolve/reject: `SYSTEM_ADMIN`, `OPS_MANAGER` |
| `/api/bed-buildings/*`, `/api/rooms/*`, `/api/beds/*` | Bed board + state-machine transitions | read: any auth; write: `SYSTEM_ADMIN`, `OPS_MANAGER` |
| `/api/route-imports/*` | CSV route checkpoint imports | read: any auth; write: `SYSTEM_ADMIN`, `OPS_MANAGER` |
| `/api/kpis/*`, `/api/kpi-scores/*` | KPI definitions and score recording | read: any auth; write: `SYSTEM_ADMIN`, `OPS_MANAGER` |
| `/api/exports/*` | Async export jobs (Excel/PDF/CSV) | any authenticated **except** `AUDITOR` |
| `/api/geozones/*` | Geographic zone management | read: any auth; write: `SYSTEM_ADMIN`, `OPS_MANAGER`; delete: `SYSTEM_ADMIN` |
| `/api/jobs/*` | Scheduled job list/pause/resume | `SYSTEM_ADMIN` |
| `/api/audit-trail` | Audit trail | `SYSTEM_ADMIN`, `AUDITOR` |
| `/api/logs` | System logs | `SYSTEM_ADMIN`, `AUDITOR` |

### Query shaping (sort + field selection)

List endpoints under `/api/communities`, `/api/users`, `/api/beds`, and
`/api/pickup-points` accept two optional shaping query params on top of the
standard pagination:

- `sort=field` — ascending by that field; `sort=-field` — descending;
  `sort=a,-b` — multi-key tiebreaker.
- `fields=a,b,c` — restrict each row to the listed keys (the `id` column is
  always preserved so clients can follow-up).

Shaping is applied in-memory on the current paginated page (default 50, max
500 rows) so it never loosens the documented pagination bounds.

### Exports

`POST /api/exports` starts an async job that queries the requested
`entityType`, writes the rows to the destination folder as CSV / Excel / PDF,
and emits a SHA-256 sidecar next to the output file.  Supported entity types:
`COMMUNITIES`, `SERVICE_AREAS`, `PICKUP_POINTS`, `BEDS`, `BED_BUILDINGS`,
`ROOMS`, `USERS`, `KPIS`, `KPI_SCORES`, `GEOZONES`.  Sensitive fields
(encrypted staff ids, resident ids, pickup addresses) are **always masked** to
`[MASKED]` in export output — the masking is not caller-controlled.

---

## Testing

Tests execute entirely inside Docker — no host Java install needed. The
`run_tests.sh` orchestrator:

1. Waits for `/api/health` to return `status=UP` (timeout 120 s).
2. Builds the `tests` stage of `app/Dockerfile` which compiles and runs
   `mvn test` (unit + REST-Assured integration).
3. Runs a jq-validated set of HTTP smoke tests against the running container
   (login + wrong-password + `/me` + communities CRUD + role-access 403 +
   audit-trail + 401 + logout).

```bash
./run_tests.sh       # exits 0 only when every check passes
```

Test layout:

- `tests/java/com/eaglepoint/console/unit/` — service and security unit tests
  (JUnit 5 + Mockito).
- `tests/java/com/eaglepoint/console/integration/` — REST-Assured integration
  tests that boot the embedded Javalin server once per JVM and exercise real
  HTTP endpoints with deterministic per-test fixtures.

Coverage includes: Auth, Users, Communities, Service Areas, Leader Assignments,
Evaluation Cycles (incl. deep paths: scorecards submit/recuse/responses,
reviews approve/reject/flag-conflict/assign-second, appeals file/resolve/reject),
Bed management (CRUD + state transitions), Route Imports, KPIs, Exports,
Geozones, System admin (audit-trail/logs/jobs), Rate Limiter, and Pickup
Points.

---

## Key Business Rules

- **One ACTIVE pickup point per community** at any time.
- **Bed state machine**: `OCCUPIED → OUT_OF_SERVICE` is explicitly forbidden; a
  `CLEANING` checkpoint is required.
- **Appeal deadline**: 7 calendar days from scorecard `submittedAt`; only the
  evaluatee can file.
- **Token expiry**: 24 h; one active token per user; fresh login revokes the
  previous token.
- **Rate limiting**: 60 requests/minute per authenticated user (sliding
  window); `/api/health` is exempt.
- **Coordinate masking**: 0.00145° grid (~0.1 mile) for non-`SYSTEM_ADMIN`
  readers.
- **Archival**: evaluation cycles with `end_date < NOW() - 24 months` are
  archived monthly.
- **Encryption**: AES-256-GCM for `staffId`, `residentId`, `address` fields at
  rest; plaintext returned only to `SYSTEM_ADMIN`.
