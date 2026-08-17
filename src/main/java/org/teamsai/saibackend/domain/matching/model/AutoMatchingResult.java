package org.teamsai.saibackend.domain.matching.model;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingDecisionType;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;

import java.util.List;

public class AutoMatchingResult {

    private final AutoMatchingDecisionType decisionType;
    private final List<EvaluatedMatchingCandidate> evaluatedCandidates;

    public AutoMatchingResult(
            List<EvaluatedMatchingCandidate> evaluatedCandidates
    ) {
        if (evaluatedCandidates == null
                || evaluatedCandidates.stream()
                .anyMatch(candidate -> candidate == null)) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        this.evaluatedCandidates = List.copyOf(evaluatedCandidates);
        this.decisionType = determineDecisionType(this.evaluatedCandidates);
    }

    public AutoMatchingDecisionType decisionType() {
        return decisionType;
    }

    public List<EvaluatedMatchingCandidate> evaluatedCandidates() {
        return evaluatedCandidates;
    }

    public EvaluatedMatchingCandidate matchedCandidate() {
        if (!isMatchable()) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        return evaluatedCandidates.get(0);
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
            List<EvaluatedMatchingCandidate> evaluatedCandidates
    ) {
        if (evaluatedCandidates.isEmpty()) {
            return AutoMatchingDecisionType.UNMATCHED;
        }

        if (evaluatedCandidates.size() == 1
                && evaluatedCandidates.get(0).amountMatchType()
                == MatchingAmountType.EXACT) {
            return AutoMatchingDecisionType.MATCHABLE;
        }

        return AutoMatchingDecisionType.NEEDS_CHECK;
    }
}
