-- V0.9.0: 사용자 프로필 이미지 URL 컬럼 추가
ALTER TABLE `user` ADD COLUMN `profile_image_url` VARCHAR(512) NULL AFTER `nickname`;
