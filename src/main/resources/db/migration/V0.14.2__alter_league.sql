SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE league_group;

ALTER TABLE league_season DROP COLUMN season_number;
ALTER TABLE user_league_info DROP COLUMN last_active_season_id;

RENAME TABLE league_season TO league_session;
ALTER TABLE league_session ADD COLUMN tier_id TINYINT UNSIGNED NOT NULL AFTER id;
ALTER TABLE league_session ADD CONSTRAINT fk__league_session__tier_id FOREIGN KEY(tier_id) REFERENCES league_tier(id);

ALTER TABLE league_participant DROP CONSTRAINT fk__league_participant__group_id;
ALTER TABLE league_participant DROP COLUMN group_id;
ALTER TABLE league_participant ADD COLUMN session_id BIGINT UNSIGNED NOT NULL AFTER id;
ALTER TABLE league_participant ADD CONSTRAINT fk__league_participant__session_id FOREIGN KEY(session_id) REFERENCES league_session(id);


SET FOREIGN_KEY_CHECKS = 1;