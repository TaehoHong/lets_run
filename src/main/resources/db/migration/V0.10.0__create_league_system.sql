-- 리그 티어 마스터 테이블
CREATE TABLE IF NOT EXISTS league_tier (
    id              TINYINT UNSIGNED    NOT NULL,
    name            VARCHAR(32)         NOT NULL COMMENT '티어명',
    display_order   TINYINT UNSIGNED    NOT NULL COMMENT '표시 순서',

    PRIMARY KEY (id)
) COMMENT '리그 티어';

INSERT INTO league_tier (id, name, display_order) VALUES
(1, 'BRONZE', 1),
(2, 'SILVER', 2),
(3, 'GOLD', 3),
(4, 'PLATINUM', 4),
(5, 'DIAMOND', 5),
(6, 'CHALLENGER', 6);


-- 리그 시즌 테이블
CREATE TABLE IF NOT EXISTS league_season (
    id              BIGINT UNSIGNED AUTO_INCREMENT  NOT NULL,
    season_number   INT UNSIGNED                    NOT NULL COMMENT '시즌 번호',
    start_datetime  DATETIME                        NOT NULL COMMENT '시작일시',
    end_datetime    DATETIME                        NOT NULL COMMENT '종료일시',
    is_active       TINYINT(1) DEFAULT 1            NOT NULL COMMENT '활성 여부',
    created_datetime DATETIME DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '생성일시',

    PRIMARY KEY (id),
    UNIQUE KEY uk__league_season__season_number (season_number)
) COMMENT '리그 시즌';


-- 리그 그룹 테이블
CREATE TABLE IF NOT EXISTS league_group (
    id              BIGINT UNSIGNED AUTO_INCREMENT  NOT NULL,
    season_id       BIGINT UNSIGNED                 NOT NULL COMMENT '시즌 ID',
    tier_id         TINYINT UNSIGNED                NOT NULL COMMENT '티어 ID',
    created_datetime DATETIME DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '생성일시',

    PRIMARY KEY (id),
    CONSTRAINT fk__league_group__season_id FOREIGN KEY (season_id) REFERENCES league_season (id),
    CONSTRAINT fk__league_group__tier_id FOREIGN KEY (tier_id) REFERENCES league_tier (id)
) COMMENT '리그 그룹';


-- 리그 참가자 테이블
CREATE TABLE IF NOT EXISTS league_participant (
    id                      BIGINT UNSIGNED AUTO_INCREMENT  NOT NULL,
    group_id                BIGINT UNSIGNED                 NOT NULL COMMENT '그룹 ID',
    user_id                 BIGINT UNSIGNED                 NULL COMMENT '사용자 ID (봇은 NULL)',

    total_distance          BIGINT UNSIGNED DEFAULT 0       NOT NULL COMMENT '총 거리 (미터)',
    distance_achieved_at    DATETIME                        NULL COMMENT '거리 달성 시점',

    final_rank              INT UNSIGNED                    NULL COMMENT '최종 순위',
    promotion_status        VARCHAR(16)                     NULL COMMENT '승격/강등/유지/환생 상태',

    is_bot                  TINYINT(1) DEFAULT 0            NOT NULL COMMENT '봇 여부',

    created_datetime        DATETIME DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '생성일시',
    updated_datetime        DATETIME DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '수정일시',

    PRIMARY KEY (id),
    CONSTRAINT fk__league_participant__group_id FOREIGN KEY (group_id) REFERENCES league_group (id),
    CONSTRAINT fk__league_participant__user_id FOREIGN KEY (user_id) REFERENCES user (id),
    INDEX idx__league_participant__user_id (user_id),
    INDEX idx__league_participant__group_distance (group_id, total_distance DESC)
) COMMENT '리그 참가자';


-- 유저 리그 정보 테이블
CREATE TABLE IF NOT EXISTS user_league_info (
    id                      BIGINT UNSIGNED AUTO_INCREMENT  NOT NULL,
    user_id                 BIGINT UNSIGNED                 NOT NULL COMMENT '사용자 ID',

    current_tier_id         TINYINT UNSIGNED DEFAULT 1      NOT NULL COMMENT '현재 티어',
    rebirth_count           INT UNSIGNED DEFAULT 0          NOT NULL COMMENT '환생 횟수',

    last_active_season_id   BIGINT UNSIGNED                 NULL COMMENT '마지막 활동 시즌 ID',
    is_active               TINYINT(1) DEFAULT 1            NOT NULL COMMENT '리그 참여 상태',

    created_datetime        DATETIME DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '생성일시',
    updated_datetime        DATETIME DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '수정일시',

    PRIMARY KEY (id),
    CONSTRAINT fk__user_league_info__user_id FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk__user_league_info__current_tier_id FOREIGN KEY (current_tier_id) REFERENCES league_tier (id),
    UNIQUE KEY uk__user_league_info__user_id (user_id)
) COMMENT '유저 리그 정보';
