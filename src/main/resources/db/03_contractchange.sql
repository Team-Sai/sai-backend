CREATE TABLE IF NOT EXISTS loan_contract_change_request (
    change_request_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    change_reason TEXT NULL,
    new_maturity_date DATE NULL,
    new_interest_rate DECIMAL(5, 2) NULL CHECK (new_interest_rate >= 0),
    new_repayment_type VARCHAR(30) NULL,
    new_repayment_date INT NULL,
    new_terms TEXT NULL,
    return_reason TEXT NULL,
    status VARCHAR(20) NOT NULL CHECK (
    status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    contract_id BIGINT NOT NULL,

    PRIMARY KEY (change_request_id),

    CONSTRAINT fk_change_request_contract FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
    CONSTRAINT fk_change_request_user FOREIGN KEY (user_id) REFERENCES users(user_id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;

ALTER TABLE loan_contract_change_request
    ADD COLUMN IF NOT EXISTS new_terms TEXT NULL AFTER new_repayment_date;

ALTER TABLE loan_contract_change_request
    ADD COLUMN IF NOT EXISTS pending_lock_key BIGINT AS (CASE WHEN status = 'PENDING' THEN contract_id ELSE NULL END) VIRTUAL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_per_contract
    ON loan_contract_change_request (pending_lock_key);

ALTER TABLE loan_contract_change_request
    ADD COLUMN IF NOT EXISTS return_reason TEXT NULL AFTER new_terms;

ALTER TABLE loan_contract_change_request
    MODIFY COLUMN new_repayment_type VARCHAR(30) NULL;