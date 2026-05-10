-- Recreate UniPort education content tables for Railway/Postgres.
--
-- Scope:
-- - Drops only generated education content tables.
-- - Keeps users and learning_user_states rows.
-- - Adds the new learning_user_states columns needed by the KMP education API.
--
-- Run after the education branch code is deployed, then restart the backend.
-- Hibernate ddl-auto=update will recreate the dropped tables on startup, and
-- EducationContentService.seedDatabaseIfNeeded() will seed them from
-- src/main/resources/education/*.json.
--
-- Example:
--   psql "$DATABASE_URL" -f scripts/recreate-education-content-prod.sql

BEGIN;

DROP TABLE IF EXISTS education_cards CASCADE;
DROP TABLE IF EXISTS education_overviews CASCADE;
DROP TABLE IF EXISTS education_quizzes CASCADE;

ALTER TABLE learning_user_states
    ADD COLUMN IF NOT EXISTS education_card_progress_json TEXT,
    ADD COLUMN IF NOT EXISTS education_sector_selections_json TEXT;

COMMIT;
