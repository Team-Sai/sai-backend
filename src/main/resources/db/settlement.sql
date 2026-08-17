CREATE TABLE IF NOT EXISTS settlement (
    settlement_id BIGINT AUTO_INCREMENT,
    recurring_settlement_id BIGINT NULL,
    owner_id BIGINT NOT NULL,
    settlement_type ENUM('SHARED', 'RECURRING') NOT NULL,

    settlement_status ENUM('IN_PROGRESS', 'CLOSED')
    NOT NULL DEFAULT 'IN_PROGRESS',

    settlement_category VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,

    split_type ENUM('EQUAL', 'CUSTOM') NULL,

    total_amount DECIMAL(19, 2) NOT NULL
    CHECK (total_amount > 0),
    due_date DATE NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME NULL,

    status ENUM('RECEIVABLE', 'PAYABLE') NULL,
    cycle_date DATE NULL,
    invitation_token VARCHAR(255) NULL,

    PRIMARY KEY (settlement_id),

    INDEX idx_settlement_owner_id (owner_id),
    INDEX idx_settlement_recurring_id (recurring_settlement_id),
    INDEX idx_settlement_type (settlement_type),
    INDEX idx_settlement_status (settlement_status),
    INDEX idx_settlement_created_at (created_at),

    CONSTRAINT fk_settlement_recurring
        FOREIGN KEY (recurring_settlement_id)
        REFERENCES recurring_settlement(recurring_settlement_id)
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement' AND COLUMN_NAME = 'recurring_settlement_id'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE settlement ADD COLUMN recurring_settlement_id BIGINT NULL',
    'SELECT ''recurring_settlement_id already exists'' AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement' AND COLUMN_NAME = 'cycle_date'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE settlement ADD COLUMN cycle_date DATE NULL',
    'SELECT ''cycle_date already exists'' AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;