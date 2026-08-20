CREATE TABLE IF NOT EXISTS payment_obligation (
                                                  payment_obligation_id  BIGINT          NOT NULL AUTO_INCREMENT,
                                                  participant_id         BIGINT          NOT NULL,
                                                  expected_amount        DECIMAL(19, 2)  NOT NULL,
    payment_status         ENUM('UNPAID', 'PARTIALLY_PAID', 'PAID') NOT NULL,
    review_status          ENUM('NORMAL', 'NEEDS_CHECK') NOT NULL,
    obligation_status      ENUM('ACTIVE', 'EXCLUDED', 'CANCELLED') NOT NULL,
    overdue_since          DATETIME        NULL,

    PRIMARY KEY (payment_obligation_id),

    INDEX idx_payment_obligation_participant_status (
                                                        participant_id,
                                                        obligation_status,
                                                        payment_status
                                                    ),

    CONSTRAINT chk_payment_obligation_amount CHECK (expected_amount > 0)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


ALTER TABLE payment_obligation
    MODIFY COLUMN obligation_status VARCHAR(20) NOT NULL
        CHECK (obligation_status IN ('ACTIVE', 'EXCLUDED', 'CANCELLED', 'WRITTEN_OFF'));

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

SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'settlement'
      AND CONSTRAINT_NAME = 'chk_recurring_fields'
);

SET @sql = IF(@constraint_exists = 0,
              'ALTER TABLE settlement ADD CONSTRAINT chk_recurring_fields
                  CHECK (
                      settlement_type != ''RECURRING''
                      OR (recurring_settlement_id IS NOT NULL AND cycle_date IS NOT NULL)
                  )',
              'SELECT ''chk_recurring_fields already exists'' AS message'
           );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;