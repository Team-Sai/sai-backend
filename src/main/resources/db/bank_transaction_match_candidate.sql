CREATE TABLE IF NOT EXISTS bank_transaction_match_candidate (
    match_candidate_id BIGINT NOT NULL AUTO_INCREMENT,
    bank_transaction_id BIGINT NOT NULL,

    target_type VARCHAR(30) NOT NULL CHECK (
        target_type IN ('SETTLEMENT', 'LOAN')
    ),
    target_id BIGINT NOT NULL,

    expected_remaining_amount DECIMAL(19, 2) NOT NULL CHECK (
        expected_remaining_amount > 0
    ),

    amount_match_type VARCHAR(30) NOT NULL CHECK (
        amount_match_type IN ('EXACT', 'PARTIAL', 'EXCESS')
    ),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (match_candidate_id),

    CONSTRAINT uk_bank_transaction_match_candidate
        UNIQUE (
            bank_transaction_id,
            target_type,
            target_id
        ),

    CONSTRAINT fk_match_candidate_bank_transaction
        FOREIGN KEY (bank_transaction_id)
        REFERENCES bank_transaction (bank_transaction_id)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;
