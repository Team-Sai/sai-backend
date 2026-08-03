CREATE TABLE IF NOT EXISTS loan_contract (
                                             contract_id BIGINT NOT NULL AUTO_INCREMENT,
                                             previous_contract_id BIGINT NULL,
                                             principal_amount DECIMAL(15, 2) NOT NULL CHECK (principal_amount > 0),
    interest_rate DECIMAL(5, 2) NOT NULL CHECK (interest_rate >= 0),
    repayment_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    maturity_date DATE NOT NULL,
    repayment_day INT NULL,
    status VARCHAR(20) NOT NULL,
    address VARCHAR(255) NULL,
    contract_alias VARCHAR(20) NULL,
    terms VARCHAR(300) NULL,
    creditor_id BIGINT NOT NULL,
    debtor_id BIGINT NOT NULL,
    creditor_signature VARCHAR(255) NULL,
    debtor_signature VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (contract_id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS loan_contract_change_request (
                                                            change_request_id BIGINT NOT NULL AUTO_INCREMENT,
                                                            user_id BIGINT NOT NULL,
                                                            change_reason TEXT NULL,
                                                            new_maturity_date DATE NULL,
                                                            new_interest_rate DECIMAL(5, 2) NULL CHECK (new_interest_rate >= 0),
    new_repayment_type VARCHAR(20) NULL,
    new_repayment_date DATE NULL,
    status VARCHAR(20) NOT NULL CHECK (
                                          status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
    contract_id BIGINT NOT NULL,

    PRIMARY KEY (change_request_id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;