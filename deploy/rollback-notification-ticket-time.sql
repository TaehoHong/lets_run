-- 수동 복구 전용. 알림 작업자를 중지하고 이전 코드로 되돌린 뒤 적용한다.
-- 기존 알림·ticket은 보존하며 추적 시작 시각만 제거한다.
-- 단기 코드 롤백은 nullable 컬럼을 유지할 수 있다.
-- 컬럼을 제거했다면 새 코드 재배포 전에 V1.11.0과 동일한 ADD COLUMN으로 복구한다.
-- Flyway 성공 이력이 남아 migration이 자동 재실행되지 않는다. 이력을 자동 삭제하지 않는다.
ALTER TABLE notification_push DROP COLUMN ticket_created_datetime;
