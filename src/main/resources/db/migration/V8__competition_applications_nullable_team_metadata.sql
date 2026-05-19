ALTER TABLE competition_applications
    ALTER COLUMN team_id DROP NOT NULL,
    ALTER COLUMN team_name DROP NOT NULL;

COMMENT ON COLUMN competition_applications.team_id
    IS 'Deprecated: no longer used. Tournament teams are derived from matching_rooms.id after matching starts.';

COMMENT ON COLUMN competition_applications.team_name
    IS 'Deprecated: no longer used. Tournament teams are derived from matching_rooms.name after matching starts.';
