-- Friend invite links are separate from directional friend requests.
-- Hibernate ddl-auto=update also creates this table, but this migration documents the production shape.

CREATE TABLE IF NOT EXISTS friend_invites (
    id BIGSERIAL PRIMARY KEY,
    inviter_user_id BIGINT NOT NULL,
    invite_code VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted_by_user_id BIGINT NULL,
    accepted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_friend_invites_inviter_user
        FOREIGN KEY (inviter_user_id) REFERENCES users (id),
    CONSTRAINT fk_friend_invites_accepted_by_user
        FOREIGN KEY (accepted_by_user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_friend_invites_invite_code
    ON friend_invites (invite_code);

CREATE INDEX IF NOT EXISTS idx_friend_invites_inviter_user_id
    ON friend_invites (inviter_user_id);
