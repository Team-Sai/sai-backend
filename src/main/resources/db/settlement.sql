CREATE TABLE IF NOT EXISTS settlement (
    settlement_id BIGINT AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    settlement_type ENUM('SHARED', 'RECURRING') NOT NULL,

    settlement_status ENUM('IN_PROGRESS', 'CLOSED')
    NOT NULL DEFAULT 'IN_PROGRESS',

    settlement_category VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,

    split_type ENUM('EQUAL', 'CUSTOM') NULL,
    due_date DATE NULL,

    cycle_rule ENUM('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY') NULL,
    start_date DATE NULL,
    end_date DATE NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME NULL,

    status ENUM('RECEIVABLE', 'PAYABLE') NULL,
    cycle_date DATE NULL,
    invitation_token VARCHAR(255) NULL,

    PRIMARY KEY (settlement_id),

    INDEX idx_settlement_owner_id (owner_id),
    INDEX idx_settlement_type (settlement_type),
    INDEX idx_settlement_status (settlement_status),
    INDEX idx_settlement_created_at (created_at)
    )
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;