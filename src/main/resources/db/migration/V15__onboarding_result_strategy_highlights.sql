ALTER TABLE onboarding_result_catalog
    ADD COLUMN IF NOT EXISTS strategy_highlights_json TEXT NOT NULL DEFAULT '[]';
