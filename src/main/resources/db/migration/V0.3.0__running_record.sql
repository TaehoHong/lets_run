CREATE OR REPLACE TABLE running_record (

    id                      BIGINT UNSIGNED AUTO_INCREMENT      NOT NULL,
    user_id                 BIGINT UNSIGNED                     NOT NULL,

    distance                INT UNSIGNED        DEFAULT 0       NOT NULL COMMENT '거리 (m)',
    duration_sec            BIGINT UNSIGNED     DEFAULT 0       NOT NULL COMMENT '시간 (s)',
    cadence                 TINYINT UNSIGNED    DEFAULT 0       NOT NULL COMMENT '평균 케이던스 step/minute',
    heart_rate              TINYINT UNSIGNED    DEFAULT 0       NOT NULL COMMENT '평균 심박수',
    calorie                 SMALLINT UNSIGNED   DEFAULT 0       NOT NULL COMMENT '칼로리 (kcal)',

    is_user_input           TINYINT(1)          DEFAULT 0       NOT NULL COMMENT '사용자 입력 여부',
    is_statistic_included   TINYINT(1)          DEFAULT 1       NOT NULL COMMENT '통계 포함 여부',
    is_end                  TINYINT(1)          DEFAULT 0       NOT NULL COMMENT '종료 여부',

    start_datetime          DATETIME                            NOT NULL COMMENT '시작 시간',
    end_datetime            DATETIME                                NULL COMMENT '종료 시간',

    created_datetime        DATETIME DEFAULT CURRENT_TIMESTAMP   NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk__running_record__user_id FOREIGN KEY (user_id) REFERENCES user(id)

) COMMENT '러닝 기록';


CREATE TABLE IF NOT EXISTS running_record_item (

    id                  BIGINT UNSIGNED     AUTO_INCREMENT  NOT NULL,
    running_record_id   BIGINT UNSIGNED     NOT NULL,

    distance            INT UNSIGNED        DEFAULT 0       NOT NULL COMMENT '거리 (m)',
    duration_sec        BIGINT UNSIGNED     DEFAULT 0       NOT NULL COMMENT '시간 (s)',
    cadence             TINYINT UNSIGNED    DEFAULT 0       NOT NULL COMMENT '평균 케이던스 step/minute',
    heart_rate          TINYINT UNSIGNED    DEFAULT 0       NOT NULL COMMENT '평균 심박수',
    min_heart_rate      TINYINT UNSIGNED    DEFAULT 0       NOT NULL COMMENT '최소 심박수',
    max_heart_rate      TINYINT UNSIGNED    DEFAULT 0       NOT NULL COMMENT '최대 심박수',

    order_index         SMALLINT UNSIGNED   DEFAULT 0       NOT NULL COMMENT '순서',

    start_datetime      DATETIME                            NOT NULL COMMENT '시작 시간',
    end_datetime        DATETIME                            NOT NULL COMMENT '종료 시간',

    created_datetime    DATETIME DEFAULT CURRENT_TIMESTAMP   NOT NULL,

    PRIMARY KEY (id),
    INDEX ix__running_record_id_order_index (running_record_id, order_index),
    CONSTRAINT fk__running_record_item__running_record_id FOREIGN KEY (running_record_id) REFERENCES running_record(id)

) COMMENT '러닝 기록 메타데이터';


CREATE TABLE IF NOT EXISTS distance_conversion (

    id      TINYINT UNSIGNED        NOT NULL,
    unit    VARCHAR(10)             NOT NULL COMMENT '단위',
    value   FLOAT                   NOT NULL COMMENT '값',

    PRIMARY KEY (id)

) COMMENT '단위 환산표 Meter 기준';