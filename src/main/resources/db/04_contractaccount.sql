CREATE TABLE IF NOT EXISTS contract_account (
    contract_account_id BIGINT NOT NULL AUTO_INCREMENT,
    linked_account_id BIGINT NOT NULL,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    selected_at DATETIME NOT NULL,
    ended_at DATETIME NULL,
    contract_id BIGINT NOT NULL,

    CONSTRAINT pk_contract_account PRIMARY KEY (contract_account_id),
    CONSTRAINT fk_contract_account_linked_account FOREIGN KEY (linked_account_id) REFERENCES linked_bank_account (linked_account_id),
    CONSTRAINT fk_contract_account_loan_contract FOREIGN KEY (contract_id) REFERENCES loan_contract (contract_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
