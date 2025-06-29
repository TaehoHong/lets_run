ALTER TABLE shoe CHANGE target_distance target_distance INT NULL COMMENT '목표 거리(m)';
ALTER TABLE shoe CHANGE total_distance total_distance INT DEFAULT 0 NULL COMMENT '달린 거리(m)';