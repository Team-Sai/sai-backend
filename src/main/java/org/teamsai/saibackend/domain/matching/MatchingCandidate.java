package org.teamsai.saibackend.domain.matching;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;

import java.math.BigDecimal;

public record MatchingCandidate(
        Long obligationId,
        Long participantId,
        String participantName,
        BigDecimal remainingAmount
) {

    public MatchingCandidate {
        if (obligationId == null
                || participantId == null
                || participantName == null
                || remainingAmount == null
                || participantName.isBlank()
                || remainingAmount.signum() <= 0) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }
}
