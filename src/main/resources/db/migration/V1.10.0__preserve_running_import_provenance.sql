ALTER TABLE running_record
    ADD COLUMN source_app_id VARCHAR(255) NULL COMMENT '최초 수신 시 건강 기록의 작성 앱 식별값',
    ADD COLUMN source_app_name VARCHAR(255) NULL COMMENT '최초 수신 시 건강 기록의 작성 앱 표시 이름',
    ADD COLUMN recording_method VARCHAR(16) NULL COMMENT '최초 인정 판정에 사용한 측정 방식',
    ADD COLUMN point_exclusion_reason VARCHAR(40) NULL COMMENT '최초 인정 판정의 포인트 제외 사유';
