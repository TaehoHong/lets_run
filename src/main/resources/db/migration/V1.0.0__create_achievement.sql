CREATE TABLE achievement_category (
    id              TINYINT UNSIGNED    NOT NULL,
    name            VARCHAR(32)         NOT NULL COMMENT '카테고리명',
    description     VARCHAR(128)            NULL COMMENT '카테고리 설명',

    icon_path        VARCHAR(255)                               NULL COMMENT '아이콘 경로',
    display_order   TINYINT UNSIGNED    NOT NULL COMMENT '표시 순서',
    PRIMARY KEY (id)
) COMMENT '업적 카테고리';

INSERT INTO achievement_category (id, name, description, display_order) VALUES
(1, 'RUNNING_DISTANCE', '러닝 거리: 누적 거리 달성', 1),
(2, 'RUNNING_COUNT', '러닝 횟수: 누적 횟수 달성', 2),
(3, 'RUNNING_SINGLE', '러닝 기록: 한 번에 달성', 3),
(4, 'SHOE', '신발: 등록한 신발 수', 4),
(5, 'AVATAR_ITEM', '아바타: 구매한 아이템 수', 5),
(6, 'LEAGUE_PROMOTION', '리그-승급: 승급 횟수', 6),
(7, 'LEAGUE_REBIRTH', '리그-환생: 환생 횟수', 7),
(8, 'LEAGUE_PODIUM', '리그-포디움: 3위 이내 횟수', 8),
(9, 'LEAGUE_CHAMPION', '리그-챔피언: 1위 횟수', 9),
(10, 'ACHIEVEMENT_META', '업적 달성: 달성한 업적 수', 10);


CREATE TABLE achievement (
    id               BIGINT UNSIGNED AUTO_INCREMENT         NOT NULL,
    category_id      TINYINT UNSIGNED                       NOT NULL COMMENT '카테고리 ID',

    name             VARCHAR(64)                            NOT NULL COMMENT '업적명',
    description      VARCHAR(256)                           NOT NULL COMMENT '업적 설명',

    goal_value       INT UNSIGNED                           NOT NULL COMMENT '목표 값',
    reward_point     INT UNSIGNED                           NOT NULL COMMENT '보상 포인트',

    display_order    INT UNSIGNED                           NOT NULL COMMENT '표시 순서',
    is_enabled       TINYINT(1) DEFAULT 1                   NOT NULL COMMENT '활성화 여부',
    created_datetime DATETIME DEFAULT CURRENT_TIMESTAMP()   NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk__achievement__category_id FOREIGN KEY (category_id) REFERENCES achievement_category(id)
) COMMENT '업적';


CREATE TABLE title (
    id                  BIGINT UNSIGNED AUTO_INCREMENT          NOT NULL,
    achievement_id      BIGINT UNSIGNED                             NULL COMMENT '연결된 업적 ID',

    name                VARCHAR(64)                             NOT NULL COMMENT '칭호명',
    description         VARCHAR(256)                                NULL COMMENT '칭호 설명',

    is_enabled          TINYINT(1) DEFAULT 1                    NOT NULL COMMENT '활성화 여부',

    display_order       INT UNSIGNED                            NOT NULL COMMENT '표시 순서',
    created_datetime    DATETIME DEFAULT CURRENT_TIMESTAMP()    NOT NULL,
    PRIMARY KEY (id),

    CONSTRAINT fk__title__achievement_id FOREIGN KEY (achievement_id) REFERENCES achievement(id)
) COMMENT '칭호';


CREATE TABLE user_achievement (
    id                      BIGINT UNSIGNED AUTO_INCREMENT          NOT NULL,
    user_id                 BIGINT UNSIGNED                         NOT NULL COMMENT '사용자 ID',
    achievement_id          BIGINT UNSIGNED                         NOT NULL COMMENT '업적 ID',

    achieved_datetime       DATETIME                                NOT NULL COMMENT '달성 일시',
    created_datetime        DATETIME DEFAULT CURRENT_TIMESTAMP()    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk__user_achievement (user_id, achievement_id),
    CONSTRAINT fk__user_achievement__user_id FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT fk__user_achievement__achievement_id FOREIGN KEY (achievement_id) REFERENCES achievement(id)
) COMMENT '유저 업적 달성 기록';


CREATE TABLE user_title (
    id                  BIGINT UNSIGNED AUTO_INCREMENT          NOT NULL,
    user_id             BIGINT UNSIGNED                         NOT NULL COMMENT '사용자 ID',
    title_id            BIGINT UNSIGNED                         NOT NULL COMMENT '칭호 ID',

    is_main             TINYINT(1) DEFAULT 0                    NOT NULL COMMENT '대표 칭호 여부',

    acquired_datetime   DATETIME                                NOT NULL COMMENT '획득 일시',
    created_datetime    DATETIME DEFAULT CURRENT_TIMESTAMP()    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk__user_title (user_id, title_id),
    CONSTRAINT fk__user_title__user_id FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT fk__user_title__title_id FOREIGN KEY (title_id) REFERENCES title(id)
) COMMENT '유저 칭호 보유';


INSERT INTO point_type (id, name) VALUES (6, '업적 보상');
