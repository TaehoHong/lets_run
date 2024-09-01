
CREATE TABLE IF NOT EXISTS running_record (

    id          BIGINT UNSIGNED             NOT NULL,

    user_id     BIGINT UNSIGNED             NOT NULL,

#     pace        SMALLINT UNSIGNED           NULL COMMENT 'KM당 소요시간 (second)',
    distance    INT UNSIGNED    DEFAULT 0   NOT NULL COMMENT '거리 (Meter)',
    cadence     TINYINT UNSIGNED DEFAULT 0  NOT NULL COMMENT '평균 케이던스 step/minute',
    heart_rate  TINYINT UNSIGNED DEFAULT 0  NOT NULL COMMENT '평균 심박수',

    metadata    LONGTEXT                        NULL COMMENT '기록 메타데이터',

    start_time  DATETIME        NOT NULL,
    end_time    DATETIME            NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk__running_record__user_id FOREIGN KEY (user_id) REFERENCES user(id)

) COMMENT '러닝 기록';
