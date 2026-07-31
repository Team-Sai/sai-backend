package org.teamsai.saibackend.domain.matching;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;

import java.util.List;

public record AutoMatchingResult(
        AutoMatchingDecisionType decisionType,
        List<MatchingCandidate> matchedCandidates
) {

    public AutoMatchingResult {
        if (decisionType == null || matchedCandidates == null) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        matchedCandidates = List.copyOf(matchedCandidates);
    }

    public boolean isMatchable() {
        return decisionType == AutoMatchingDecisionType.MATCHABLE;
    }

    public boolean needsCheck() {
        return decisionType == AutoMatchingDecisionType.NEEDS_CHECK;
    }

    public boolean isUnmatched() {
        return decisionType == AutoMatchingDecisionType.UNMATCHED;
    }
}
