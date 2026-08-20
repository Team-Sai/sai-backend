package org.teamsai.saibackend.domain.matching.type;

import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;

public class RetryPolicy {

    private RetryPolicy() {
    }

    public static int maxRetryCount(BankTransactionProcessingStatus status) {
        return switch (status) {
            case FAILED -> 5;
            case UNMATCHED, NEEDS_CHECK -> 14;
            default -> 0;
        };
    }
}