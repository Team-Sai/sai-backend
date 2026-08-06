CREATE TABLE IF NOT EXITSTS identity (
                            identity_id bigint NOT NULL AUTO_INCREMENT,
                            identity_verification_id varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                            user_id bigint NOT NULL,
                            purpose varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
                            status varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
                            requested_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            verified_at datetime DEFAULT NULL,
                            expires_at datetime DEFAULT NULL,
                            used_at datetime DEFAULT NULL,
                            failure_reason varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                            PRIMARY KEY (identity_id),
                            UNIQUE KEY uk_identity_verification_id (identity_verification_id),
                            KEY idx_identity_user_purpose_status (user_id,purpose,status),
                            CONSTRAINT fk_identity_verification_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci