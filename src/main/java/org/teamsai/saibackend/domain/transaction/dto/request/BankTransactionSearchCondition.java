package org.teamsai.saibackend.domain.transaction.dto.request;

import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.time.LocalDate;

public record BankTransactionSearchCondition(
        BankTransactionProcessingStatus processingStatus,
        BankTransactionType transactionType,
        String keyword,
        LocalDate fromDate,
        LocalDate toDate,
        int page,
        int size
) {
    public BankTransactionSearchCondition {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0 || size > 100) {
            size = 20;
        }
    }

    public int offset() {
        return page * size;
    }
}
