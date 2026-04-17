-- Seed demo users for every role used by authorization logic.
-- BCrypt hashes generated with org.mindrot:jbcrypt:0.4 at cost=12 and verified.
-- Documented passwords (see README > Demo Credentials) — do not change without regenerating hashes.

INSERT OR IGNORE INTO users (username, password_hash, display_name, role, staff_id_encrypted, is_active)
VALUES
  ('admin',           '$2a$12$g9JblFv8QrTN/2VP2SOA/OFwv8AQqx1/VSMacPvXZfqmuRTAXpofa', 'System Administrator', 'SYSTEM_ADMIN',    'SEED-ADMIN',  1),
  ('ops_manager',     '$2a$12$2cWIhKpa9S7tcvWxkY2d6.Tm/j4ZPbpYYTi5soNvGzDu0SSvaoNiS', 'Operations Manager',   'OPS_MANAGER',     'SEED-OPS',    1),
  ('reviewer',        '$2a$12$CzgLsCmiLoyM5ib/uXgGPulPj9WgCr1H4zZN8tNNNLLdEjullsKXm', 'Evaluation Reviewer',  'REVIEWER',        'SEED-REV',    1),
  ('auditor',         '$2a$12$3E.bxeoKqeOlJPcXwcWm9ucxCWt5w/K45aHPHh5U.i3.mwXKP1wUO', 'Compliance Auditor',   'AUDITOR',         'SEED-AUD',    1),
  ('data_integrator', '$2a$12$9LBH/Ufwy/Yc/l511cua3OzTjZYK7R/8ebkklotgKLwui/cM/c1qu', 'Data Integrator',      'DATA_INTEGRATOR', 'SEED-DI',     1);

-- Default scheduled jobs
INSERT OR IGNORE INTO scheduled_jobs (job_type, cron_expression, timeout_seconds, status, next_run)
VALUES
  ('BACKUP',            '0 0 2 * * ?',   1800, 'ACTIVE', datetime('now', '+1 day')),
  ('ARCHIVE',           '0 0 3 1 * ?',   3600, 'ACTIVE', datetime('now', '+1 month')),
  ('CONSISTENCY_CHECK', '0 0 4 * * ?',    900, 'ACTIVE', datetime('now', '+1 day'));
