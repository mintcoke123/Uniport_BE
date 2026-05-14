CREATE TABLE IF NOT EXISTS user_push_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(2048) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    permission_status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_push_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_push_tokens_token
    ON user_push_tokens (token);

CREATE INDEX IF NOT EXISTS idx_user_push_tokens_user_active
    ON user_push_tokens (user_id, active);
