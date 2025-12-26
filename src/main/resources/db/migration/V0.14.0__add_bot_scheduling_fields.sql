-- 봇 스케줄링 시스템 추가
-- 30분 단위로 봇 기록을 분산 업데이트

-- 1. LeagueParticipant에 봇 스케줄링 필드 추가
ALTER TABLE league_participant
    ADD COLUMN scheduled_update_slot TINYINT UNSIGNED NULL COMMENT '봇 업데이트 시간대 (0-47, 30분 단위)',
    ADD COLUMN last_bot_update_date DATE NULL COMMENT '마지막 봇 업데이트 날짜';

-- 2. 기존 봇에 랜덤 슬롯 배정 (0-47)
UPDATE league_participant
SET scheduled_update_slot = FLOOR(RAND() * 48)
WHERE is_bot = 1 AND scheduled_update_slot IS NULL;

-- 3. 인덱스 추가 (슬롯별 봇 조회 최적화)
CREATE INDEX idx_participant_bot_schedule
    ON league_participant (is_bot, scheduled_update_slot, last_bot_update_date);
