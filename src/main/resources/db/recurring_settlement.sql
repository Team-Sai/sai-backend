CREATE TABLE recurring_settlement (
    recurring_settlement_id BIGINT AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    settlement_category VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    split_type ENUM('EQUAL', 'CUSTOM') NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL
    CHECK (total_amount > 0),
    cycle_rule ENUM(
        'DAILY',
        'WEEKLY',
        'MONTHLY',
        'YEARLY'
    ) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    created_at DATETIME NOT NULL
     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recurring_settlement_id),

    INDEX idx_recurring_settlement_owner_id (owner_id)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;