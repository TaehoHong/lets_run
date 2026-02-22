ALTER TABLE running_record_item
    ADD COLUMN gps_points_json LONGTEXT NULL
    COMMENT '세그먼트 GPS 포인트(JSON 배열)';
