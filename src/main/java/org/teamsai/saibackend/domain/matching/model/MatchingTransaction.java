package org.teamsai.saibackend.domain.matching.model;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MatchingTransaction(
        Long transactionId,
        AutoMatchingTransactionType transactionType,
        BigDecimal amount,
        String counterpartyName,
        LocalDateTime transactionAt
) {

    public MatchingTransaction {
        if (transactionId == null
                || transactionType == null
                || amount == null
                || counterpartyName == null
                || counterpartyName.isBlank()
                || amount.signum() <= 0
                || transactionAt == null) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }
}
