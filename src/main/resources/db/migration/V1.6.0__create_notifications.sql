-- UTC DATETIME. 기존 language 문자열은 구버전 앱/서버와 롤백을 위해 유지한다.
-- 알 수 없는 기존 언어가 있으면 어떤 DDL도 실행하기 전에 실패한다.
DELIMITER $$
BEGIN NOT ATOMIC
    IF EXISTS (SELECT 1 FROM user_configuration WHERE language NOT IN ('system', 'ko', 'ja')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown user_configuration.language; resolve before migration';
    END IF;
END$$
DELIMITER ;

CREATE TABLE language (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY ux__language__code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO language (code, name) VALUES ('ko', '한국어'), ('ja', '日本語');

ALTER TABLE user_configuration
    ADD COLUMN language_id SMALLINT UNSIGNED NULL COMMENT 'NULL: 기기 언어 사용',
    ADD CONSTRAINT fk__user_configuration__language FOREIGN KEY (language_id) REFERENCES language(id);

UPDATE user_configuration uc
JOIN language l ON l.code = uc.language
SET uc.language_id = l.id;

CREATE TABLE user_notification_configuration (
    user_id BIGINT UNSIGNED NOT NULL,
    push_enabled TINYINT(1) NOT NULL DEFAULT 0,
    league_push_enabled TINYINT(1) NOT NULL DEFAULT 1,
    reminder_push_enabled TINYINT(1) NOT NULL DEFAULT 1,
    notice_push_enabled TINYINT(1) NOT NULL DEFAULT 1,
    timezone VARCHAR(64) NULL,
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk__user_notification_configuration__user FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_running_reminder_state (
    user_id BIGINT UNSIGNED NOT NULL,
    anchor_datetime DATETIME NOT NULL COMMENT '최근 러닝 완료 시각',
    next_reminder_datetime DATETIME NULL COMMENT '다음 알림 생성 시각. 완료 시 +48h, 1차 생성 후 +72h, 2차 생성 후 NULL',
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    INDEX ix__user_running_reminder_state__next_reminder (next_reminder_datetime),
    CONSTRAINT fk__user_running_reminder_state__user FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 가입 시 또는 배포 시 과거 기록으로 알림을 일괄 생성하지 않는다.
-- 배포 후 러닝 완료/Health 기록 반영 트랜잭션에서 상태를 생성·갱신한다.
CREATE TABLE user_device (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    installation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    push_token VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    platform VARCHAR(16) NOT NULL,
    language_id SMALLINT UNSIGNED NOT NULL,
    binding_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '소유자·토큰·활성 상태 변경 시 증가',
    is_enabled TINYINT(1) NOT NULL DEFAULT 0,
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY ux__user_device__installation_id (installation_id),
    UNIQUE KEY ux__user_device__push_token (push_token),
    INDEX ix__user_device__user_enabled (user_id, is_enabled),
    INDEX ix__user_device__language (language_id),
    CONSTRAINT fk__user_device__user FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT fk__user_device__language FOREIGN KEY (language_id) REFERENCES language(id),
    CONSTRAINT ck__user_device__platform CHECK (platform IN ('IOS', 'ANDROID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notice (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_datetime DATETIME NULL,
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notice_translation (
    notice_id BIGINT UNSIGNED NOT NULL,
    language_id SMALLINT UNSIGNED NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (notice_id, language_id),
    INDEX ix__notice_translation__language (language_id),
    CONSTRAINT fk__notice_translation__notice FOREIGN KEY (notice_id) REFERENCES notice(id),
    CONSTRAINT fk__notice_translation__language FOREIGN KEY (language_id) REFERENCES language(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(32) NOT NULL,
    source_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사용자별 이벤트 중복 방지 키',
    notice_id BIGINT UNSIGNED NULL,
    league_participant_id BIGINT UNSIGNED NULL,
    read_datetime DATETIME NULL,
    expired_datetime DATETIME NOT NULL,
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY ux__notification__user_source (user_id, source_key),
    INDEX ix__notification__user (user_id),
    INDEX ix__notification__expired (expired_datetime),
    INDEX ix__notification__notice (notice_id),
    INDEX ix__notification__league_participant (league_participant_id),
    CONSTRAINT fk__notification__user FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT fk__notification__notice FOREIGN KEY (notice_id) REFERENCES notice(id),
    CONSTRAINT fk__notification__league_participant FOREIGN KEY (league_participant_id) REFERENCES league_participant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_translation (
    notification_id BIGINT UNSIGNED NOT NULL,
    language_id SMALLINT UNSIGNED NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id, language_id),
    INDEX ix__notification_translation__language (language_id),
    CONSTRAINT fk__notification_translation__notification FOREIGN KEY (notification_id) REFERENCES notification(id),
    CONSTRAINT fk__notification_translation__language FOREIGN KEY (language_id) REFERENCES language(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 리그 결과·리마인더·공지의 공통 푸시 큐. 외부 전송 중 DB 트랜잭션을 유지하지 않는다.
CREATE TABLE notification_push (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_id BIGINT UNSIGNED NOT NULL,
    user_device_id BIGINT UNSIGNED NOT NULL,
    binding_version BIGINT UNSIGNED NOT NULL COMMENT '큐 생성 시점의 기기 바인딩 버전',
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_datetime DATETIME NOT NULL COMMENT '재시도·영수증 조회 예정 시각',
    locked_until_datetime DATETIME NULL,
    lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '현재 점유 식별자. 만료된 작업자의 갱신 차단',
    provider_ticket_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    error_code VARCHAR(64) NULL COMMENT '정규화한 오류 코드만 저장. 토큰·원문 응답 저장 금지',
    created_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_datetime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY ux__notification_push__notification_device (notification_id, user_device_id),
    INDEX ix__notification_push__status_next_attempt (status, next_attempt_datetime),
    INDEX ix__notification_push__status_locked_until (status, locked_until_datetime),
    INDEX ix__notification_push__user_device (user_device_id),
    CONSTRAINT fk__notification_push__notification FOREIGN KEY (notification_id) REFERENCES notification(id),
    CONSTRAINT fk__notification_push__user_device FOREIGN KEY (user_device_id) REFERENCES user_device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
