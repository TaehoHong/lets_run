CREATE TABLE shoe (

    id                  BIGINT UNSIGNED     AUTO_INCREMENT  NOT NULL,
    user_id             BIGINT UNSIGNED                     NOT NULL,

    brand               VARCHAR(254)                        NOT NULL COMMENT '브랜드',
    model               VARCHAR(254)                        NOT NULL COMMENT '모델명',

    target_distance     SMALLINT                                NULL COMMENT '목표 거리(m)',
    total_distance      SMALLINT            DEFAULT 0           NULL COMMENT '달린 거리(m)',

    is_main             TINYINT(1)          DEFAULT 0       NOT NULL COMMENT '착용 여부',

    is_enabled          TINYINT(1)          DEFAULT 1       NOT NULL COMMENT '활성화 여부',
    is_deleted          TINYINT(1)          DEFAULT 0       NOT NULL COMMENT '삭제 여부',

    created_datetime    DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL,
    updated_datetime    DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk__shoe__user_id FOREIGN KEY (user_id) REFERENCES user(id)

) COMMENT '사용자 신발';