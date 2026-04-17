-- Crash-safe, signature-verified offline update history.
--
-- Each row records one apply / rollback event for the offline .msi-style
-- package workflow.  The "current" row for a given installation is the
-- latest INSTALLED row that has not been superseded by a SUPERSEDED /
-- ROLLED_BACK row pointing at the same package version.  The table is
-- append-only at the service layer so the full audit trail is preserved.

CREATE TABLE IF NOT EXISTS update_history (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  package_name       TEXT    NOT NULL,
  from_version       TEXT,
  to_version         TEXT    NOT NULL,
  action             TEXT    NOT NULL CHECK (action IN ('DISCOVERED','VERIFIED','INSTALLED','FAILED','ROLLED_BACK','SUPERSEDED')),
  status             TEXT    NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED')),
  sha256_hash        TEXT,
  signature_status   TEXT    CHECK (signature_status IN ('VALID','INVALID','UNTRUSTED','NOT_VERIFIED')),
  installed_path     TEXT,
  backup_path        TEXT,
  error_message      TEXT,
  initiated_by       INTEGER REFERENCES users(id),
  occurred_at        TEXT    NOT NULL DEFAULT (datetime('now')),
  notes              TEXT
);
CREATE INDEX IF NOT EXISTS idx_update_history_version ON update_history(to_version);
CREATE INDEX IF NOT EXISTS idx_update_history_action  ON update_history(action);
CREATE INDEX IF NOT EXISTS idx_update_history_time    ON update_history(occurred_at);
