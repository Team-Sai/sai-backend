package org.teamsai.saibackend.domain.settlement.matching;

import java.math.BigDecimal;
import java.util.Objects;

public record AutoMatchingObligationCandidate(
        Long obligationId,
        Long participantId,
        String participantName,
        BigDecimal remainingAmount
) {

    public AutoMatchingObligationCandidate {
        Objects.requireNonNull(
                obligationId,
                "obligationId은 null일 수 없습니다."
        );

        Objects.requireNonNull(
                participantId,
                "participantId은 null일 수 없습니다."
        );

        Objects.requireNonNull(
                participantName,
                "participantName은 null일 수 없습니다."
        );

        Objects.requireNonNull(
                remainingAmount,
                "remainingAmount은 null일 수 없습니다."
        );

        if (participantName.isBlank()) {
            throw new IllegalArgumentException(
                    "participantName은 빈 값일 수 없습니다."
            );
        }

        if (remainingAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "remainingAmount은 0보다 커야합니다."
            );
        }
    }
}
