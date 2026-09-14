package org.teamsai.saibackend.domain.transaction.type;

public enum BankTransactionProcessingStatus {
    PENDING,
    APPLIED,
    UNMATCHED,
    NEEDS_CHECK,
    FAILED
}
