-- Reset legacy onboarding data created before the single-sector rollout.
-- Old onboarding results stored two sectors in users.interest_sector as a comma-separated string.
-- Those users should re-run onboarding so both profile data and education seeds match the new spec.

UPDATE learning_user_states
SET education_current_day_json = '{}',
    education_completed_days_json = '{}',
    education_quiz_answers_json = '{}',
    education_card_progress_json = '{}',
    education_sector_selections_json = '{}'
WHERE user_id IN (
    SELECT id
    FROM users
    WHERE interest_sector LIKE '%,%'
);

UPDATE users
SET investment_profile_result = NULL,
    investment_level = NULL,
    interest_sector = NULL
WHERE interest_sector LIKE '%,%';
