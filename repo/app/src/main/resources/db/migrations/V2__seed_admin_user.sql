-- Seed demo users for every role used by authorization logic.
--
-- `password_hash` values are real BCrypt hashes (org.mindrot:jbcrypt:0.4, cost=12),
-- verified before check-in.  See the README > Demo Credentials section for the
-- matching plaintext passwords.
--
-- `staff_id_encrypted` values are **pre-encrypted ciphertext** — AES-256-GCM
-- Base64-encoded payloads produced by `EncryptionUtil` under the
-- deployment's seed key (`APP_TEST_ENC_KEY` in headless/Docker mode, Windows
-- DPAPI in desktop production).  Each payload decrypts to one of the
-- documented seed markers:
--
--   admin           -> SEED-ADMIN
--   ops_manager     -> SEED-OPS
--   reviewer        -> SEED-REV
--   auditor         -> SEED-AUD
--   data_integrator -> SEED-DI
--
-- No plaintext markers are stored in this migration.  `SeedEncryptionService`
-- runs on every boot and will re-encrypt any value it cannot decrypt under
-- the current runtime key, so deployments whose key differs from the seed
-- key still converge to valid ciphertext without manual intervention.

INSERT OR IGNORE INTO users (username, password_hash, display_name, role, staff_id_encrypted, is_active)
VALUES
  ('admin',           '$2a$12$g9JblFv8QrTN/2VP2SOA/OFwv8AQqx1/VSMacPvXZfqmuRTAXpofa', 'System Administrator', 'SYSTEM_ADMIN',    'K+epFq+MYbOJBRGS6NIq3V/bCO6gs6MPLknRKVuK7bsYg8fS5/w=', 1),
  ('ops_manager',     '$2a$12$2cWIhKpa9S7tcvWxkY2d6.Tm/j4ZPbpYYTi5soNvGzDu0SSvaoNiS', 'Operations Manager',   'OPS_MANAGER',     'fjYPWr7lw26i8t9N6o+U6ecDk1cIfFZhAAQGWLJ3qPEGZpa6',     1),
  ('reviewer',        '$2a$12$CzgLsCmiLoyM5ib/uXgGPulPj9WgCr1H4zZN8tNNNLLdEjullsKXm', 'Evaluation Reviewer',  'REVIEWER',        'opsFWyGpuNMRErt4vXlfXcBIL1rZmiosm1KZfwZAqI7Zld0v',     1),
  ('auditor',         '$2a$12$3E.bxeoKqeOlJPcXwcWm9ucxCWt5w/K45aHPHh5U.i3.mwXKP1wUO', 'Compliance Auditor',   'AUDITOR',         'g0kvrUwWM989lVc02O4IIkWHJA07IOf0XohS3fO+/2VJB6pX',     1),
  ('data_integrator', '$2a$12$9LBH/Ufwy/Yc/l511cua3OzTjZYK7R/8ebkklotgKLwui/cM/c1qu', 'Data Integrator',      'DATA_INTEGRATOR', 's/vNcjXN4hRKJ0SIyihPgWLVgf9qOs5cbxVSTHdQ4MqPwUc=',     1);

-- Default scheduled jobs
INSERT OR IGNORE INTO scheduled_jobs (job_type, cron_expression, timeout_seconds, status, next_run)
VALUES
  ('BACKUP',            '0 0 2 * * ?',   1800, 'ACTIVE', datetime('now', '+1 day')),
  ('ARCHIVE',           '0 0 3 1 * ?',   3600, 'ACTIVE', datetime('now', '+1 month')),
  ('CONSISTENCY_CHECK', '0 0 4 * * ?',    900, 'ACTIVE', datetime('now', '+1 day'));
