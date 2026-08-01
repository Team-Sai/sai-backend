CREATE TABLE IF NOT EXISTS users (
                                     user_id BIGINT NOT NULL AUTO_INCREMENT,
                                     user_key VARCHAR(36) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_user_key (user_key),
    UNIQUE KEY uk_users_email (email)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment_obligation (
    payment_obligation_id BIGINT NOT NULL AUTO_INCREMENT,
    participant_id BIGINT NOT NULL,
    expected_amount DECIMAL(15, 2) NOT NULL,
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

CREATE TABLE IF NOT EXISTS payment_record (
    payment_record_id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_bank_transaction_id BIGINT NOT NULL,
    obligation_id BIGINT NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    source_type VARCHAR(30) NOT NULL CHECK (
        source_type IN ('AUTO_MATCH', 'MANUAL')
    ),
    record_status VARCHAR(30) NOT NULL CHECK (
        record_status IN ('CONFIRMED', 'CANCELED')
    ),
    recorded_at DATETIME NOT NULL,
    canceled_by_id BIGINT NULL,
    canceled_at DATETIME NULL,
    memo VARCHAR(500) NULL,

    PRIMARY KEY (payment_record_id),
    CONSTRAINT uk_payment_record_transaction
        UNIQUE (settlement_bank_transaction_id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;
