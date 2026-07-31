package org.teamsai.saibackend.domain.matching;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;

import java.math.BigDecimal;

public record MatchingTransaction(
        Long transactionId,
        AutoMatchingTransactionType transactionType,
        BigDecimal amount,
        String counterpartyName
) {

    public MatchingTransaction {
        if (transactionId == null
                || transactionType == null
                || amount == null
                || counterpartyName == null
                || counterpartyName.isBlank()
                || amount.signum() <= 0) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }
}
