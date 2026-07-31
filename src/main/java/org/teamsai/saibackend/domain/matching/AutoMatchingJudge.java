package org.teamsai.saibackend.domain.matching;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;

import java.util.List;

public class AutoMatchingJudge {

    public AutoMatchingResult judge(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        validateInput(transaction, candidates);

        if (transaction.transactionType() != AutoMatchingTransactionType.DEPOSIT) {
            return new AutoMatchingResult(
                    AutoMatchingDecisionType.UNMATCHED,
                    List.of()
            );
        }

        List<MatchingCandidate> matchedCandidates = candidates.stream()
                .filter(candidate -> isMatched(transaction, candidate))
                .toList();

        return new AutoMatchingResult(
                determineDecisionType(matchedCandidates.size()),
                matchedCandidates
        );
    }

    private AutoMatchingDecisionType determineDecisionType(
            int matchedCandidateCount
    ) {
        if (matchedCandidateCount == 0) {
            return AutoMatchingDecisionType.UNMATCHED;
        }

        if (matchedCandidateCount == 1) {
            return AutoMatchingDecisionType.MATCHABLE;
        }

        return AutoMatchingDecisionType.NEEDS_CHECK;
    }

    private boolean isMatched(
            MatchingTransaction transaction,
            MatchingCandidate candidate
    ) {
        return isAmountMatched(transaction, candidate)
                && isParticipantNameMatched(transaction, candidate);
    }

    private boolean isAmountMatched(
            MatchingTransaction transaction,
            MatchingCandidate candidate
    ) {
        return transaction.amount()
                .compareTo(candidate.remainingAmount()) == 0;
    }

    private boolean isParticipantNameMatched(
            MatchingTransaction transaction,
            MatchingCandidate candidate
    ) {
        return normalizeName(transaction.counterpartyName())
                .equals(normalizeName(candidate.participantName()));
    }

    private String normalizeName(String name) {
        return name.replaceAll("\\s+", "");
    }

    private void validateInput(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        if (transaction == null || candidates == null) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        if (candidates.stream().anyMatch(candidate -> candidate == null)) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }
}
