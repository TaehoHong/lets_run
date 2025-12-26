-- 리그 시즌에 상태(state) 컬럼 추가
ALTER TABLE league_season
    ADD COLUMN state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '시즌 상태 (ACTIVE, LOCKED, CALCULATING, AUDITING, FINALIZED)'
    AFTER is_active;

-- 기존 비활성 시즌은 FINALIZED로 설정
UPDATE league_season SET state = 'FINALIZED' WHERE is_active = 0;

-- 리그 참가자에 봇 관련 컬럼 추가
ALTER TABLE league_participant
    ADD COLUMN bot_type VARCHAR(16) NULL COMMENT '봇 유형 (PACER, COMPETITOR)'
    AFTER is_bot;

ALTER TABLE league_participant
    ADD COLUMN bot_name VARCHAR(32) NULL COMMENT '봇 이름'
    AFTER bot_type;

-- 리그 참가자에 보호 플래그 컬럼 추가 (Soft Lock용)
ALTER TABLE league_participant
    ADD COLUMN is_protected TINYINT(1) NOT NULL DEFAULT 0 COMMENT '보호 플래그 (Soft Lock)'
    AFTER bot_name;

-- 기존 봇들에 기본 유형 설정 (COMPETITOR)
UPDATE league_participant SET bot_type = 'COMPETITOR' WHERE is_bot = 1;
