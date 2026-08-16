CREATE TABLE IF NOT EXISTS notification (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(500) NOT NULL,
    reference_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),

    KEY idx_notification_user_created (user_id, created_at),
    CONSTRAINT fk_notification_user
    FOREIGN KEY (user_id)
    REFERENCES users (user_id)
    )
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

ALTER TABLE notification
     ADD COLUMN IF NOT EXISTS secondary_reference_id BIGINT NULL;