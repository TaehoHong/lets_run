-- OAuth 토큰 저장 테이블
-- Apple 회원 탈퇴 시 토큰 해제(revoke)를 위해 refresh_token 저장

CREATE TABLE IF NOT EXISTS oauth_token (

    id                  BIGINT UNSIGNED AUTO_INCREMENT          NOT NULL,
    user_account_id     BIGINT UNSIGNED                         NOT NULL COMMENT '사용자 계정 ID',

    refresh_token       VARCHAR(512)                            NULL     COMMENT 'OAuth Refresh Token',

    created_datetime    DATETIME    DEFAULT CURRENT_TIMESTAMP() NOT NULL COMMENT '생성 일시',
    updated_datetime    DATETIME    DEFAULT CURRENT_TIMESTAMP()
                        ON UPDATE CURRENT_TIMESTAMP()           NOT NULL COMMENT '수정 일시',

    PRIMARY KEY (id),
    CONSTRAINT fk__oauth_token__user_account_id
        FOREIGN KEY (user_account_id) REFERENCES user_account (id),
    CONSTRAINT uk__oauth_token__user_account_id
        UNIQUE (user_account_id)

) COMMENT 'OAuth 토큰 저장';
