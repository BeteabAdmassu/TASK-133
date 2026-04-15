# Design Document — Hospital Operations Performance & Service-Area Management Console

## Business Goal
Provide hospital operations staff with a fully offline, Windows-desktop console for managing service-area KPIs, community pickup points, housing bed states, and performance evaluations — all persisted locally in SQLite and accessible to other hospital desktop modules via a localhost REST API.

---

## Core Requirements

### Authentication & Access Control
1. Five roles exist: `SYSTEM_ADMIN`, `OPS_MANAGER`, `REVIEWER`, `AUDITOR`, `DATA_INTEGRATOR`; each role grants a distinct permission set (see Role Matrix below).
2. Session locks automatically after 10 minutes of user inactivity; resuming requires re-entering credentials on the lock screen.
3. API callers authenticate with a per-user bearer token; tokens are issued at login and stored hashed in the database.
4. Rate limiting enforces 60 API requests/minute per local user token; excess requests receive HTTP 429.
5. `AUDITOR` role is read-only across all resources.
6. `DATA_INTEGRATOR` role may only call read/export API endpoints; it cannot mutate data through the UI.

### Multi-Window UI
7. The application supports multiple simultaneously open top-level windows: KPI Reviews, Pickup Points, Bed Board, Reports; each window is independently resizable.
8. Global keyboard shortcuts are available from any focused window: `Ctrl+F` (search), `Ctrl+N` (new record), `Ctrl+E` (export), `Ctrl+L` (open logs).
9. Right-click context menus on relevant table rows expose: Assign Leader, Pause Service, Transfer Bed, Open Audit Trail.
10. The application minimizes to system tray; tray icon provides a quick menu (Open, Lock, Exit).
11. While minimized to tray, scheduled background jobs (nightly backups, archival, consistency checks, report generation) continue running.
12. The UI renders correctly at 1920x1080 and higher resolutions with high-DPI scaling (JavaFX `Screen.getMainScreen().getDpi()` detection).
13. The application must remain stable across 8+ hour sessions through explicit release of file handles, JDBC connections, and background threads on window close.

### Performance Evaluations
14. Evaluations are organized into `EvaluationCycle` records with a name, start date, end date, and status (`DRAFT -> ACTIVE -> CLOSED -> ARCHIVED`).
15. Each cycle has one or more `ScorecardTemplate` definitions; each template has a type (`SELF`, `PEER`, `EXPERT`) and a set of `ScorecardMetric` entries with a numeric weight; weights within a template must sum exactly to 100.0.
16. A `Scorecard` is an instance of a template assigned to a specific evaluatee + evaluator pair for a cycle.
17. Reviewers may flag a conflict of interest on any scorecard; upon flagging, the reviewer must supply a recusal reason and the scorecard status transitions to `RECUSED`.
18. An `Appeal` may be filed against a submitted scorecard within 7 calendar days of submission; filing after the deadline is rejected with an error message.
19. Re-reviews (triggered by a rejected appeal or specific admin action) require a second, distinct expert reviewer to be assigned; the same reviewer who completed the original review cannot serve as the second reviewer.
20. Scorecards progress through states: `PENDING -> IN_PROGRESS -> SUBMITTED -> APPROVED` (or `RECUSED` or `APPEALED`).

### Pickup-Point Management
21. Each `PickupPoint` has: encrypted address, ZIP code, street range (start/end), operating hours (per weekday), capacity (integer >= 1), status, and an optional link to a `Geozone`.
22. At most one pickup point per community may be in `ACTIVE` status on any given calendar day; attempting to activate a second raises a validation error.
23. A pickup point may be temporarily paused with a reason and `paused_until` datetime; after `paused_until` the system automatically transitions the point back to `ACTIVE`.
24. Pickup-point matching resolves community-to-pickup-point by first checking ZIP+street-range overlap in `geozones`, falling back to ZIP-only match; a manual override field overrides automated matching.
25. Leaders are assigned to service areas via `LeaderAssignment` records; only users with role `OPS_MANAGER` or `SYSTEM_ADMIN` may create/end assignments.

### Route Tracking (Offline Evidence Intake)
26. Staff import checkpoint log files from USB-connected handheld devices; the import UI accepts files in a defined CSV/JSON format (see Validation Rules).
27. On import, the system validates file format (required columns, type coercions) before processing; a batch with format errors is rejected entirely with line-level error detail.
28. Coordinate data in stored `RouteCheckpoint` records is masked to a 0.1-mile grid in all UI views and API responses (`lat` and `lon` are rounded to the nearest 0.1-mile equivalent in decimal degrees ~0.00145 deg).
29. An alert is raised (added to on-screen inbox) for any checkpoint where the actual location deviates from expected by more than 0.5 miles.
30. An alert is raised for any checkpoint where `actual_at` is more than 15 minutes later than `expected_at`.
31. Import operations are crash-safe: progress is checkpointed to a file so an interrupted import can resume from the last committed batch without re-importing prior records.

### Bed-State Management
32. Beds are organized in a hierarchy: `BedBuilding -> BedRoom -> Bed`.
33. Each `Bed` carries a state machine with states: `AVAILABLE`, `OCCUPIED`, `RESERVED`, `CLEANING`, `OUT_OF_SERVICE`, `MAINTENANCE`.
34. Allowed state transitions (all others are rejected with a validation error):
    - `AVAILABLE -> OCCUPIED`, `AVAILABLE -> RESERVED`, `AVAILABLE -> CLEANING`, `AVAILABLE -> MAINTENANCE`
    - `OCCUPIED -> AVAILABLE` (checkout), `OCCUPIED -> RESERVED`, `OCCUPIED -> CLEANING`
    - `RESERVED -> OCCUPIED`, `RESERVED -> AVAILABLE`
    - `CLEANING -> AVAILABLE`, `CLEANING -> MAINTENANCE`
    - `MAINTENANCE -> AVAILABLE`, `MAINTENANCE -> OUT_OF_SERVICE`
    - `OUT_OF_SERVICE -> MAINTENANCE`
    - **Forbidden**: `OCCUPIED -> OUT_OF_SERVICE` (must checkout first)
35. Every state transition is recorded in `BedStateHistory` with the acting user, timestamp, and optional notes; resident identifier is captured at transition time (encrypted).
36. The Bed Board window displays a visual grid of all beds color-coded by state; beds can be filtered by building, floor, and state.

### Local REST API
37. The embedded API server binds to `127.0.0.1` only; binding to `0.0.0.0` is not permitted.
38. All list endpoints support: `?page=` (1-based, default 1), `?pageSize=` (default 50, max 500), `?sort=field:asc|desc`, `?fields=` (comma-separated field selection).
39. Exports initiated via API write files to a caller-specified destination path; the server verifies write permission on the destination folder before starting; if the check fails it returns 403 with message.
40. Every export file (Excel, PDF, CSV) is accompanied by a `.sha256` sidecar file containing the SHA-256 hash of the export file for tamper-evidence.
41. Export responses mask sensitive fields by default (staff IDs replaced with `[MASKED]`, resident identifiers replaced with `[MASKED]`); a `?unmask=true` parameter is available only to `SYSTEM_ADMIN` tokens.

### Logging & Governance
42. Two log categories exist: `BUSINESS` (domain events: evaluation approved, bed transferred, pickup paused) and `SYSTEM` (infra: startup, DB errors, job failures).
43. Key-path tracing logs the full event chain for critical workflows: evaluation approval, bed state transfer, pickup-point pause; each step in the chain shares a `trace_id`.
44. Nightly backups run at 02:00 local time; backups are retained for 14 days; older backup files are deleted automatically.
45. Monthly archival (runs on the 1st of each month at 03:00) moves `EvaluationCycle` records (and all children) whose `end_date` is older than 24 months to an archive table; the original records are deleted after successful archival.
46. Consistency validators (run as part of the scheduled consistency check job) detect and report: orphaned `Scorecard` records without a valid `EvaluationCycle`, beds with no parent `BedRoom`, `LeaderAssignment` records referencing inactive users.

### Observability & Resilience
47. A runtime health panel (accessible from any window via menu) shows: DB connection status, last backup timestamp, last consistency check result, active job list, and API server uptime.
48. Exception alerts are delivered to an on-screen notification inbox in the main window; each alert shows severity, message, entity reference, and timestamp; alerts persist until dismissed.
49. Background jobs have a configurable timeout; if a job exceeds its timeout the scheduler marks it `FAILED`, rolls back any in-progress transaction, and raises an inbox alert.
50. Export and route-import jobs write progress checkpoints to a temp file; if the application crashes mid-job the job resumes from the last checkpoint on next startup.
51. Application updates are delivered as a signed `.msi` installer; the update UI shows version, changelog, and a "Rollback to previous version" button (invoking the previous `.msi` uninstall + re-install).

### KPI Scoring
52. `KpiDefinition` records define metrics (name, unit, category, optional formula string); scores are recorded per `KpiScore` with a value, date, optional cycle reference, and optional service-area reference.
53. The KPI Reviews window shows a table of KPI scores filterable by date range, service area, and category; scores can be entered manually or imported from a CSV.
54. KPI scores are computed offline; no external service calls are made.

### Analytics & Exports
55. The Reports window allows users to configure and generate four report types: KPI Summary, Evaluation Results, Bed Occupancy, and Pickup-Point Utilization.
56. All reports can be exported as Excel (`.xlsx`), PDF (`.pdf`), or CSV (`.csv`).
57. Scheduled reports run on a user-defined cron schedule; output goes to a configured folder.

---

## Role Matrix

| Permission | SYSTEM_ADMIN | OPS_MANAGER | REVIEWER | AUDITOR | DATA_INTEGRATOR |
|---|---|---|---|---|---|
| Manage users / roles | Yes | No | No | No | No |
| Configure service areas | Yes | Yes | No | No | No |
| Assign leaders | Yes | Yes | No | No | No |
| Manage pickup points | Yes | Yes | No | No | No |
| Evaluate (submit scorecard) | Yes | Yes | Yes | No | No |
| Approve/reject reviews | Yes | No | Yes | No | No |
| File/resolve appeals | Yes | Yes | No | No | No |
| Manage beds / transitions | Yes | Yes | No | No | No |
| Import route checkpoints | Yes | Yes | No | No | No |
| View all data | Yes | Yes | Yes | Yes | No |
| Call read API endpoints | Yes | Yes | Yes | Yes | Yes |
| Call mutating API endpoints | Yes | Yes | Yes | No | No |
| Unmask sensitive fields | Yes | No | No | No | No |
| Manage scheduled jobs | Yes | No | No | No | No |

---

## Main User Flow

1. User launches the application -> splash screen -> Login dialog (username + password fields).
2. User submits credentials -> server verifies bcrypt hash -> issues session + API token -> main window opens.
3. Main window shows navigation toolbar with buttons: KPI Reviews, Pickup Points, Bed Board, Reports, Health Panel, Notification Inbox (badge count).
4. User opens Bed Board window (Ctrl+N or toolbar) -> grid of beds loads, color-coded by state.
5. User right-clicks a bed in `AVAILABLE` state -> context menu -> "Admit Resident" -> dialog asks for encrypted resident ID + notes -> bed transitions to `OCCUPIED` -> `BedStateHistory` record created -> audit trail updated.
6. User opens Pickup Points window -> table of all pickup points loads -> user selects a row -> right-click -> "Pause Service" -> dialog prompts pause reason + `paused_until` -> point status becomes `PAUSED`.
7. User opens KPI Reviews -> enters a new KPI score (Ctrl+N) -> form with KPI definition, value, date, service area -> saves -> score appears in table.
8. User opens Reports -> selects "Evaluation Results" report type -> sets date range -> clicks Export (Ctrl+E) -> chooses format (Excel) -> system checks destination folder permission -> writes file + `.sha256` sidecar -> inbox notification "Export complete."
9. User minimizes to tray -> 10 minutes pass -> lock screen overlay appears on next restore -> user re-authenticates -> session resumes exactly where left off.

---

## Additional User Flows

### Error Flow
- Invalid login: "Invalid username or password" message shown inline; no indication of which field is wrong.
- Duplicate community name: 409 response -> UI shows "A community with this name already exists."
- Bed state violation (e.g., OCCUPIED -> OUT_OF_SERVICE): dialog shows "Cannot transition bed from Occupied to Out of Service. Please check out the resident first."
- Export folder not writable: inline error in the export dialog; job is not created.
- Route import format error: validation summary dialog lists each line with its error; import is not started.

### Inactivity Lock Flow
- Any window detects mouse/keyboard inactivity for 10 minutes -> lock screen covers all open windows -> only the currently logged-in user's username/password unlocks -> all windows restore to prior state.

### Recusal Flow
- Reviewer opens assigned scorecard -> clicks "Flag Conflict" -> supplies recusal reason -> scorecard transitions to `RECUSED` -> system admin is notified via inbox -> admin reassigns to another reviewer.

### Appeal Flow
- Evaluatee views submitted scorecard -> sees "Appeal" button active within 7-day window -> fills appeal reason -> appeal filed -> `PENDING` status -> assigned reviewer or admin reviews appeal -> resolves with notes -> scorecard updated.

### Re-Review Flow
- Admin or reviewer triggers re-review -> system requires selection of a second expert reviewer (different from original) -> new `Review` record created linked to second reviewer -> original scorecard marked `UNDER_REVIEW`.

### Background Job Flow
- Nightly at 02:00: `BackupJob` runs -> compresses SQLite DB file -> writes to backup folder -> deletes backups older than 14 days -> logs result -> raises inbox alert only on failure.
- Monthly on the 1st at 03:00: `ArchivalJob` queries cycles with `end_date < NOW() - 24 months` -> copies to archive tables -> deletes originals -> logs record count.

---

## Tech Stack

- **Frontend**: JavaFX 21 + Java 21 + Maven
- **Backend (embedded)**: Javalin 6 (embedded HTTP server, binds to 127.0.0.1) + Java 21
- **Database**: SQLite 3 via `sqlite-jdbc` driver + Flyway for schema migrations
- **Auth**: bcrypt password hashing (`jBCrypt`); API bearer tokens (random 256-bit, stored as SHA-256 hash in DB)
- **Encryption**: AES-256-GCM for sensitive fields at rest (`javax.crypto`); key stored in OS keystore (Windows DPAPI via `KeyStore`)
- **Export libraries**: Apache POI (Excel), Apache PDFBox (PDF), OpenCSV (CSV)
- **Scheduler**: Quartz Scheduler (in-process, no external daemon)
- **Logging**: SLF4J + Logback with JSON encoder; separate appenders for BUSINESS and SYSTEM categories
- **Testing**: JUnit 5 + Mockito (unit); REST-Assured (integration against running embedded server)
- **Build/packaging**: Maven + `javafx-maven-plugin`; WiX Toolset for `.msi` packaging
- **Containerization**: Docker (headless mode -- JavaFX UI disabled, API + scheduler only) + docker-compose for CI test runs

> **Justification**: JavaFX is explicitly required by the prompt. Javalin was chosen for the embedded API because it is lightweight, embeds with a single JAR, and starts/stops programmatically within the JavaFX lifecycle. SQLite is explicitly required. Quartz is the standard Java in-process scheduler.

---

## Database Schema

```sql
-- SQLite; all TEXT datetimes are ISO-8601 UTC strings.
-- Sensitive fields marked _encrypted are AES-256-GCM ciphertext stored as Base64.

CREATE TABLE users (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  username          TEXT NOT NULL UNIQUE,
  password_hash     TEXT NOT NULL,          -- jBCrypt hash
  display_name      TEXT NOT NULL,
  role              TEXT NOT NULL CHECK (role IN
                    ('SYSTEM_ADMIN','OPS_MANAGER','REVIEWER','AUDITOR','DATA_INTEGRATOR')),
  staff_id_encrypted TEXT NOT NULL,         -- AES-256-GCM Base64
  is_active         INTEGER NOT NULL DEFAULT 1,
  last_login        TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE api_tokens (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id           INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash        TEXT NOT NULL UNIQUE,   -- SHA-256 of the raw token
  expires_at        TEXT NOT NULL,
  created_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_api_tokens_hash ON api_tokens(token_hash);

CREATE TABLE communities (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  description       TEXT,
  status            TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE geozones (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  zip_codes         TEXT NOT NULL,          -- JSON array of strings
  street_ranges     TEXT,                   -- JSON: [{"zip":"12345","start":"100","end":"299"}]
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE service_areas (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  community_id      INTEGER NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  name              TEXT NOT NULL,
  description       TEXT,
  status            TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(community_id, name)
);
CREATE INDEX idx_service_areas_community ON service_areas(community_id);

CREATE TABLE pickup_points (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  community_id          INTEGER NOT NULL REFERENCES communities(id) ON DELETE RESTRICT,
  service_area_id       INTEGER REFERENCES service_areas(id) ON DELETE SET NULL,
  geozone_id            INTEGER REFERENCES geozones(id) ON DELETE SET NULL,
  address_encrypted     TEXT NOT NULL,      -- AES-256-GCM Base64
  zip_code              TEXT NOT NULL,
  street_range_start    TEXT,
  street_range_end      TEXT,
  hours_json            TEXT NOT NULL,      -- {"mon":"08:00-17:00","tue":"08:00-17:00",...}
  capacity              INTEGER NOT NULL DEFAULT 1 CHECK (capacity >= 1),
  status                TEXT NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','PAUSED','INACTIVE')),
  paused_until          TEXT,
  pause_reason          TEXT,
  manual_override       INTEGER NOT NULL DEFAULT 0,
  override_notes        TEXT,
  created_at            TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at            TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_pickup_points_community ON pickup_points(community_id);
CREATE INDEX idx_pickup_points_status    ON pickup_points(status);

CREATE TABLE leader_assignments (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  service_area_id   INTEGER NOT NULL REFERENCES service_areas(id) ON DELETE CASCADE,
  user_id           INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  assigned_by       INTEGER NOT NULL REFERENCES users(id),
  assigned_at       TEXT NOT NULL DEFAULT (datetime('now')),
  unassigned_at     TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_leader_assignments_area ON leader_assignments(service_area_id);

CREATE TABLE evaluation_cycles (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  start_date        TEXT NOT NULL,
  end_date          TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','ACTIVE','CLOSED','ARCHIVED')),
  created_by        INTEGER NOT NULL REFERENCES users(id),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE scorecard_templates (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  cycle_id          INTEGER NOT NULL REFERENCES evaluation_cycles(id) ON DELETE CASCADE,
  name              TEXT NOT NULL,
  type              TEXT NOT NULL CHECK (type IN ('SELF','PEER','EXPERT')),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(cycle_id, name)
);

CREATE TABLE scorecard_metrics (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  template_id       INTEGER NOT NULL REFERENCES scorecard_templates(id) ON DELETE CASCADE,
  name              TEXT NOT NULL,
  description       TEXT,
  weight            REAL NOT NULL CHECK (weight > 0),
  max_score         REAL NOT NULL DEFAULT 5.0 CHECK (max_score > 0),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_metrics_template ON scorecard_metrics(template_id);

CREATE TABLE scorecards (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  cycle_id          INTEGER NOT NULL REFERENCES evaluation_cycles(id) ON DELETE CASCADE,
  template_id       INTEGER NOT NULL REFERENCES scorecard_templates(id),
  evaluatee_id      INTEGER NOT NULL REFERENCES users(id),
  evaluator_id      INTEGER NOT NULL REFERENCES users(id),
  type              TEXT NOT NULL CHECK (type IN ('SELF','PEER','EXPERT')),
  status            TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','IN_PROGRESS','SUBMITTED',
                                      'APPROVED','RECUSED','APPEALED')),
  submitted_at      TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_scorecards_cycle     ON scorecards(cycle_id);
CREATE INDEX idx_scorecards_evaluatee ON scorecards(evaluatee_id);
CREATE INDEX idx_scorecards_evaluator ON scorecards(evaluator_id);

CREATE TABLE scorecard_responses (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  scorecard_id      INTEGER NOT NULL REFERENCES scorecards(id) ON DELETE CASCADE,
  metric_id         INTEGER NOT NULL REFERENCES scorecard_metrics(id),
  score             REAL NOT NULL,
  comments          TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(scorecard_id, metric_id)
);

CREATE TABLE reviews (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  scorecard_id          INTEGER NOT NULL REFERENCES scorecards(id) ON DELETE CASCADE,
  reviewer_id           INTEGER NOT NULL REFERENCES users(id),
  second_reviewer_id    INTEGER REFERENCES users(id),
  status                TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','IN_REVIEW','APPROVED',
                                         'REJECTED','RECUSED')),
  conflict_flagged      INTEGER NOT NULL DEFAULT 0,
  recusal_reason        TEXT,
  recused_at            TEXT,
  reviewed_at           TEXT,
  comments              TEXT,
  created_at            TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at            TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_reviews_scorecard ON reviews(scorecard_id);

CREATE TABLE appeals (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  scorecard_id      INTEGER NOT NULL REFERENCES scorecards(id) ON DELETE CASCADE,
  filed_by          INTEGER NOT NULL REFERENCES users(id),
  filed_at          TEXT NOT NULL DEFAULT (datetime('now')),
  deadline          TEXT NOT NULL,          -- filed_at + 7 calendar days
  reason            TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','UNDER_REVIEW','RESOLVED','REJECTED','EXPIRED')),
  resolved_at       TEXT,
  resolution_notes  TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_appeals_scorecard ON appeals(scorecard_id);

CREATE TABLE bed_buildings (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  address           TEXT,
  service_area_id   INTEGER REFERENCES service_areas(id) ON DELETE SET NULL,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE bed_rooms (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  building_id       INTEGER NOT NULL REFERENCES bed_buildings(id) ON DELETE CASCADE,
  room_number       TEXT NOT NULL,
  floor             INTEGER,
  room_type         TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(building_id, room_number)
);
CREATE INDEX idx_bed_rooms_building ON bed_rooms(building_id);

CREATE TABLE beds (
  id                      INTEGER PRIMARY KEY AUTOINCREMENT,
  room_id                 INTEGER NOT NULL REFERENCES bed_rooms(id) ON DELETE CASCADE,
  bed_label               TEXT NOT NULL,
  state                   TEXT NOT NULL DEFAULT 'AVAILABLE'
                          CHECK (state IN ('AVAILABLE','OCCUPIED','RESERVED',
                                           'CLEANING','OUT_OF_SERVICE','MAINTENANCE')),
  resident_id_encrypted   TEXT,
  admitted_at             TEXT,
  created_at              TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at              TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(room_id, bed_label)
);
CREATE INDEX idx_beds_room  ON beds(room_id);
CREATE INDEX idx_beds_state ON beds(state);

CREATE TABLE bed_state_history (
  id                      INTEGER PRIMARY KEY AUTOINCREMENT,
  bed_id                  INTEGER NOT NULL REFERENCES beds(id) ON DELETE CASCADE,
  from_state              TEXT NOT NULL,
  to_state                TEXT NOT NULL,
  changed_by              INTEGER NOT NULL REFERENCES users(id),
  changed_at              TEXT NOT NULL DEFAULT (datetime('now')),
  reason                  TEXT,
  resident_id_encrypted   TEXT,
  notes                   TEXT
);
CREATE INDEX idx_bed_history_bed ON bed_state_history(bed_id);

CREATE TABLE route_imports (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  filename          TEXT NOT NULL,
  imported_by       INTEGER NOT NULL REFERENCES users(id),
  imported_at       TEXT NOT NULL DEFAULT (datetime('now')),
  status            TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','VALIDATING','VALID','INVALID',
                                      'PROCESSING','COMPLETED','FAILED')),
  record_count      INTEGER,
  error_count       INTEGER DEFAULT 0,
  checkpoint_path   TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE route_checkpoints (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  import_id           INTEGER NOT NULL REFERENCES route_imports(id) ON DELETE CASCADE,
  checkpoint_name     TEXT NOT NULL,
  expected_at         TEXT NOT NULL,
  actual_at           TEXT,
  lat_masked          REAL,
  lon_masked          REAL,
  deviation_miles     REAL,
  is_deviation_alert  INTEGER NOT NULL DEFAULT 0,
  is_missed_alert     INTEGER NOT NULL DEFAULT 0,
  status              TEXT NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','ON_TIME','DEVIATED','MISSED')),
  created_at          TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_route_checkpoints_import ON route_checkpoints(import_id);

CREATE TABLE kpi_definitions (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  description       TEXT,
  unit              TEXT,
  category          TEXT NOT NULL,
  formula           TEXT,
  is_active         INTEGER NOT NULL DEFAULT 1,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE kpi_scores (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  kpi_id            INTEGER NOT NULL REFERENCES kpi_definitions(id) ON DELETE CASCADE,
  service_area_id   INTEGER REFERENCES service_areas(id) ON DELETE SET NULL,
  cycle_id          INTEGER REFERENCES evaluation_cycles(id) ON DELETE SET NULL,
  score_date        TEXT NOT NULL,
  value             REAL NOT NULL,
  computed_by       INTEGER REFERENCES users(id),
  notes             TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_kpi_scores_kpi          ON kpi_scores(kpi_id);
CREATE INDEX idx_kpi_scores_date         ON kpi_scores(score_date);
CREATE INDEX idx_kpi_scores_service_area ON kpi_scores(service_area_id);

CREATE TABLE export_jobs (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  type              TEXT NOT NULL CHECK (type IN ('EXCEL','PDF','CSV')),
  entity_type       TEXT NOT NULL,
  filters_json      TEXT,
  destination_path  TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
  output_file_path  TEXT,
  sha256_hash       TEXT,
  initiated_by      INTEGER NOT NULL REFERENCES users(id),
  started_at        TEXT,
  completed_at      TEXT,
  error_message     TEXT,
  checkpoint_path   TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE scheduled_jobs (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  job_type          TEXT NOT NULL CHECK (job_type IN
                    ('BACKUP','ARCHIVE','CONSISTENCY_CHECK','REPORT')),
  cron_expression   TEXT NOT NULL,
  timeout_seconds   INTEGER NOT NULL DEFAULT 3600,
  last_run          TEXT,
  next_run          TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','PAUSED','FAILED')),
  last_result       TEXT,
  config_json       TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE audit_trail (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  entity_type       TEXT NOT NULL,
  entity_id         INTEGER NOT NULL,
  action            TEXT NOT NULL,
  user_id           INTEGER NOT NULL REFERENCES users(id),
  trace_id          TEXT,
  occurred_at       TEXT NOT NULL DEFAULT (datetime('now')),
  old_values_json   TEXT,
  new_values_json   TEXT,
  notes             TEXT
);
CREATE INDEX idx_audit_entity ON audit_trail(entity_type, entity_id);
CREATE INDEX idx_audit_user   ON audit_trail(user_id);
CREATE INDEX idx_audit_trace  ON audit_trail(trace_id);

CREATE TABLE system_logs (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  level             TEXT NOT NULL CHECK (level IN ('INFO','WARN','ERROR','CRITICAL')),
  category          TEXT NOT NULL CHECK (category IN ('BUSINESS','SYSTEM')),
  message           TEXT NOT NULL,
  entity_type       TEXT,
  entity_id         INTEGER,
  user_id           INTEGER REFERENCES users(id),
  trace_id          TEXT,
  request_id        TEXT,
  path              TEXT,
  status_code       INTEGER,
  duration_ms       INTEGER,
  created_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_logs_level    ON system_logs(level);
CREATE INDEX idx_logs_category ON system_logs(category);
CREATE INDEX idx_logs_created  ON system_logs(created_at);

-- Archive tables populated by ArchivalJob
CREATE TABLE IF NOT EXISTS evaluation_cycles_archive AS
  SELECT * FROM evaluation_cycles WHERE 0;
CREATE TABLE IF NOT EXISTS scorecards_archive AS
  SELECT * FROM scorecards WHERE 0;
```

---

## API Endpoints

All endpoints served on `http://127.0.0.1:PORT/api/`. All responses are JSON. All mutating endpoints require `Authorization: Bearer <token>`. Rate limit: 60 req/min per token (HTTP 429 on excess). Pagination params apply to all list endpoints unless noted.

### Authentication

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| POST | /api/auth/login | No | Login | `{username, password}` | 200 `{user:{id,username,role}, token, expiresAt}` | 400 missing fields, 401 invalid |
| POST | /api/auth/logout | Yes | Revoke token | -- | 204 | 401 |
| GET | /api/auth/me | Yes | Current user | -- | 200 `{user}` | 401 |
| GET | /api/health | No | Health check | -- | 200 `{status:"ok", db, uptime, lastBackup, version}` | -- |

### Users

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/users | Yes (ADMIN) | List users | -- | 200 `{data, total, page, pageSize}` | 401, 403 |
| POST | /api/users | Yes (ADMIN) | Create user | `{username, password, displayName, role, staffId}` | 201 `{user}` | 400, 401, 403, 409 |
| GET | /api/users/:id | Yes (ADMIN) | Get user | -- | 200 `{user}` | 401, 403, 404 |
| PUT | /api/users/:id | Yes (ADMIN) | Update user | `{displayName?, role?, isActive?, staffId?}` | 200 `{user}` | 400, 401, 403, 404 |
| DELETE | /api/users/:id | Yes (ADMIN) | Deactivate user | -- | 204 | 401, 403, 404 |

### Communities

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/communities | Yes | List | -- | 200 `{data, total, page, pageSize}` | 401 |
| POST | /api/communities | Yes (ADMIN,OPS) | Create | `{name, description?}` | 201 `{community}` | 400, 401, 403, 409 |
| GET | /api/communities/:id | Yes | Get | -- | 200 `{community}` | 401, 404 |
| PUT | /api/communities/:id | Yes (ADMIN,OPS) | Update | `{name?, description?, status?}` | 200 `{community}` | 400, 401, 403, 404, 409 |
| DELETE | /api/communities/:id | Yes (ADMIN) | Delete | -- | 204 | 401, 403, 404, 409 has children |

### Service Areas

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/service-areas | Yes | List | -- | 200 `{data, total, page, pageSize}` | 401 |
| POST | /api/service-areas | Yes (ADMIN,OPS) | Create | `{communityId, name, description?}` | 201 `{serviceArea}` | 400, 401, 403, 404, 409 |
| GET | /api/service-areas/:id | Yes | Get | -- | 200 `{serviceArea}` | 401, 404 |
| PUT | /api/service-areas/:id | Yes (ADMIN,OPS) | Update | `{name?, description?, status?}` | 200 `{serviceArea}` | 400, 401, 403, 404 |
| DELETE | /api/service-areas/:id | Yes (ADMIN) | Delete | -- | 204 | 401, 403, 404 |

### Pickup Points

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/pickup-points | Yes | List | -- | 200 `{data, total, page, pageSize}` | 401 |
| POST | /api/pickup-points | Yes (ADMIN,OPS) | Create | `{communityId, address, zipCode, streetRangeStart?, streetRangeEnd?, hoursJson, capacity, geozoneId?}` | 201 `{pickupPoint}` | 400, 401, 403, 404, 409 |
| GET | /api/pickup-points/:id | Yes | Get | -- | 200 `{pickupPoint}` | 401, 404 |
| PUT | /api/pickup-points/:id | Yes (ADMIN,OPS) | Update | `{address?, zipCode?, capacity?, hoursJson?, geozoneId?}` | 200 `{pickupPoint}` | 400, 401, 403, 404 |
| DELETE | /api/pickup-points/:id | Yes (ADMIN) | Delete | -- | 204 | 401, 403, 404 |
| POST | /api/pickup-points/:id/pause | Yes (ADMIN,OPS) | Pause | `{reason, pausedUntil}` | 200 `{pickupPoint}` | 400, 401, 403, 404, 409 |
| POST | /api/pickup-points/:id/resume | Yes (ADMIN,OPS) | Resume | -- | 200 `{pickupPoint}` | 401, 403, 404, 409 |
| POST | /api/pickup-points/match | Yes | Match by ZIP/geozone | `{zipCode, streetAddress?, communityId}` | 200 `{pickupPoint}` | 400, 401, 404 |

### Leader Assignments

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/leader-assignments | Yes | List | -- | 200 `{data, total, page, pageSize}` | 401 |
| POST | /api/leader-assignments | Yes (ADMIN,OPS) | Assign | `{serviceAreaId, userId}` | 201 `{assignment}` | 400, 401, 403, 404 |
| PUT | /api/leader-assignments/:id/end | Yes (ADMIN,OPS) | End assignment | -- | 200 `{assignment}` | 401, 403, 404 |

### Evaluation Cycles

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/cycles | Yes | List | -- | 200 `{data, total, page, pageSize}` | 401 |
| POST | /api/cycles | Yes (ADMIN,OPS) | Create | `{name, startDate, endDate}` | 201 `{cycle}` | 400, 401, 403, 409 |
| GET | /api/cycles/:id | Yes | Get | -- | 200 `{cycle}` | 401, 404 |
| PUT | /api/cycles/:id | Yes (ADMIN,OPS) | Update | `{name?, startDate?, endDate?}` | 200 `{cycle}` | 400, 401, 403, 404 |
| POST | /api/cycles/:id/activate | Yes (ADMIN,OPS) | DRAFT->ACTIVE | -- | 200 `{cycle}` | 401, 403, 404, 409 |
| POST | /api/cycles/:id/close | Yes (ADMIN,OPS) | ACTIVE->CLOSED | -- | 200 `{cycle}` | 401, 403, 404, 409 |

### Scorecard Templates & Metrics

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/cycles/:id/templates | Yes | List templates | -- | 200 `{data}` | 401, 404 |
| POST | /api/cycles/:id/templates | Yes (ADMIN,OPS) | Create template | `{name, type}` | 201 `{template}` | 400, 401, 403, 404 |
| POST | /api/templates/:id/metrics | Yes (ADMIN,OPS) | Add metric | `{name, weight, maxScore?, description?}` | 201 `{metric}` | 400, 401, 403 |
| PUT | /api/templates/:id/metrics/:mid | Yes (ADMIN,OPS) | Update metric | `{name?, weight?, maxScore?}` | 200 `{metric}` | 400, 401, 403, 404 |
| DELETE | /api/templates/:id/metrics/:mid | Yes (ADMIN,OPS) | Remove metric | -- | 204 | 401, 403, 404 |

### Scorecards

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/scorecards | Yes | List | -- | 200 `{data, total, page, pageSize}` | 401 |
| POST | /api/scorecards | Yes (ADMIN,OPS) | Create | `{cycleId, templateId, evaluateeId, evaluatorId}` | 201 `{scorecard}` | 400, 401, 403, 404 |
| GET | /api/scorecards/:id | Yes | Get with responses | -- | 200 `{scorecard, responses:[]}` | 401, 404 |
| PUT | /api/scorecards/:id/responses | Yes (own evaluator) | Save responses | `{responses:[{metricId, score, comments?}]}` | 200 `{scorecard}` | 400, 401, 403, 404 |
| POST | /api/scorecards/:id/submit | Yes (own evaluator) | Submit | -- | 200 `{scorecard}` | 401, 403, 404, 409 |
| POST | /api/scorecards/:id/recuse | Yes (own evaluator) | Recuse | `{reason}` | 200 `{scorecard}` | 400, 401, 403, 404 |

### Reviews

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/reviews | Yes | List | -- | 200 `{data, total}` | 401 |
| GET | /api/reviews/:id | Yes | Get | -- | 200 `{review}` | 401, 404 |
| POST | /api/reviews/:id/approve | Yes (REVIEWER,ADMIN) | Approve | `{comments?}` | 200 `{review}` | 401, 403, 404 |
| POST | /api/reviews/:id/reject | Yes (REVIEWER,ADMIN) | Reject | `{comments}` | 200 `{review}` | 400, 401, 403, 404 |
| POST | /api/reviews/:id/flag-conflict | Yes (REVIEWER) | Flag conflict | `{reason}` | 200 `{review}` | 400, 401, 403, 404 |
| POST | /api/reviews/:id/assign-second | Yes (ADMIN) | Assign second reviewer | `{reviewerId}` | 200 `{review}` | 400 same reviewer, 401, 403, 404 |

### Appeals

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/appeals | Yes | List | -- | 200 `{data, total}` | 401 |
| POST | /api/appeals | Yes (evaluatee) | File appeal | `{scorecardId, reason}` | 201 `{appeal}` | 400 past deadline, 401, 403, 404, 409 |
| GET | /api/appeals/:id | Yes | Get | -- | 200 `{appeal}` | 401, 404 |
| POST | /api/appeals/:id/resolve | Yes (REVIEWER,ADMIN) | Resolve | `{resolutionNotes}` | 200 `{appeal}` | 400, 401, 403, 404 |
| POST | /api/appeals/:id/reject | Yes (REVIEWER,ADMIN) | Reject appeal | `{resolutionNotes}` | 200 `{appeal}` | 400, 401, 403, 404 |

### Beds

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/bed-buildings | Yes | List buildings | -- | 200 `{data, total}` | 401 |
| POST | /api/bed-buildings | Yes (ADMIN,OPS) | Create building | `{name, address?, serviceAreaId?}` | 201 `{building}` | 400, 401, 403 |
| GET | /api/bed-buildings/:id/rooms | Yes | List rooms | -- | 200 `{data}` | 401, 404 |
| POST | /api/bed-buildings/:id/rooms | Yes (ADMIN,OPS) | Create room | `{roomNumber, floor?, roomType?}` | 201 `{room}` | 400, 401, 403, 409 |
| GET | /api/rooms/:id/beds | Yes | List beds in room | -- | 200 `{data}` | 401, 404 |
| POST | /api/rooms/:id/beds | Yes (ADMIN,OPS) | Create bed | `{bedLabel}` | 201 `{bed}` | 400, 401, 403, 409 |
| GET | /api/beds | Yes | List all beds | -- | 200 `{data, total, page, pageSize}` | 401 |
| GET | /api/beds/:id | Yes | Get bed | -- | 200 `{bed, room, building}` | 401, 404 |
| POST | /api/beds/:id/transition | Yes (ADMIN,OPS) | State transition | `{toState, residentId?, reason?, notes?}` | 200 `{bed, historyRecord}` | 400, 401, 403, 404 |
| GET | /api/beds/:id/history | Yes | State history | -- | 200 `{data}` | 401, 404 |

### Route Imports

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/route-imports | Yes | List imports | -- | 200 `{data, total}` | 401 |
| POST | /api/route-imports | Yes (ADMIN,OPS) | Upload file | `multipart/form-data {file}` | 202 `{importJob}` | 400 format, 401, 403 |
| GET | /api/route-imports/:id | Yes | Get import status | -- | 200 `{import}` | 401, 404 |
| GET | /api/route-imports/:id/checkpoints | Yes | List checkpoints | -- | 200 `{data, total}` coords masked | 401, 404 |

### KPI

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| GET | /api/kpis | Yes | List definitions | -- | 200 `{data, total}` | 401 |
| POST | /api/kpis | Yes (ADMIN) | Create definition | `{name, unit?, category, formula?, description?}` | 201 `{kpi}` | 400, 401, 403, 409 |
| GET | /api/kpis/:id | Yes | Get definition | -- | 200 `{kpi}` | 401, 404 |
| PUT | /api/kpis/:id | Yes (ADMIN) | Update | `{name?, unit?, category?, formula?, isActive?}` | 200 `{kpi}` | 400, 401, 403, 404 |
| GET | /api/kpi-scores | Yes | List scores | `?kpiId=&serviceAreaId=&from=&to=` | 200 `{data, total}` | 401 |
| POST | /api/kpi-scores | Yes (ADMIN,OPS) | Record score | `{kpiId, value, scoreDate, serviceAreaId?, cycleId?, notes?}` | 201 `{score}` | 400, 401, 403, 404 |

### Exports & System

| Method | Path | Auth | Description | Request Body | Success | Errors |
|--------|------|------|-------------|-------------|---------|--------|
| POST | /api/exports | Yes | Create export job | `{type, entityType, destinationPath, filtersJson?}` | 202 `{exportJob}` | 400, 401, 403 no write perm |
| GET | /api/exports/:id | Yes | Get job status | -- | 200 `{exportJob}` | 401, 404 |
| GET | /api/audit-trail | Yes (ADMIN,AUDITOR) | Query audit trail | `?entityType=&entityId=&from=&to=` | 200 `{data, total}` | 401, 403 |
| GET | /api/logs | Yes (ADMIN,AUDITOR) | Query system logs | `?level=&category=&from=&to=` | 200 `{data, total}` | 401, 403 |
| GET | /api/jobs | Yes (ADMIN) | List scheduled jobs | -- | 200 `{data}` | 401, 403 |
| POST | /api/jobs/:id/pause | Yes (ADMIN) | Pause job | -- | 200 `{job}` | 401, 403, 404 |
| POST | /api/jobs/:id/resume | Yes (ADMIN) | Resume job | -- | 200 `{job}` | 401, 403, 404 |
| GET | /api/geozones | Yes | List geozones | -- | 200 `{data, total}` | 401 |
| POST | /api/geozones | Yes (ADMIN) | Create geozone | `{name, zipCodes, streetRanges?}` | 201 `{geozone}` | 400, 401, 403 |
| PUT | /api/geozones/:id | Yes (ADMIN) | Update geozone | `{name?, zipCodes?, streetRanges?}` | 200 `{geozone}` | 400, 401, 403, 404 |

---

## Validation Rules

| Field | Location | Rules |
|-------|----------|-------|
| username | create user | required, 3-50 chars, alphanumeric + underscore only, unique |
| password | create user | required, 8-128 chars |
| displayName | create/update user | required, 1-100 chars, trimmed |
| role | create/update user | required, one of the 5 defined role values |
| staffId | create/update user | required, 1-50 chars (stored encrypted) |
| community.name | create/update | required, 1-100 chars, trimmed, unique |
| serviceArea.name | create/update | required, 1-100 chars, trimmed, unique within community |
| pickupPoint.address | create/update | required, 1-255 chars (stored encrypted) |
| pickupPoint.zipCode | create/update | required, matches `^\d{5}(-\d{4})?$` |
| pickupPoint.capacity | create/update | required, integer >= 1 |
| pickupPoint.hoursJson | create/update | required, valid JSON, at least one weekday key, values match `HH:MM-HH:MM` |
| pickupPoint.pausedUntil | pause | required, ISO-8601 datetime, must be in the future |
| pickupPoint.pauseReason | pause | required, 1-500 chars |
| cycle.name | create/update | required, 1-100 chars, trimmed, unique |
| cycle.startDate / endDate | create/update | required, ISO-8601 date, endDate must be after startDate |
| metric.weight | add/update | required, > 0; sum of all weights per template must equal exactly 100.0 |
| metric.maxScore | add/update | optional, > 0, default 5.0 |
| scorecard_response.score | submit | required, numeric, 0 <= score <= metric.maxScore |
| review.recusalReason | flag conflict | required, 1-1000 chars |
| appeal.reason | file | required, 10-2000 chars |
| appeal deadline | file | scorecardId.submitted_at + 7 calendar days must not have passed |
| bed.bedLabel | create | required, 1-20 chars, unique within room |
| bed.toState | transition | must be an allowed transition per state machine table |
| bed.residentId | AVAILABLE->OCCUPIED | required when moving to OCCUPIED |
| route file columns | upload CSV | required: checkpoint_name (text), expected_at (ISO-8601), actual_at (ISO-8601 or empty), lat (numeric -90..90), lon (numeric -180..180) |
| kpi.name | create/update | required, 1-100 chars, unique |
| kpi.category | create/update | required, 1-50 chars |
| kpiScore.value | record | required, numeric |
| kpiScore.scoreDate | record | required, ISO-8601 date |
| export.destinationPath | create | required; server verifies write permission before accepting |
| export.type | create | required, one of EXCEL, PDF, CSV |
| page | all list endpoints | integer >= 1, default 1 |
| pageSize | all list endpoints | integer 1-500, default 50 |

---

## Frontend Pages & States

All windows are separate `javafx.stage.Stage` instances managed by `WindowManager`. Each window registers a `KeyEventFilter` at the scene level for global shortcuts.

```
LoginDialog (modal, shown on startup)
  States: default, submitting (fields + button disabled), auth error inline
  Actions: submit -> AuthService.login() -> on success open MainWindow

MainWindow
  Toolbar: [KPI Reviews] [Pickup Points] [Bed Board] [Reports] [Health] [Inbox(N)]
  System tray icon: Open, Lock, Exit menu
  Inactivity timer: resets on any mouse/key event; fires LockScreen after 600 seconds
  NotificationInbox: slide-in panel showing alerts (severity, message, entity ref, timestamp, dismiss)

LockScreen (overlay covering all open windows)
  States: locked (username shown, password field), unlocking (spinner), auth error
  Actions: correct password -> dismiss overlay and restore prior window state

KpiReviewWindow (opened from toolbar)
  Data: GET /api/kpis + GET /api/kpi-scores on open
  States: loading spinner, error-with-retry, populated table, empty state
  Table columns: KPI Name, Category, Date, Service Area, Value, Unit
  Filters: date range picker, service area dropdown, category dropdown
  Ctrl+N: NewKpiScoreDialog
  Ctrl+E: ExportDialog (pre-selected entity type = KPI_SCORES)
  Ctrl+F: focus search/filter field
  Right-click row: Open Audit Trail

PickupPointWindow (opened from toolbar)
  Data: GET /api/pickup-points + GET /api/communities on open
  States: loading, error, populated table, empty state
  Table columns: Community, Address (masked), ZIP, Hours, Capacity, Status, Paused Until
  Ctrl+N: NewPickupPointDialog
  Ctrl+E: ExportDialog
  Ctrl+F: filter by community/ZIP/status
  Right-click row: Pause Service | Resume Service | Open Audit Trail | Assign Leader

BedBoardWindow (opened from toolbar)
  Data: GET /api/beds + GET /api/bed-buildings on open; auto-refresh every 30s
  States: loading, error, grid view color-coded by state
  Color coding: AVAILABLE=green, OCCUPIED=blue, RESERVED=yellow,
                CLEANING=orange, MAINTENANCE=gray, OUT_OF_SERVICE=red
  Filters: building dropdown, floor picker, state multi-select checkbox
  Ctrl+N: AddBedDialog
  Ctrl+F: filter by bed label
  Right-click bed cell: Transfer Bed (transition dialog) | Open Audit Trail

EvaluationWindow (opened from toolbar or menu)
  Data: GET /api/cycles + GET /api/scorecards on open
  States: loading, empty (no active cycle), populated
  Tabs: [Cycles] [Scorecards] [Reviews] [Appeals]
  Ctrl+N: create new cycle (in Cycles tab) or new scorecard (in Scorecards tab)
  Right-click scorecard: Recuse | Submit | Open Audit Trail

ReportsWindow (opened from toolbar)
  Report types: KPI Summary, Evaluation Results, Bed Occupancy, Pickup Utilization
  States: configure form, generating (progress bar), complete (open file, copy sha256)
  Ctrl+E: trigger export with current configuration

HealthPanelWindow (opened from toolbar)
  Data: GET /api/health every 10s
  Shows: DB status, API server port + uptime, last backup, job list with status/next run
  Job table: name, cron, last run, next run, status, [Pause] [Resume] buttons
```

---

## Error Handling Strategy

- **API layer**: Every route handler wrapped in `try/catch`; uncaught exceptions return `500 {error:{code:"INTERNAL_ERROR", message:"An unexpected error occurred"}}`. Stack traces never in responses.
- **Validation**: Custom validators applied before business logic. Errors return `400 {error:{code:"VALIDATION_ERROR", fields:{fieldName:"message"}}}`.
- **Auth**: Missing/expired token -> `401 {error:{code:"UNAUTHORIZED"}}`. Valid token, wrong role -> `403 {error:{code:"FORBIDDEN"}}`. Protected resource existence never leaked to unauthorized callers.
- **Business rule violations** (state machine, active-per-day, weight sum): `409 {error:{code:"CONFLICT", message:"<human-readable description>"}}`.
- **Database**: SQLite constraint violations caught; unique violation -> 409; FK violation on create (parent missing) -> 404; FK violation on delete (has dependents) -> 409.
- **Frontend**: Every async call has loading / success / error state in controller; errors show inline red label; server errors show inbox alert.
- **Job failures**: Scheduler catches exceptions, rolls back open transactions, sets job `status=FAILED`, raises inbox alert, logs at ERROR level.

---

## Logging Strategy

- Logger: SLF4J API + Logback with two `RollingFileAppender`s:
  - `logs/business.log` -- category=BUSINESS, JSON lines, daily rotation, 90-day retention
  - `logs/system.log` -- category=SYSTEM, JSON lines, daily rotation, 90-day retention
  - Console appender in development mode only (controlled by `app.properties`)
- Log format JSON fields: `timestamp, level, category, message, traceId, requestId, userId, entityType, entityId, path, statusCode, durationMs`
- NEVER log: password values, raw API tokens, decrypted sensitive field values (staffId, residentId, address).
- DO log:
  - Every API request: method, path, status code, duration, requestId, userId
  - Auth failures: username only (no password), reason
  - All state transitions: entity, from state, to state, userId, traceId
  - All job executions: job type, start, end, record counts, result
  - All exports: type, entity type, destination folder only
  - Validation failures: field names only (no user-supplied values that could contain PII)

---

## Implied Requirements

- GET /api/health returns `{status:"ok"}` without auth so external monitors can probe it.
- All list API responses: `{data:[...], total:N, page:N, pageSize:N}`.
- All timestamps stored and returned as ISO-8601 UTC strings.
- SQLite opened with `PRAGMA journal_mode=WAL` and `PRAGMA foreign_keys=ON` on every connection.
- Flyway runs schema migrations on application startup before any business logic.
- Javalin server starts on application launch and stops on `Stage.setOnCloseRequest` + `Platform.exit`.
- WindowManager prevents multiple instances of the same window type; brings existing instance to front.
- All background threads are daemon threads or explicitly tracked in a shutdown hook.
- HikariCP connection pool (maxPoolSize=5); connections returned to pool on close; no leaked handles.
- Encryption key loaded from OS Windows DPAPI `KeyStore` on first run; generated and stored if absent; application refuses to start if key is inaccessible.
- All UI dialogs validate client-side before API calls; server-side validation is authoritative.
- SHA-256 sidecar file written atomically (write to temp file, then rename) to prevent partial files.
- SQLite `SQLITE_BUSY` handled with exponential back-off, max 3 retries, then surface error.
- The API port is configurable in `app.properties` (default: 8080); the port must be free at startup or the application logs a clear error and exits.

---

## Scope Boundary

- No networked or multi-machine deployment; strictly single-machine.
- No external database; SQLite only.
- No cloud sync or remote backup.
- No email or push notifications; all alerts are on-screen only.
- No OAuth/SSO; username + password only.
- No mobile or web UI; Windows 11 desktop only.
- No real-time collaboration or WebSocket features.
- No map rendering or GIS visualization; geozones are ZIP+street-range text records only.
- No biometric authentication.
- No custom report builder; only the four defined report types.
- No multi-tenant support; single-hospital installation.
- No password-reset flow; admin resets passwords manually.

---

## Project Structure

```
repo/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/eaglepoint/console/
│   │   │   │   ├── App.java                        # JavaFX Application entry point
│   │   │   │   ├── config/
│   │   │   │   │   ├── AppConfig.java              # Load app.properties
│   │   │   │   │   ├── DatabaseConfig.java         # HikariCP + SQLite + Flyway
│   │   │   │   │   ├── SecurityConfig.java         # Encryption key via DPAPI KeyStore
│   │   │   │   │   └── LoggingConfig.java          # Logback programmatic configuration
│   │   │   │   ├── api/
│   │   │   │   │   ├── ApiServer.java              # Javalin, bind 127.0.0.1
│   │   │   │   │   ├── middleware/
│   │   │   │   │   │   ├── AuthMiddleware.java
│   │   │   │   │   │   ├── RateLimiter.java        # 60 req/min per token
│   │   │   │   │   │   └── ErrorHandler.java       # Global exception -> JSON
│   │   │   │   │   └── routes/
│   │   │   │   │       ├── AuthRoutes.java
│   │   │   │   │       ├── UserRoutes.java
│   │   │   │   │       ├── CommunityRoutes.java
│   │   │   │   │       ├── ServiceAreaRoutes.java
│   │   │   │   │       ├── PickupPointRoutes.java
│   │   │   │   │       ├── LeaderAssignmentRoutes.java
│   │   │   │   │       ├── EvaluationRoutes.java
│   │   │   │   │       ├── BedRoutes.java
│   │   │   │   │       ├── RouteImportRoutes.java
│   │   │   │   │       ├── KpiRoutes.java
│   │   │   │   │       ├── ExportRoutes.java
│   │   │   │   │       ├── GeozoneRoutes.java
│   │   │   │   │       └── SystemRoutes.java
│   │   │   │   ├── model/
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── ApiToken.java
│   │   │   │   │   ├── Community.java
│   │   │   │   │   ├── Geozone.java
│   │   │   │   │   ├── ServiceArea.java
│   │   │   │   │   ├── PickupPoint.java
│   │   │   │   │   ├── LeaderAssignment.java
│   │   │   │   │   ├── EvaluationCycle.java
│   │   │   │   │   ├── ScorecardTemplate.java
│   │   │   │   │   ├── ScorecardMetric.java
│   │   │   │   │   ├── Scorecard.java
│   │   │   │   │   ├── ScorecardResponse.java
│   │   │   │   │   ├── Review.java
│   │   │   │   │   ├── Appeal.java
│   │   │   │   │   ├── BedBuilding.java
│   │   │   │   │   ├── BedRoom.java
│   │   │   │   │   ├── Bed.java
│   │   │   │   │   ├── BedState.java               # Enum
│   │   │   │   │   ├── BedStateHistory.java
│   │   │   │   │   ├── RouteImport.java
│   │   │   │   │   ├── RouteCheckpoint.java
│   │   │   │   │   ├── KpiDefinition.java
│   │   │   │   │   ├── KpiScore.java
│   │   │   │   │   ├── ExportJob.java
│   │   │   │   │   └── ScheduledJobConfig.java
│   │   │   │   ├── repository/
│   │   │   │   │   ├── BaseRepository.java         # Pagination helper, query builder
│   │   │   │   │   ├── UserRepository.java
│   │   │   │   │   ├── CommunityRepository.java
│   │   │   │   │   ├── GeozoneRepository.java
│   │   │   │   │   ├── ServiceAreaRepository.java
│   │   │   │   │   ├── PickupPointRepository.java
│   │   │   │   │   ├── LeaderAssignmentRepository.java
│   │   │   │   │   ├── EvaluationRepository.java
│   │   │   │   │   ├── BedRepository.java
│   │   │   │   │   ├── RouteImportRepository.java
│   │   │   │   │   ├── KpiRepository.java
│   │   │   │   │   ├── ExportJobRepository.java
│   │   │   │   │   ├── AuditTrailRepository.java
│   │   │   │   │   └── SystemLogRepository.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── AuthService.java
│   │   │   │   │   ├── UserService.java
│   │   │   │   │   ├── CommunityService.java
│   │   │   │   │   ├── GeozoneService.java
│   │   │   │   │   ├── ServiceAreaService.java
│   │   │   │   │   ├── PickupPointService.java
│   │   │   │   │   ├── LeaderAssignmentService.java
│   │   │   │   │   ├── EvaluationService.java
│   │   │   │   │   ├── ReviewService.java
│   │   │   │   │   ├── AppealService.java
│   │   │   │   │   ├── BedService.java
│   │   │   │   │   ├── BedStateMachine.java        # Transition table + validation
│   │   │   │   │   ├── RouteImportService.java
│   │   │   │   │   ├── KpiService.java
│   │   │   │   │   ├── ExportService.java
│   │   │   │   │   ├── AuditService.java
│   │   │   │   │   └── ConsistencyService.java
│   │   │   │   ├── security/
│   │   │   │   │   ├── EncryptionUtil.java         # AES-256-GCM encrypt/decrypt
│   │   │   │   │   ├── PasswordUtil.java           # jBCrypt
│   │   │   │   │   ├── TokenService.java           # Generate + hash + validate
│   │   │   │   │   └── MaskingUtil.java            # Coord masking, field masking
│   │   │   │   ├── export/
│   │   │   │   │   ├── ExcelExporter.java          # Apache POI
│   │   │   │   │   ├── PdfExporter.java            # Apache PDFBox
│   │   │   │   │   ├── CsvExporter.java            # OpenCSV
│   │   │   │   │   └── FingerprintUtil.java        # SHA-256 sidecar
│   │   │   │   ├── scheduler/
│   │   │   │   │   ├── JobScheduler.java           # Quartz setup + registry
│   │   │   │   │   └── jobs/
│   │   │   │   │       ├── BackupJob.java
│   │   │   │   │       ├── ArchivalJob.java
│   │   │   │   │       ├── ConsistencyCheckJob.java
│   │   │   │   │       └── ScheduledReportJob.java
│   │   │   │   └── ui/
│   │   │   │       ├── MainWindow.java
│   │   │   │       ├── WindowManager.java
│   │   │   │       ├── SystemTrayManager.java
│   │   │   │       ├── InactivityMonitor.java      # 600s timer -> LockScreen
│   │   │   │       ├── LockScreenController.java
│   │   │   │       ├── NotificationInbox.java
│   │   │   │       ├── kpi/
│   │   │   │       │   ├── KpiReviewWindow.java
│   │   │   │       │   └── KpiReviewController.java
│   │   │   │       ├── pickup/
│   │   │   │       │   ├── PickupPointWindow.java
│   │   │   │       │   └── PickupPointController.java
│   │   │   │       ├── bed/
│   │   │   │       │   ├── BedBoardWindow.java
│   │   │   │       │   └── BedBoardController.java
│   │   │   │       ├── evaluation/
│   │   │   │       │   ├── EvaluationWindow.java
│   │   │   │       │   └── EvaluationController.java
│   │   │   │       ├── reports/
│   │   │   │       │   ├── ReportsWindow.java
│   │   │   │       │   └── ReportsController.java
│   │   │   │       └── shared/
│   │   │   │           ├── ContextMenuFactory.java
│   │   │   │           ├── AuditTrailDialog.java
│   │   │   │           ├── ExportDialog.java
│   │   │   │           └── HealthPanelWindow.java
│   │   │   └── resources/
│   │   │       ├── db/migrations/
│   │   │       │   ├── V1__initial_schema.sql
│   │   │       │   └── V2__seed_admin_user.sql
│   │   │       ├── fxml/
│   │   │       │   ├── main.fxml
│   │   │       │   ├── lock-screen.fxml
│   │   │       │   ├── kpi-review.fxml
│   │   │       │   ├── pickup-points.fxml
│   │   │       │   ├── bed-board.fxml
│   │   │       │   ├── evaluation.fxml
│   │   │       │   └── reports.fxml
│   │   │       ├── css/application.css
│   │   │       └── app.properties
│   │   └── test/
│   │       └── java/com/eaglepoint/console/
│   │           ├── unit/
│   │           │   ├── service/
│   │           │   │   ├── AuthServiceTest.java
│   │           │   │   ├── BedStateMachineTest.java
│   │           │   │   ├── EvaluationServiceTest.java
│   │           │   │   ├── PickupPointServiceTest.java
│   │           │   │   ├── AppealServiceTest.java
│   │           │   │   └── RouteImportServiceTest.java
│   │           │   └── security/
│   │           │       ├── EncryptionUtilTest.java
│   │           │       └── MaskingUtilTest.java
│   │           └── integration/
│   │               ├── BaseIntegrationTest.java    # start Javalin + in-memory SQLite
│   │               ├── AuthApiTest.java
│   │               ├── CommunityApiTest.java
│   │               ├── PickupPointApiTest.java
│   │               ├── BedApiTest.java
│   │               ├── EvaluationApiTest.java
│   │               └── RouteImportApiTest.java
│   ├── Dockerfile
│   ├── pom.xml
│   └── .dockerignore
├── docker-compose.yml
├── run_tests.sh
└── README.md
```

---

## Test Coverage Plan

### Unit Tests (`backend/src/test/java/.../unit/`)

**AuthServiceTest**
1. `login_validCredentials_returnsUserAndToken`
2. `login_wrongPassword_throwsUnauthorized`
3. `login_inactiveUser_throwsUnauthorized`
4. `login_unknownUser_throwsUnauthorized`
5. `createUser_duplicateUsername_throwsConflict`
6. `createUser_passwordStoredAsHash_notPlaintext`

**BedStateMachineTest**
7. `transition_availableToOccupied_allowed`
8. `transition_occupiedToOutOfService_forbidden`
9. `transition_occupiedToAvailable_allowed`
10. `transition_cleaningToAvailable_allowed`
11. `transition_maintenanceToOutOfService_allowed`
12. `transition_outOfServiceToMaintenance_allowed`
13. `transition_outOfServiceToAvailable_forbidden`
14. `transition_allDefinedAllowedPaths_succeed`
15. `transition_undefinedPath_throws`

**EvaluationServiceTest**
16. `addMetric_weightsSumExactly100_accepted`
17. `addMetric_weightsSumExceeds100_throws`
18. `submitScorecard_allMetricsAnswered_succeeds`
19. `submitScorecard_missingMetricResponse_throws`
20. `fileAppeal_within7Days_accepted`
21. `fileAppeal_after7Days_throws`
22. `fileAppeal_duplicate_throws`
23. `recuseReview_setsRecusedStatusAndReason`
24. `assignSecondReviewer_sameAsOriginal_throws`

**PickupPointServiceTest**
25. `activate_noOtherActiveToday_succeeds`
26. `activate_anotherActiveForSameCommunityToday_throws`
27. `pause_setsPausedUntilAndReason`
28. `matchByZipAndStreetRange_returnsCorrectPoint`
29. `match_noGeozoneMatch_fallsBackToZipOnly`
30. `match_manualOverride_returnsOverriddenPoint`

**RouteImportServiceTest**
31. `validateCsv_validFile_noErrors`
32. `validateCsv_missingColumn_returnsLineError`
33. `validateCsv_outOfRangeLatitude_returnsLineError`
34. `processCheckpoint_deviationOver0Point5Miles_setsDeviationAlert`
35. `processCheckpoint_latenessOver15Min_setsMissedAlert`
36. `processCheckpoint_coordsMaskedTo0Point1MileGrid`
37. `resumeImport_fromCheckpoint_skipsAlreadyProcessedRows`

**EncryptionUtilTest**
38. `encrypt_thenDecrypt_returnsOriginal`
39. `encryptTwice_differentCiphertext` (GCM nonce uniqueness)
40. `maskCoordinate_roundedToGrid_withinTolerance`

### Integration Tests (`backend/src/test/java/.../integration/`)

Tests start a Javalin server backed by in-memory SQLite (`:memory:`) with Flyway migrations. Each test class resets the database in `@BeforeEach`.

**AuthApiTest**
1. `GET /api/health` -> 200, body.status == "ok"
2. `POST /api/auth/login` valid -> 200, body has token, user.id, user.role
3. `POST /api/auth/login` wrong password -> 401, body.error.code present
4. `POST /api/auth/login` missing username -> 400, body.error.fields.username present
5. `GET /api/auth/me` with token -> 200, body.username matches
6. `GET /api/auth/me` without token -> 401

**CommunityApiTest**
7. `GET /api/communities` without token -> 401
8. `POST /api/communities` valid -> 201, body has id, name, createdAt
9. `POST /api/communities` duplicate name -> 409
10. `POST /api/communities` missing name -> 400 with field error
11. `GET /api/communities` -> 200, total >= 1, data is array
12. `PUT /api/communities/:id` valid -> 200, updated name matches
13. `DELETE /api/communities/:id` -> 204
14. `GET /api/communities/:id` after delete -> 404

**PickupPointApiTest**
15. `POST /api/pickup-points` valid -> 201
16. `POST /api/pickup-points` second active for same community same day -> 409
17. `POST /api/pickup-points/:id/pause` valid -> 200, status == PAUSED
18. `POST /api/pickup-points/:id/pause` pausedUntil in the past -> 400
19. `POST /api/pickup-points/:id/resume` -> 200, status == ACTIVE
20. `POST /api/pickup-points/match` by ZIP and street range -> 200, correct point returned

**BedApiTest**
21. `POST /api/beds/:id/transition` AVAILABLE -> OCCUPIED with residentId -> 200
22. `POST /api/beds/:id/transition` OCCUPIED -> OUT_OF_SERVICE -> 400 (forbidden transition)
23. `POST /api/beds/:id/transition` OCCUPIED -> AVAILABLE (checkout) -> 200
24. `GET /api/beds/:id/history` after two transitions -> 200, data length == 2
25. `GET /api/beds?state=AVAILABLE` -> 200, all returned beds have state AVAILABLE

**EvaluationApiTest**
26. `POST /api/cycles` valid -> 201
27. `POST /api/cycles/:id/activate` from DRAFT -> 200, status == ACTIVE
28. `POST /api/templates/:id/metrics` total weight reaches 100 exactly -> 201
29. `POST /api/templates/:id/metrics` total weight would exceed 100 -> 400
30. `POST /api/scorecards/:id/submit` -> 200, status == SUBMITTED
31. `POST /api/appeals` within 7 days of submission -> 201
32. `POST /api/appeals` after 7-day window -> 400
33. `POST /api/reviews/:id/flag-conflict` -> 200, conflict_flagged == 1

**RouteImportApiTest**
34. `POST /api/route-imports` valid CSV -> 202, status == PROCESSING
35. `POST /api/route-imports` CSV missing required column -> 400 with line-level errors
36. `GET /api/route-imports/:id/checkpoints` -> 200, coords differ from raw input (masked)
37. Import with deviation > 0.5 miles -> checkpoint has is_deviation_alert == 1
38. Import with lateness > 15 min -> checkpoint has is_missed_alert == 1

**RateLimiterTest**
39. 61st request within 1 minute with same token -> 429

### run_tests.sh
```bash
#!/usr/bin/env bash
set -e
echo "Building and starting test containers..."
docker compose -f docker-compose.yml up -d --build backend-test

echo "Waiting for health endpoint..."
until curl -sf "http://127.0.0.1:${API_PORT:-8080}/api/health"; do
  sleep 2
done

echo "Running tests..."
docker compose exec backend-test mvn test -pl backend --no-transfer-progress

echo "Tearing down..."
docker compose down
echo "Done."
```
