CREATE TABLE IF NOT EXISTS repayment_schedule (
    schedule_id BIGINT NOT NULL AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    sequence INT NOT NULL,
    due_date DATE NOT NULL,
    principal_due DECIMAL(15, 2) NOT NULL,
    interest_due DECIMAL(15, 2) NOT NULL,
    total_payment_due DECIMAL(15, 2) NOT NULL,
    remaining_principal DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID')),
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (schedule_id),
    CONSTRAINT fk_repayment_schedule_contract FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id) ON DELETE CASCADE
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;

ALTER TABLE repayment_schedule
    MODIFY COLUMN status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'OVERDUE', 'PAID', 'WRITTEN_OFF'));