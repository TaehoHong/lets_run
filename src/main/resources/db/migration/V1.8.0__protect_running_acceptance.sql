ALTER TABLE running_record
    ADD COLUMN acceptance_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN original_start_datetime DATETIME(3) NULL,
    ADD COLUMN original_end_datetime DATETIME(3) NULL,
    ADD INDEX ix__running_record__user_acceptance_start_end
        (user_id, acceptance_status, original_start_datetime, original_end_datetime);

-- Preserve previously recorded league values and already granted rewards.
-- Earlier edits cannot be reconstructed; retain the currently known start.
UPDATE running_record
SET acceptance_status = CASE
        WHEN league_distance_override = 0 AND point_awarded = 0
            THEN 'PERSONAL_ONLY'
        ELSE 'ACCEPTED'
    END,
    original_start_datetime = start_datetime,
    original_end_datetime = COALESCE(league_end_datetime_override, end_datetime),
    league_distance_override = COALESCE(league_distance_override, distance),
    league_end_datetime_override = COALESCE(league_end_datetime_override, end_datetime)
WHERE is_end = 1 AND end_datetime IS NOT NULL;

UPDATE running_record
SET point_eligible = 0
WHERE acceptance_status = 'PERSONAL_ONLY';
