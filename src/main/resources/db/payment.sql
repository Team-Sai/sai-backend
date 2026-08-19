CREATE TABLE IF NOT EXISTS payment_obligation (
      payment_obligation_id BIGINT NOT NULL AUTO_INCREMENT,
      participant_id BIGINT NOT NULL,
      expected_amount DECIMAL(19, 2) NOT NULL CHECK (expected_amount > 0),
    payment_status ENUM('UNPAID', 'PARTIALLY_PAID', 'PAID') NOT NULL,
    review_status ENUM('NORMAL', 'NEEDS_CHECK') NOT NULL,
    obligation_status ENUM('ACTIVE', 'EXCLUDED', 'CANCELLED') NOT NULL,
    overdue_since DATETIME NULL,

    PRIMARY KEY (payment_obligation_id),

    INDEX idx_payment_obligation_participant_status (
                                                        participant_id,
                                                        obligation_status,
                                                        payment_status
                                                    )
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS payment_record (
                                              payment_record_id BIGINT NOT NULL AUTO_INCREMENT,
                                              bank_transaction_id BIGINT NOT NULL,
                                              payment_target_type ENUM('SETTLEMENT', 'LOAN') NOT NULL,
    target_id BIGINT NOT NULL,

    amount DECIMAL(19, 2) NOT NULL CHECK (amount > 0),
    source_type ENUM('AUTO_MATCH', 'MANUAL') NOT NULL,
    record_status ENUM('CONFIRMED', 'CANCELLED') NOT NULL,
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