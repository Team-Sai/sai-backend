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

    PRIMARY KEY (change_request_id),

    CONSTRAINT fk_change_request_contract FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;