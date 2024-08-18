CREATE TABLE IF NOT EXISTS user (

    id              BIGINT UNSIGNED AUTO_INCREMENT          NOT NULL,
    nickname        VARCHAR(128)                            NOT NULL COMMENT '닉네임',

    is_enabled      TINYINT(1)  DEFAULT 1                   NOT NULL COMMENT '활성화 여부',
    is_deleted      TINYINT(1)  DEFAULT 0                   NOT NULL COMMENT '삭제 여부',

    create_datetime DATETIME    DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '생성 일시',

    PRIMARY KEY (id)

)  COMMENT '사용자';


CREATE TABLE IF NOT EXISTS account_type (

    id  TINYINT UNSIGNED    NOT NULL,
    name VARCHAR(32)        NOT NULL,

    PRIMARY KEY (id)

) COMMENT '계정 타입';

INSERT INTO account_type (id, name)
VALUES (1, 'SELF'), (2, 'GOOGLE'), (3, 'APPLE');


CREATE TABLE IF NOT EXISTS user_account (

    id              BIGINT UNSIGNED     AUTO_INCREMENT          NOT NULL,
    user_id         BIGINT UNSIGNED                             NOT NULL COMMENT '사용자 ID',

    account_type_id TINYINT UNSIGNED                            NOT NULL COMMENT '계정 타입',

    email           VARCHAR(128)                                NOT NULL COMMENT '이메일',
    password        VARCHAR(256)                                NOT NULL COMMENT '비밀번호',

    is_enabled      TINYINT(1)  DEFAULT 1                       NOT NULL COMMENT '활성화 여부',
    is_deleted      TINYINT(1)  DEFAULT 0                       NOT NULL COMMENT '삭제 여부',

    create_datetime DATETIME    DEFAULT CURRENT_TIMESTAMP()     NOT NULL COMMENT '생성 일시',

    PRIMARY KEY (id),
    CONSTRAINT fk__user_account__user_id FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk__user_account__account_type_id FOREIGN KEY (account_type_id) REFERENCES account_type (id)

) COMMENT '사용자 계정 정보';