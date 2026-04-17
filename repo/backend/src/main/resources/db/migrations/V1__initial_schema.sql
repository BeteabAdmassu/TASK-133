-- SQLite; all TEXT datetimes are ISO-8601 UTC strings.
-- Sensitive fields marked _encrypted are AES-256-GCM ciphertext stored as Base64.

CREATE TABLE IF NOT EXISTS users (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  username          TEXT NOT NULL UNIQUE,
  password_hash     TEXT NOT NULL,
  display_name      TEXT NOT NULL,
  role              TEXT NOT NULL CHECK (role IN ('SYSTEM_ADMIN','OPS_MANAGER','REVIEWER','AUDITOR','DATA_INTEGRATOR')),
  staff_id_encrypted TEXT NOT NULL,
  is_active         INTEGER NOT NULL DEFAULT 1,
  last_login        TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_tokens (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id           INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash        TEXT NOT NULL UNIQUE,
  expires_at        TEXT NOT NULL,
  created_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_api_tokens_hash ON api_tokens(token_hash);

CREATE TABLE IF NOT EXISTS communities (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  description       TEXT,
  status            TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS geozones (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  zip_codes         TEXT NOT NULL,
  street_ranges     TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS service_areas (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  community_id      INTEGER NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  name              TEXT NOT NULL,
  description       TEXT,
  status            TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(community_id, name)
);
CREATE INDEX IF NOT EXISTS idx_service_areas_community ON service_areas(community_id);

CREATE TABLE IF NOT EXISTS pickup_points (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  community_id          INTEGER NOT NULL REFERENCES communities(id) ON DELETE RESTRICT,
  service_area_id       INTEGER REFERENCES service_areas(id) ON DELETE SET NULL,
  geozone_id            INTEGER REFERENCES geozones(id) ON DELETE SET NULL,
  address_encrypted     TEXT NOT NULL,
  zip_code              TEXT NOT NULL,
  street_range_start    TEXT,
  street_range_end      TEXT,
  hours_json            TEXT NOT NULL,
  capacity              INTEGER NOT NULL DEFAULT 1 CHECK (capacity >= 1),
  status                TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAUSED','INACTIVE')),
  paused_until          TEXT,
  pause_reason          TEXT,
  manual_override       INTEGER NOT NULL DEFAULT 0,
  override_notes        TEXT,
  created_at            TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at            TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_pickup_points_community ON pickup_points(community_id);
CREATE INDEX IF NOT EXISTS idx_pickup_points_status    ON pickup_points(status);

CREATE TABLE IF NOT EXISTS leader_assignments (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  service_area_id   INTEGER NOT NULL REFERENCES service_areas(id) ON DELETE CASCADE,
  user_id           INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  assigned_by       INTEGER NOT NULL REFERENCES users(id),
  assigned_at       TEXT NOT NULL DEFAULT (datetime('now')),
  unassigned_at     TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_leader_assignments_area ON leader_assignments(service_area_id);

CREATE TABLE IF NOT EXISTS evaluation_cycles (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  start_date        TEXT NOT NULL,
  end_date          TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','CLOSED','ARCHIVED')),
  created_by        INTEGER NOT NULL REFERENCES users(id),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS scorecard_templates (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  cycle_id          INTEGER NOT NULL REFERENCES evaluation_cycles(id) ON DELETE CASCADE,
  name              TEXT NOT NULL,
  type              TEXT NOT NULL CHECK (type IN ('SELF','PEER','EXPERT')),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(cycle_id, name)
);

CREATE TABLE IF NOT EXISTS scorecard_metrics (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  template_id       INTEGER NOT NULL REFERENCES scorecard_templates(id) ON DELETE CASCADE,
  name              TEXT NOT NULL,
  description       TEXT,
  weight            REAL NOT NULL CHECK (weight > 0),
  max_score         REAL NOT NULL DEFAULT 5.0 CHECK (max_score > 0),
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_metrics_template ON scorecard_metrics(template_id);

CREATE TABLE IF NOT EXISTS scorecards (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  cycle_id          INTEGER NOT NULL REFERENCES evaluation_cycles(id) ON DELETE CASCADE,
  template_id       INTEGER NOT NULL REFERENCES scorecard_templates(id),
  evaluatee_id      INTEGER NOT NULL REFERENCES users(id),
  evaluator_id      INTEGER NOT NULL REFERENCES users(id),
  type              TEXT NOT NULL CHECK (type IN ('SELF','PEER','EXPERT')),
  status            TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','IN_PROGRESS','SUBMITTED','APPROVED','RECUSED','APPEALED')),
  submitted_at      TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_scorecards_cycle     ON scorecards(cycle_id);
CREATE INDEX IF NOT EXISTS idx_scorecards_evaluatee ON scorecards(evaluatee_id);
CREATE INDEX IF NOT EXISTS idx_scorecards_evaluator ON scorecards(evaluator_id);

CREATE TABLE IF NOT EXISTS scorecard_responses (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  scorecard_id      INTEGER NOT NULL REFERENCES scorecards(id) ON DELETE CASCADE,
  metric_id         INTEGER NOT NULL REFERENCES scorecard_metrics(id),
  score             REAL NOT NULL,
  comments          TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(scorecard_id, metric_id)
);

CREATE TABLE IF NOT EXISTS reviews (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  scorecard_id          INTEGER NOT NULL REFERENCES scorecards(id) ON DELETE CASCADE,
  reviewer_id           INTEGER NOT NULL REFERENCES users(id),
  second_reviewer_id    INTEGER REFERENCES users(id),
  status                TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','IN_REVIEW','APPROVED','REJECTED','RECUSED')),
  conflict_flagged      INTEGER NOT NULL DEFAULT 0,
  recusal_reason        TEXT,
  recused_at            TEXT,
  reviewed_at           TEXT,
  comments              TEXT,
  created_at            TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at            TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_reviews_scorecard ON reviews(scorecard_id);

CREATE TABLE IF NOT EXISTS appeals (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  scorecard_id      INTEGER NOT NULL REFERENCES scorecards(id) ON DELETE CASCADE,
  filed_by          INTEGER NOT NULL REFERENCES users(id),
  filed_at          TEXT NOT NULL DEFAULT (datetime('now')),
  deadline          TEXT NOT NULL,
  reason            TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','UNDER_REVIEW','RESOLVED','REJECTED','EXPIRED')),
  resolved_at       TEXT,
  resolution_notes  TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_appeals_scorecard ON appeals(scorecard_id);

CREATE TABLE IF NOT EXISTS bed_buildings (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  name              TEXT NOT NULL UNIQUE,
  address           TEXT,
  service_area_id   INTEGER REFERENCES service_areas(id) ON DELETE SET NULL,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS bed_rooms (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  building_id       INTEGER NOT NULL REFERENCES bed_buildings(id) ON DELETE CASCADE,
  room_number       TEXT NOT NULL,
  floor             INTEGER,
  room_type         TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(building_id, room_number)
);
CREATE INDEX IF NOT EXISTS idx_bed_rooms_building ON bed_rooms(building_id);

CREATE TABLE IF NOT EXISTS beds (
  id                      INTEGER PRIMARY KEY AUTOINCREMENT,
  room_id                 INTEGER NOT NULL REFERENCES bed_rooms(id) ON DELETE CASCADE,
  bed_label               TEXT NOT NULL,
  state                   TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (state IN ('AVAILABLE','OCCUPIED','RESERVED','CLEANING','OUT_OF_SERVICE','MAINTENANCE')),
  resident_id_encrypted   TEXT,
  admitted_at             TEXT,
  created_at              TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at              TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(room_id, bed_label)
);
CREATE INDEX IF NOT EXISTS idx_beds_room  ON beds(room_id);
CREATE INDEX IF NOT EXISTS idx_beds_state ON beds(state);

CREATE TABLE IF NOT EXISTS bed_state_history (
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
CREATE INDEX IF NOT EXISTS idx_bed_history_bed ON bed_state_history(bed_id);

CREATE TABLE IF NOT EXISTS route_imports (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  filename          TEXT NOT NULL,
  imported_by       INTEGER NOT NULL REFERENCES users(id),
  imported_at       TEXT NOT NULL DEFAULT (datetime('now')),
  status            TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','VALIDATING','VALID','INVALID','PROCESSING','COMPLETED','FAILED')),
  record_count      INTEGER,
  error_count       INTEGER DEFAULT 0,
  checkpoint_path   TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS route_checkpoints (
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
  status              TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ON_TIME','DEVIATED','MISSED')),
  created_at          TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_route_checkpoints_import ON route_checkpoints(import_id);

CREATE TABLE IF NOT EXISTS kpi_definitions (
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

CREATE TABLE IF NOT EXISTS kpi_scores (
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
CREATE INDEX IF NOT EXISTS idx_kpi_scores_kpi          ON kpi_scores(kpi_id);
CREATE INDEX IF NOT EXISTS idx_kpi_scores_date         ON kpi_scores(score_date);
CREATE INDEX IF NOT EXISTS idx_kpi_scores_service_area ON kpi_scores(service_area_id);

CREATE TABLE IF NOT EXISTS export_jobs (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  type              TEXT NOT NULL CHECK (type IN ('EXCEL','PDF','CSV')),
  entity_type       TEXT NOT NULL,
  filters_json      TEXT,
  destination_path  TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
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

CREATE TABLE IF NOT EXISTS scheduled_jobs (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  job_type          TEXT NOT NULL CHECK (job_type IN ('BACKUP','ARCHIVE','CONSISTENCY_CHECK','REPORT')),
  cron_expression   TEXT NOT NULL,
  timeout_seconds   INTEGER NOT NULL DEFAULT 3600,
  last_run          TEXT,
  next_run          TEXT NOT NULL DEFAULT (datetime('now')),
  status            TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAUSED','FAILED')),
  last_result       TEXT,
  config_json       TEXT,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS audit_trail (
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
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_trail(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_user   ON audit_trail(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_trace  ON audit_trail(trace_id);

CREATE TABLE IF NOT EXISTS system_logs (
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
CREATE INDEX IF NOT EXISTS idx_logs_level    ON system_logs(level);
CREATE INDEX IF NOT EXISTS idx_logs_category ON system_logs(category);
CREATE INDEX IF NOT EXISTS idx_logs_created  ON system_logs(created_at);

-- Archive tables
CREATE TABLE IF NOT EXISTS evaluation_cycles_archive (
  id          INTEGER PRIMARY KEY,
  name        TEXT,
  start_date  TEXT,
  end_date    TEXT,
  status      TEXT,
  created_by  INTEGER,
  created_at  TEXT,
  updated_at  TEXT,
  archived_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS scorecards_archive (
  id           INTEGER PRIMARY KEY,
  cycle_id     INTEGER,
  template_id  INTEGER,
  evaluatee_id INTEGER,
  evaluator_id INTEGER,
  type         TEXT,
  status       TEXT,
  submitted_at TEXT,
  created_at   TEXT,
  updated_at   TEXT,
  archived_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
