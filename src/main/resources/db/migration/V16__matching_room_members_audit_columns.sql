ALTER TABLE matching_room_members
    ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE matching_room_members
    ADD COLUMN IF NOT EXISTS last_read_at TIMESTAMP WITH TIME ZONE;

UPDATE matching_room_members
SET joined_at = COALESCE(joined_at, NOW())
WHERE joined_at IS NULL;

UPDATE matching_room_members
SET last_read_at = COALESCE(last_read_at, joined_at, NOW())
WHERE last_read_at IS NULL;

ALTER TABLE matching_room_members
    ALTER COLUMN joined_at SET NOT NULL;
