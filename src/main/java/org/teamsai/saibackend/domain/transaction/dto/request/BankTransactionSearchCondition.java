package org.teamsai.saibackend.domain.transaction.dto.request;

import org.teamsai.saibackend.domain.transaction.exception.BankTransactionErrorCode;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.time.LocalDate;

public record BankTransactionSearchCondition(
        BankTransactionProcessingStatus processingStatus,
        BankTransactionType transactionType,
        String keyword,
        LocalDate fromDate,
        LocalDate toDate,
        long page,
        long size
) {
    public BankTransactionSearchCondition {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0 || size > 100) {
            size = 20;
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw BankTransactionErrorCode.INVALID_DATE_RANGE.toException();
        }
        keyword = normalizeKeyword(keyword);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public long offset() {
        return page * size;
    }
}
