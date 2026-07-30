package org.teamsai.saibackend.domain.settlement.matching;

import java.math.BigDecimal;
import java.util.Objects;

public record AutoMatchingTransaction(
        Long transactionId,
        AutoMatchingTransactionType transactionType,
        BigDecimal amount,
        String counterpartyName
) {

    public AutoMatchingTransaction {
        Objects.requireNonNull(
                transactionId,
                "transactionId은 null일 수 없습니다."
        );

        Objects.requireNonNull(
                transactionType,
                "transactionType은 null일 수 없습니다."
        );

        Objects.requireNonNull(
                amount,
                "amount은 null일 수 없습니다."
        );

        Objects.requireNonNull(
                counterpartyName,
                "counterpartyName은 null일 수 없습니다."
        );

        if (counterpartyName.isBlank()) {
            throw new IllegalArgumentException(
                    "counterpartyName은 빈값일 수 없습니다."
            );
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount는 0보다 커야합니다"
            );
        }
    }
}
