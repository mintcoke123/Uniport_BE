CREATE TABLE IF NOT EXISTS user_auth_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    firebase_uid VARCHAR(128) NOT NULL,
    provider_id VARCHAR(80),
    email VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_auth_identities_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_auth_identities_firebase_uid
        UNIQUE (firebase_uid)
);

CREATE INDEX IF NOT EXISTS idx_user_auth_identities_user_id
    ON user_auth_identities(user_id);

CREATE INDEX IF NOT EXISTS idx_user_auth_identities_email
    ON user_auth_identities(email);

INSERT INTO user_auth_identities (
    user_id,
    firebase_uid,
    provider_id,
    email,
    email_verified,
    created_at,
    updated_at
)
SELECT
    id,
    firebase_uid,
    NULL,
    email,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM users
WHERE firebase_uid IS NOT NULL
  AND TRIM(firebase_uid) <> ''
ON CONFLICT (firebase_uid) DO NOTHING;
