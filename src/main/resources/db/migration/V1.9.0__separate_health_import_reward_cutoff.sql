ALTER TABLE user_configuration
    ADD COLUMN health_import_first_enabled_at DATETIME(3) NULL COMMENT '확인 가능한 최초 건강 연동 활성화 시각',
    ADD COLUMN health_import_reward_cutoff_at DATETIME(3) NULL COMMENT '건강 연동 포인트 지급 기준 시각';

UPDATE user_configuration
SET health_import_reward_cutoff_at = COALESCE(health_import_last_synced_at, UTC_TIMESTAMP(3))
WHERE health_import_last_synced_at IS NOT NULL OR health_import_enabled = 1;
