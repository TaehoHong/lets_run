CREATE TABLE IF NOT EXISTS item_type (

    id      TINYINT UNSIGNED    NOT NULL,
    name    VARCHAR(16)         NOT NULL,

    PRIMARY KEY (id)

) COMMENT '아이템 종류';


CREATE TABLE IF NOT EXISTS item (

    id                  BIGINT UNSIGNED AUTO_INCREMENT          NOT NULL,
    item_type_id        TINYINT UNSIGNED                        NOT NULL,
    name                VARCHAR(128)                            NOT NULL,

    file_path           VARCHAR(64)                             NOT NULL,

    created_datetime    DATETIME    DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '생성 일시',

    PRIMARY KEY (id),
    CONSTRAINT fk__item__item_type_id FOREIGN KEY (item_type_id) REFERENCES item_type(id)

) COMMENT '캐릭터 아이템';


CREATE TABLE IF NOT EXISTS avatar (

    id          BIGINT UNSIGNED AUTO_INCREMENT      NOT NULL,
    user_id     BIGINT UNSIGNED                     NOT NULL,

    is_main     TINYINT(1)                          NOT NULL,

    order_index TINYINT UNSIGNED                    NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk__avatar__user_id FOREIGN KEY (user_id) REFERENCES user(id)

) COMMENT '아바타';


CREATE TABLE IF NOT EXISTS user_item (

    id                  BIGINT UNSIGNED                         NOT NULL,

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


CREATE TABLE IF NOT EXISTS avatar_user_item (

    avatar_id       BIGINT UNSIGNED NOT NULL,
    user_item_id    BIGINT UNSIGNED NOT NULL,

    PRIMARY KEY (avatar_id, user_item_id),
    CONSTRAINT fk__avatar_item__avatar_id FOREIGN KEY (avatar_id) REFERENCES avatar(id),
    CONSTRAINT fk__avatar_item__user_item_id FOREIGN KEY (user_item_id) REFERENCES user_item(id)
);