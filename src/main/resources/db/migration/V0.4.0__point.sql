
CREATE OR REPLACE TABLE point_type (

    id         TINYINT UNSIGNED    NOT NULL,
    name       VARCHAR(20)         NOT NULL COMMENT '이름',

    PRIMARY KEY (id)

) COMMENT '포인트 타입';

INSERT INTO point_type (id, name)
VALUES (1, '러닝 기록'),
       (2, '아이템 구매');

CREATE OR REPLACE TABLE user_point (

    user_id             BIGINT UNSIGNED                     NOT NULL,
    point               INT UNSIGNED        DEFAULT 0       NOT NULL COMMENT '포인트',

    updated_datetime    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,

    PRIMARY KEY (user_id),
    CONSTRAINT fk__user_point__user_id FOREIGN KEY (user_id) REFERENCES user(id)

) COMMENT '사용자 포인트';


CREATE OR REPLACE TABLE user_point_history (

    id                  BIGINT UNSIGNED     AUTO_INCREMENT  NOT NULL,
    user_id             BIGINT UNSIGNED                     NOT NULL,

    point_type_id       TINYINT UNSIGNED                    NOT NULL COMMENT '포인트 타입',
    point               INT                                 NOT NULL COMMENT '포인트',

    running_record_id   BIGINT UNSIGNED                         NULL COMMENT '러닝 기록 ID',
    item_id             BIGINT UNSIGNED                         NULL COMMENT '아이템 ID',

    is_deleted          TINYINT(1)          DEFAULT 0       NOT NULL COMMENT '삭제 여부',

    created_datetime    DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk__user_point_history__user_id FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT fk__user_point_history__point_type_id FOREIGN KEY (point_type_id) REFERENCES point_type(id),
    CONSTRAINT fk__user_point_history__running_record_id FOREIGN KEY (running_record_id) REFERENCES running_record(id),
    CONSTRAINT fk__user_point_history__item_id FOREIGN KEY (item_id) REFERENCES item(id)

) COMMENT '사용자 포인트 이력';