CREATE TABLE IF NOT EXISTS identity_verification (
                                       identity_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       identity_verification_id VARCHAR(100) NOT NULL UNIQUE,
                                       user_id BIGINT NOT NULL,
                                       purpose VARCHAR(30) NOT NULL,
                                       status VARCHAR(20) NOT NULL,
                                       requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       verified_at DATETIME NULL,
                                       expires_at DATETIME NULL,
                                       used_at DATETIME NULL,

                                       failure_reason VARCHAR(255) NULL,

                                       INDEX idx_identity_user_purpose_status (
        user_id,
        purpose,
        status
    )
);