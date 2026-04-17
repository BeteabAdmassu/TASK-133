-- Installer execution fields for the .msi apply/rollback workflow.
--
-- These columns are nullable so pre-installer rows (file-promotion-only
-- apply/rollback events written by the V3 era of UpdateService) remain
-- valid and queryable through the same endpoint.

ALTER TABLE update_history ADD COLUMN exit_code INTEGER;
ALTER TABLE update_history ADD COLUMN log_path  TEXT;
ALTER TABLE update_history ADD COLUMN installer_type TEXT;
