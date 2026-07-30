package org.teamsai.saibackend.domain.matching;

import java.util.List;
import java.util.Objects;

public record AutoMatchingResult(
        AutoMatchingDecisionType decisionType,
        List<MatchingCandidate> matchedCandidates
) {

    public AutoMatchingResult {
        Objects.requireNonNull(decisionType, "decisionType은 null일 수 없습니다.");
        Objects.requireNonNull(matchedCandidates, "matchedCandidates는 null일 수 없습니다.");

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
