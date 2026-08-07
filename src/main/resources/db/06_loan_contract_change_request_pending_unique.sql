ALTER TABLE loan_contract_change_request
    ADD COLUMN IF NOT EXISTS pending_lock_key BIGINT AS (CASE WHEN status = 'PENDING' THEN contract_id ELSE NULL END) VIRTUAL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_per_contract
    ON loan_contract_change_request (pending_lock_key);