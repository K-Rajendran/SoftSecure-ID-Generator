-- Additive schema. Run manually as a schema owner after a verified backup.
-- Does not read, alter, or delete LOGIN, student, or faculty.
CREATE TABLE IF NOT EXISTS ss_users (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    username_tag CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    username_enc TEXT NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii NOT NULL,
    role ENUM('ADMIN','STUDENT','FACULTY') NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    session_version INT NOT NULL DEFAULT 1,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ss_requests (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    profile_enc MEDIUMBLOB NOT NULL,
    status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    version INT NOT NULL DEFAULT 1,
    submitted_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reviewed_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reason_enc TEXT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
    token_enc TEXT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    CONSTRAINT ss_request_owner FOREIGN KEY (user_id) REFERENCES ss_users(id),
    CONSTRAINT ss_request_submitter FOREIGN KEY (submitted_by) REFERENCES ss_users(id),
    CONSTRAINT ss_request_reviewer FOREIGN KEY (reviewed_by) REFERENCES ss_users(id),
    INDEX ss_queue (status, submitted_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ss_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action VARCHAR(40) CHARACTER SET ascii NOT NULL,
    target_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ss_audit_time (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
