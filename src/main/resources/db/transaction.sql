CREATE TABLE IF NOT EXISTS bank_transaction (
    bank_transaction_id BIGINT NOT NULL AUTO_INCREMENT,

    linked_account_id BIGINT NOT NULL,
    external_transaction_id VARCHAR(100) NOT NULL,

    amount DECIMAL(19, 2) NOT NULL CHECK (amount > 0),

    transaction_type VARCHAR(20) NOT NULL CHECK (
        transaction_type IN ('DEPOSIT', 'WITHDRAWAL')
    ),

    processing_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (
        processing_status IN (
            'PENDING',
            'APPLIED',
            'UNMATCHED',
            'NEEDS_CHECK',
            'FAILED'
        )
    ),

    transaction_at DATETIME NOT NULL,
    counterparty_name VARCHAR(50) NULL,
    memo VARCHAR(50) NULL,
    synced_at DATETIME NOT NULL,

    PRIMARY KEY (bank_transaction_id),

    CONSTRAINT uk_bank_transaction_linked_external
        UNIQUE (linked_account_id, external_transaction_id),

    INDEX idx_bank_transaction_matching_target (
        processing_status,
        transaction_type,
        transaction_at
    ),

    INDEX idx_bank_transaction_linked_account (
       linked_account_id
    )
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;