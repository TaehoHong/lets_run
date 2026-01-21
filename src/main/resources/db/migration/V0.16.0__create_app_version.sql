-- 앱 버전 관리 테이블
CREATE TABLE IF NOT EXISTS app_version (
    id                      BIGINT UNSIGNED AUTO_INCREMENT NOT NULL,
    platform                VARCHAR(16) NOT NULL COMMENT 'IOS, ANDROID',
    minimum_version         VARCHAR(32) NOT NULL,
    message                 VARCHAR(512) NULL,
    is_enabled              TINYINT(1) DEFAULT 1 NOT NULL,
    created_datetime        DATETIME DEFAULT CURRENT_TIMESTAMP() NOT NULL,
    updated_datetime        DATETIME DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP() NOT NULL,
    updated_by              VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk__app_version__platform (platform)
);

-- 초기 데이터
INSERT INTO app_version (platform, minimum_version, message) VALUES
('IOS', '1.0.1', '더 나은 러닝 경험을 위해 업데이트해 주세요.'),
('ANDROID', '1.0.0', '더 나은 러닝 경험을 위해 업데이트해 주세요.');
