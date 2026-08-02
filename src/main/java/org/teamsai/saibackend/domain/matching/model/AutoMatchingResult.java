package org.teamsai.saibackend.domain.matching.model;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingDecisionType;

import java.util.List;

public class AutoMatchingResult {

    private final AutoMatchingDecisionType decisionType;
    private final List<MatchingCandidate> matchedCandidates;

    public AutoMatchingResult(List<MatchingCandidate> matchedCandidates) {
        if (matchedCandidates == null) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        this.matchedCandidates = List.copyOf(matchedCandidates);
        this.decisionType = determineDecisionType(this.matchedCandidates);
    }

    public AutoMatchingDecisionType decisionType() {
        return decisionType;
    }

    public List<MatchingCandidate> matchedCandidates() {
        return matchedCandidates;
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

    private AutoMatchingDecisionType determineDecisionType(
            List<MatchingCandidate> matchedCandidates
    ) {
        int matchedCandidateCount = matchedCandidates.size();

        if (matchedCandidateCount == 0) {
            return AutoMatchingDecisionType.UNMATCHED;
        }

        if (matchedCandidateCount == 1) {
            return AutoMatchingDecisionType.MATCHABLE;
        }

        return AutoMatchingDecisionType.NEEDS_CHECK;
    }
}