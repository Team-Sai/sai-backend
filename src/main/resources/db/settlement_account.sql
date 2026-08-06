CREATE TABLE IF NOT EXISTS settlement_account (
    settlement_account_id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_id BIGINT NOT NULL,
    linked_account_id BIGINT NOT NULL,
    account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (
        account_status IN ('ACTIVE', 'REPLACED', 'DISABLED')
    ),

    selected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at DATETIME NULL,

    active_key TINYINT
        GENERATED ALWAYS AS (
            CASE
                WHEN account_status = 'ACTIVE'
                    AND ended_at IS NULL
                THEN 1
                ELSE NULL
            END
        ) STORED,

    PRIMARY KEY (settlement_account_id),

    INDEX idx_settlement_account_linked_account (
        linked_account_id,
        account_status,
        ended_at
    ),

    INDEX idx_settlement_account_active (
        settlement_id,
        account_status,
        ended_at
    ),

    CONSTRAINT uk_settlement_account_active
    UNIQUE (
               settlement_id,
               active_key
           ),

    CONSTRAINT fk_settlement_account_settlement
        FOREIGN KEY (settlement_id)
        REFERENCES settlement (settlement_id),

    CONSTRAINT fk_settlement_account_linked_account
        FOREIGN KEY (linked_account_id)
        REFERENCES linked_bank_account (linked_account_id)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;
