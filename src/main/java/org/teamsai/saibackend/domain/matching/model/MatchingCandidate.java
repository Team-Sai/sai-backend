package org.teamsai.saibackend.domain.matching.model;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;

import java.math.BigDecimal;

public record MatchingCandidate(
        MatchingTargetType targetType,
        Long targetId,
        Long participantId,
        String participantName,
        BigDecimal remainingAmount
) {

    public MatchingCandidate {
        if (targetType == null
                || targetId == null
                || participantId == null
                || participantName == null
                || remainingAmount == null
                || participantName.isBlank()
                || remainingAmount.signum() <= 0) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }
}
