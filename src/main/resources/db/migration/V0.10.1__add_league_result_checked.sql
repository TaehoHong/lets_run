-- 리그 결과 확인 여부 컬럼 추가
ALTER TABLE league_participant
    ADD COLUMN is_result_checked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '결과 확인 여부'
    AFTER is_bot;
