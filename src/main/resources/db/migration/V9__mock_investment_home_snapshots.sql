CREATE TABLE IF NOT EXISTS team_game_snapshots (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    team_name VARCHAR(150) NOT NULL,
    team_game_id VARCHAR(80) NOT NULL,
    member_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_asset_amount NUMERIC(19, 4) NOT NULL,
    return_rate NUMERIC(10, 4) NOT NULL,
    snapshot_at TIMESTAMP WITH TIME ZONE NOT NULL,
    snapshot_date DATE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_team_game_snapshots_latest ON team_game_snapshots (snapshot_at DESC, return_rate DESC);
CREATE INDEX IF NOT EXISTS idx_team_game_snapshots_team_date ON team_game_snapshots (team_id, snapshot_date);
