CREATE TABLE IF NOT EXISTS payment_obligation (
    payment_obligation_id BIGINT NOT NULL AUTO_INCREMENT,
    participant_id BIGINT NOT NULL,
    expected_amount DECIMAL(19, 2) NOT NULL CHECK (expected_amount > 0),
    payment_status VARCHAR(30) NOT NULL CHECK (
        payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')
    ),
    review_status VARCHAR(30) NOT NULL CHECK (
        review_status IN ('NORMAL', 'NEEDS_CHECK')
    ),
    obligation_status VARCHAR(30) NOT NULL CHECK (
        obligation_status IN ('ACTIVE', 'EXCLUDED', 'CANCELLED')
    ),

    PRIMARY KEY (payment_obligation_id)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'payment_obligation'
      AND INDEX_NAME = 'uq_payment_obligation_participant'
);

SET @sql = IF(@constraint_exists = 0,
              'ALTER TABLE payment_obligation ADD CONSTRAINT uq_payment_obligation_participant UNIQUE (participant_id)',
              'SELECT ''uq_payment_obligation_participant already exists'' AS message'
           );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS payment_record (
    payment_record_id BIGINT NOT NULL AUTO_INCREMENT,
    bank_transaction_id BIGINT NOT NULL,

    payment_target_type VARCHAR(30) NOT NULL CHECK (
        payment_target_type IN ('SETTLEMENT', 'LOAN')
    ),
    target_id BIGINT NOT NULL,

    amount DECIMAL(19, 2) NOT NULL CHECK (amount > 0),
    source_type VARCHAR(30) NOT NULL CHECK (
        source_type IN ('AUTO_MATCH', 'MANUAL')
    ),
    record_status VARCHAR(30) NOT NULL CHECK (
        record_status IN ('CONFIRMED', 'CANCELLED')
    ),
    recorded_at DATETIME NOT NULL,
    cancelled_by_id BIGINT NULL,
    cancelled_at DATETIME NULL,
    memo VARCHAR(500) NULL,

    PRIMARY KEY (payment_record_id),
    CONSTRAINT uk_payment_record_bank_transaction
        UNIQUE (bank_transaction_id),

    INDEX idx_payment_target_target(
        payment_target_type,
        target_id,
        record_status
        )
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;
