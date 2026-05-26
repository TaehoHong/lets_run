ALTER TABLE running_record
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'LIVE' COMMENT '러닝 기록 출처' AFTER is_end,
    ADD COLUMN external_id VARCHAR(191) NULL COMMENT '외부 원천 기록 ID' AFTER source,
    ADD COLUMN imported_datetime DATETIME NULL COMMENT 'Health import 저장 시각' AFTER external_id,
    ADD COLUMN point_eligible TINYINT(1) NOT NULL DEFAULT 1 COMMENT '포인트 지급 가능 여부' AFTER imported_datetime,
    ADD COLUMN point_awarded TINYINT(1) NOT NULL DEFAULT 0 COMMENT '포인트 지급 완료 여부' AFTER point_eligible,
    ADD UNIQUE KEY ux__running_record__user_source_external_id (user_id, source, external_id),
    ADD INDEX ix__running_record__user_end_start_end (user_id, is_end, start_datetime, end_datetime);

UPDATE running_record rr
SET point_awarded = 1
WHERE EXISTS (
    SELECT 1
    FROM user_point_history uph
    WHERE uph.running_record_id = rr.id
      AND uph.point_type_id = 1
      AND uph.is_deleted = 0
);

CREATE TABLE IF NOT EXISTS user_configuration (
    user_id BIGINT UNSIGNED NOT NULL,
    health_import_enabled TINYINT(1) NOT NULL DEFAULT 0,
    health_import_last_synced_at DATETIME NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk__user_configuration__user_id FOREIGN KEY (user_id) REFERENCES user(id)
) COMMENT '사용자 설정';
