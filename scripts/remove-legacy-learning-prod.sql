-- Legacy mock learning cleanup for Railway Postgres.
-- Keep learning_user_states: Education V1 uses it for onboarding choices,
-- roadmap progress, streak, level, point, quiz answers, and card progress.

BEGIN;

DROP TABLE IF EXISTS learning_courses CASCADE;

UPDATE learning_user_states
SET active_course_id = NULL,
    current_day_by_course_json = '{}',
    completed_days_by_course_json = '{}',
    submitted_step_ids_json = '[]'
WHERE active_course_id IS NOT NULL
   OR COALESCE(current_day_by_course_json, '{}') <> '{}'
   OR COALESCE(completed_days_by_course_json, '{}') <> '{}'
   OR COALESCE(submitted_step_ids_json, '[]') <> '[]';

COMMIT;
