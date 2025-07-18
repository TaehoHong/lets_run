SET @@session.foreign_key_checks = 0;

DROP TABLE user_item;

CREATE TABLE IF NOT EXISTS user_item (
     id                  BIGINT UNSIGNED AUTO_INCREMENT         NOT NULL,

     user_id             BIGINT UNSIGNED                         NOT NULL,
     item_id             BIGINT UNSIGNED                         NOT NULL,

     is_enabled          TINYINT(1)                              NOT NULL,
     is_expired          TINYINT(1)  DEFAULT 0                   NOT NULL COMMENT '사용 만료 여부',

     expire_datetime     DATETIME                                    NULL COMMENT '사용 만료일',
     created_datetime    DATETIME    DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '생성 일시',

     PRIMARY KEY (id),
     CONSTRAINT fk__user_item__avatar_id FOREIGN KEY (user_id) REFERENCES user(id),
     CONSTRAINT fk__user_item__item_id FOREIGN KEY (item_id) REFERENCES item(id)

) COMMENT '사용자가 가진 아이템';

SET @@session.foreign_key_checks = 1;