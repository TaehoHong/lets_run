-- 아바타 테이블에 헤어 색상 컬럼 추가
ALTER TABLE avatar ADD COLUMN hair_color VARCHAR(7) NOT NULL DEFAULT '#FFFFFF' COMMENT '헤어 색상 (HEX 형식)';
