ALTER TABLE item
    ADD COLUMN tier VARCHAR(16) NOT NULL DEFAULT 'COMMON' COMMENT 'FREE|COMMON|RARE|UNIQUE|LEGENDARY' AFTER point,
    ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '신규 지급/판매/노출 가능 여부' AFTER tier,
    ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '회원가입 직후 기본 장착용' AFTER is_active;

UPDATE item
SET tier = 'FREE',
    point = 0
WHERE name IN (
    'Normal_Hair9', 'Normal_Hair1', 'Normal_Hair5', 'Normal_Hair8',
    'New_Hair_01', 'New_Hair_08', 'New_Hair_12', 'New_Hair_18',
    'New_Cloth_10', 'Normal_Cloth1', 'New_Cloth_01', 'New_Cloth_03', 'New_Cloth_12',
    'New_Pant_02', 'New_Pant_01', 'New_Pant_03', 'New_Pant_07', 'New_Pant_12'
);

UPDATE item
SET point = 1000
WHERE tier = 'COMMON';

UPDATE item
SET is_default = 1
WHERE name IN ('Normal_Hair9', 'New_Cloth_10', 'New_Pant_02');
