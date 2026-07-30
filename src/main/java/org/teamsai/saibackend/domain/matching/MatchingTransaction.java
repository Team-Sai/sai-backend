package org.teamsai.saibackend.domain.matching;

import java.math.BigDecimal;
import java.util.Objects;

public record MatchingTransaction(
        Long transactionId,
        AutoMatchingTransactionType transactionType,
        BigDecimal amount,
        String counterpartyName
) {

    public MatchingTransaction {
        Objects.requireNonNull(transactionId, "transactionId는 null일 수 없습니다.");
        Objects.requireNonNull(transactionType, "transactionType은 null일 수 없습니다.");
        Objects.requireNonNull(amount, "amount는 null일 수 없습니다.");
        Objects.requireNonNull(counterpartyName, "counterpartyName은 null일 수 없습니다.");

        if (counterpartyName.isBlank()) {
            throw new IllegalArgumentException("counterpartyName은 빈 값일 수 없습니다.");
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount는 0보다 커야 합니다.");
        }
    }
}
