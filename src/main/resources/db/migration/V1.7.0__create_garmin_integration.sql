ALTER TABLE running_record
    ADD COLUMN league_distance_override INT NULL,
    ADD COLUMN league_end_datetime_override DATETIME NULL;

CREATE TABLE activity_connection (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    external_user_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    status VARCHAR(24) NOT NULL,
    connected_at DATETIME(3) NULL,
    last_received_at DATETIME(3) NULL,
    last_imported_at DATETIME(3) NULL,
    last_imported_count INT NOT NULL DEFAULT 0,
    maintenance_attempted_at DATETIME(3) NULL,
    oauth_state_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    oauth_verifier_encrypted TEXT NULL,
    oauth_expires_at DATETIME(3) NULL,
    oauth_used_at DATETIME(3) NULL,
    UNIQUE KEY uk_activity_user_provider (user_id, provider),
    UNIQUE KEY uk_activity_external_user (provider, external_user_id),
    UNIQUE KEY uk_activity_oauth_state (oauth_state_hash),
    CONSTRAINT fk_activity_connection_user FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE activity_oauth_credential (
    connection_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    access_token_encrypted TEXT NOT NULL,
    refresh_token_encrypted TEXT NOT NULL,
    access_expires_at DATETIME(3) NOT NULL,
    refresh_expires_at DATETIME(3) NULL,
    CONSTRAINT fk_activity_credential_connection FOREIGN KEY (connection_id) REFERENCES activity_connection(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE activity_webhook_inbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_encrypted LONGTEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    UNIQUE KEY uk_activity_event (provider, event_hash),
    KEY ix_activity_inbox_pending (status, available_at, id),
    KEY ix_activity_inbox_retention (processed_at)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
