CREATE TABLE IF NOT EXISTS identity (
    identity_id BIGINT NOT NULL AUTO_INCREMENT,
    identity_verification_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at DATETIME NULL,
    expires_at DATETIME NULL,
    used_at DATETIME NULL,
    failure_reason VARCHAR(255) NULL,
    PRIMARY KEY (identity_id),
    UNIQUE KEY uk_identity_verification_id (
        identity_verification_id
    ),

    INDEX idx_identity_user_purpose_status (
        user_id,
        purpose,
        status
    ),

    CONSTRAINT fk_identity_verification_user
    FOREIGN KEY (user_id)
    REFERENCES users(user_id)
    ON DELETE CASCADE
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;