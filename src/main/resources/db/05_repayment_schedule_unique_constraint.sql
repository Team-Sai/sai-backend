CREATE UNIQUE INDEX IF NOT EXISTS uq_repayment_schedule_contract_sequence
    ON repayment_schedule (contract_id, sequence);