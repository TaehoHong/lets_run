ALTER TABLE user_configuration
    ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'system' COMMENT 'App language: system, ko, ja';
