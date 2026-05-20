CREATE TABLE IF NOT EXISTS investment_test_reservations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    contact_type VARCHAR(20) NOT NULL,
    contact_value VARCHAR(254) NOT NULL,
    consent BOOLEAN NOT NULL,
    result_key VARCHAR(40) NOT NULL,
    result_title VARCHAR(120) NOT NULL,
    interest_keywords_json TEXT NOT NULL,
    answers_json TEXT NOT NULL,
    user_agent VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_investment_test_reservations_contact UNIQUE (contact_type, contact_value)
);

CREATE INDEX IF NOT EXISTS idx_investment_test_reservations_contact
    ON investment_test_reservations (contact_type, contact_value);
CREATE INDEX IF NOT EXISTS idx_investment_test_reservations_result_key
    ON investment_test_reservations (result_key);
