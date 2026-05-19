ALTER TABLE beta_ios_applications
    ADD COLUMN IF NOT EXISTS beta_tester_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS testflight_group_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS testflight_group_failure_message VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS testflight_group_added_at TIMESTAMP;
