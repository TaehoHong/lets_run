-- 연속 러닝 보너스 및 페이스 보너스 시스템 추가

-- 1. UserLeagueInfo에 연속 러닝 추적 필드 추가
ALTER TABLE user_league_info
    ADD COLUMN streak_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '연속 러닝 일수',
    ADD COLUMN last_run_date DATE NULL COMMENT '마지막 러닝 날짜';

-- 2. 포인트 타입 추가
INSERT INTO point_type (id, name) VALUES
    (3,'연속 러닝 보너스'),
    (4,'페이스 보너스'),
    (5, '시즌 보상');
