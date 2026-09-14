package org.teamsai.saibackend.domain.matching.service;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;

public record EvaluatedMatchingCandidate(
        MatchingCandidate candidate,
        MatchingAmountType amountMatchType
) {

    public EvaluatedMatchingCandidate {
        if (candidate == null || amountMatchType == null) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }
}
