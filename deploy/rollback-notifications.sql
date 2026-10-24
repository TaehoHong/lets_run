-- 수동 복구 전용. Flyway 자동 실행 대상이 아니다.
-- 우선 알림 작업자를 중지하고 이전 서버로 되돌린다. 새 테이블은 남겨 두어도 된다.
-- 아래 DDL은 알림·공지·기기 정보를 삭제하므로 필요한 데이터를 별도 백업한 뒤에만 실행한다.
-- MariaDB DDL은 implicit commit이다. 트랜잭션 ROLLBACK으로 되돌릴 수 없다.
-- 일부만 적용된 경우 information_schema에서 상태를 확인하고 해당 구문만 실행한다.
-- 기존 user_configuration.language는 유지되므로 구버전 서버 언어 설정은 보존된다.

DROP TABLE notification_push;
DROP TABLE notification_translation;
DROP TABLE notification;
DROP TABLE notice_translation;
DROP TABLE notice;
DROP TABLE user_device;
DROP TABLE user_running_reminder_state;
DROP TABLE user_notification_configuration;
ALTER TABLE user_configuration
    DROP FOREIGN KEY fk__user_configuration__language,
    DROP COLUMN language_id;
DROP TABLE language;

-- Flyway 이력은 자동 삭제하지 않는다. 재배포 방식에 맞춰 별도로 검토한다.
