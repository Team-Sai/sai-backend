package org.teamsai.saibackend.domain.matching;

import java.math.BigDecimal;
import java.util.Objects;

public record MatchingCandidate(
        Long obligationId,
        Long participantId,
        String participantName,
        BigDecimal remainingAmount
) {

    public MatchingCandidate {
        Objects.requireNonNull(obligationId, "obligationId는 null일 수 없습니다.");
        Objects.requireNonNull(participantId, "participantId는 null일 수 없습니다.");
        Objects.requireNonNull(participantName, "participantName은 null일 수 없습니다.");
        Objects.requireNonNull(remainingAmount, "remainingAmount는 null일 수 없습니다.");

        if (participantName.isBlank()) {
            throw new IllegalArgumentException("participantName은 빈 값일 수 없습니다.");
        }

        if (remainingAmount.signum() <= 0) {
            throw new IllegalArgumentException("remainingAmount는 0보다 커야 합니다.");
        }
    }
}
