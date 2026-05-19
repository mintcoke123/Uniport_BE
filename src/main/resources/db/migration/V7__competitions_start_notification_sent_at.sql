ALTER TABLE competitions
    ADD COLUMN IF NOT EXISTS start_notification_sent_at TIMESTAMP WITH TIME ZONE;
