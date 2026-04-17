-- Seed admin user (password: Admin1234!)
-- BCrypt hash of "Admin1234!" with cost 12
INSERT OR IGNORE INTO users (username, password_hash, display_name, role, staff_id_encrypted, is_active)
VALUES (
  'admin',
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewEPD0PNKkNM0EAi',
  'System Administrator',
  'SYSTEM_ADMIN',
  'SEED',
  1
);

-- Default scheduled jobs
INSERT OR IGNORE INTO scheduled_jobs (job_type, cron_expression, timeout_seconds, status, next_run)
VALUES
  ('BACKUP',            '0 0 2 * * ?',   1800, 'ACTIVE', datetime('now', '+1 day')),
  ('ARCHIVE',           '0 0 3 1 * ?',   3600, 'ACTIVE', datetime('now', '+1 month')),
  ('CONSISTENCY_CHECK', '0 0 4 * * ?',    900, 'ACTIVE', datetime('now', '+1 day'));
