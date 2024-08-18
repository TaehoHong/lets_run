ALTER TABLE user ADD COLUMN phone_number CHAR(11) NOT NULL COMMENT '핸드폰 번호';

CREATE TABLE IF NOT EXISTS user_auth_code (

    user_id     BIGINT UNSIGNED     NOT NULL COMMENT '사용자 ID',
    code        SMALLINT UNSIGNED   NOT NULL COMMENT '인증 번호',

    PRIMARY KEY (user_id)

) COMMENT '사용자 인증 번호';