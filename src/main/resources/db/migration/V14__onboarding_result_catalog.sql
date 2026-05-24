CREATE TABLE IF NOT EXISTS onboarding_result_catalog (
    character_id INTEGER PRIMARY KEY,
    profile_key VARCHAR(80) NOT NULL UNIQUE,
    canonical_name VARCHAR(80) NOT NULL,
    legacy_aliases_json TEXT NOT NULL,
    level_label VARCHAR(20) NOT NULL,
    card_summary TEXT NOT NULL,
    investment_type TEXT NOT NULL,
    analysis_title TEXT NOT NULL,
    analysis_subtitle TEXT NOT NULL,
    traits_json TEXT NOT NULL,
    trait_descriptions_json TEXT NOT NULL,
    principles_json TEXT NOT NULL,
    principle_descriptions_json TEXT NOT NULL,
    strategies_json TEXT NOT NULL,
    character_image_resource VARCHAR(120) NOT NULL,
    character_asset_url TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
