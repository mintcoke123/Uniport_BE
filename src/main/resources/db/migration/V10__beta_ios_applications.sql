CREATE TABLE IF NOT EXISTS beta_ios_applications (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    apple_id_email VARCHAR(254) NOT NULL UNIQUE,
    contact_email VARCHAR(254) NOT NULL,
    device VARCHAR(40) NOT NULL,
    consent BOOLEAN NOT NULL,
    status VARCHAR(40) NOT NULL,
    app_store_connect_invitation_id VARCHAR(120),
    invite_failure_message VARCHAR(1000),
    invited_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_beta_ios_applications_apple_id_email
    ON beta_ios_applications (apple_id_email);
CREATE INDEX IF NOT EXISTS idx_beta_ios_applications_status
    ON beta_ios_applications (status);
