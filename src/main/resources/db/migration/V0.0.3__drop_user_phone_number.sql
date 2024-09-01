ALTER TABLE user DROP COLUMN phone_number;

DROP TABLE user_auth_code;

ALTER TABLE user ADD COLUMN authority_type CHAR(1)  NOT NULL COMMENT '권한 A(어드민), U(일반 이용자)' AFTER nickname;