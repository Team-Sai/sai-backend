CREATE TABLE IF NOT EXISTS loan_contract (
    contract_id          BIGINT NOT NULL AUTO_INCREMENT,
    previous_contract_id BIGINT NULL,

    creditor_id          BIGINT NOT NULL,
    debtor_id            BIGINT NOT NULL,

    principal_amount     DECIMAL(15,2) NOT NULL,
    interest_rate        DECIMAL(5,2) NOT NULL,
    repayment_type       VARCHAR(30) NOT NULL,
    start_date           DATE NOT NULL,
    maturity_date        DATE NOT NULL,
    repayment_day        INT NOT NULL,

    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    creditor_address     VARCHAR(255) NOT NULL,
    debtor_address       VARCHAR(255) NULL,
    contract_alias       VARCHAR(100) NOT NULL,
    terms                TEXT NULL,

    creditor_signature   VARCHAR(500) NULL,
    debtor_signature     VARCHAR(500) NULL,

    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (contract_id),
    KEY idx_loan_contract_creditor_id (creditor_id),
    KEY idx_loan_contract_debtor_id (debtor_id),
    CONSTRAINT fk_loan_contract_previous FOREIGN KEY (previous_contract_id) REFERENCES loan_contract (contract_id),
    CONSTRAINT fk_loan_contract_creditor FOREIGN KEY (creditor_id) REFERENCES users (user_id),
    CONSTRAINT fk_loan_contract_debtor FOREIGN KEY (debtor_id) REFERENCES users (user_id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS loan_contract_file (
    file_id           BIGINT NOT NULL AUTO_INCREMENT,
    contract_id       BIGINT NOT NULL,

    original_filename VARCHAR(255) NOT NULL,
    saved_filename    VARCHAR(255) NOT NULL,
    file_size         BIGINT NOT NULL,
    file_type         VARCHAR(100) NULL,

    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (file_id),
    KEY idx_loan_contract_file_contract_id (contract_id),
    CONSTRAINT fk_loan_contract_file_contract FOREIGN KEY (contract_id) REFERENCES loan_contract (contract_id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;
