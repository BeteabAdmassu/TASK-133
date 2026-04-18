-- Adds day-level activity tracking for the one-active-per-community-per-day rule.
-- Records the calendar date (UTC, yyyy-MM-dd) on which a pickup point most recently
-- transitioned to ACTIVE. The service layer uses this alongside status='ACTIVE' to
-- prevent any two pickup points in the same community from being ACTIVE on the same
-- calendar day, even if the first was paused before the second was activated.
ALTER TABLE pickup_points ADD COLUMN active_date TEXT;

-- Backfill: mark currently-ACTIVE rows with today so the invariant holds immediately.
UPDATE pickup_points SET active_date = date('now') WHERE status = 'ACTIVE';
