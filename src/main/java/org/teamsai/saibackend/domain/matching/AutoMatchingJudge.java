package org.teamsai.saibackend.domain.matching;

import java.util.List;
import java.util.Objects;

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

    //입금자 명은 추가 확인 필요
    private String normalizeName(String name) {
        return name.replaceAll("\\s+", "");
    }

    private void validateInput(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        Objects.requireNonNull(transaction, "transaction은 null일 수 없습니다.");
        Objects.requireNonNull(candidates, "candidates는 null일 수 없습니다.");

        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("candidates에는 null 요소가 포함될 수 없습니다.");
        }
    }
}
