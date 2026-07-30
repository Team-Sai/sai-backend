package org.teamsai.saibackend.domain.settlement.matching;

import java.util.List;
import java.util.Objects;

public class AutoMatchingJudge {

    public AutoMatchingResult judge(
            AutoMatchingTransaction transaction,
            List<AutoMatchingObligationCandidate> candidates
    ) {
        validateInput(transaction, candidates);

        if (transaction.transactionType()
                != AutoMatchingTransactionType.DEPOSIT) {
            return new AutoMatchingResult(
                    AutoMatchingDecisionType.UNMATCHED,
                    List.of()
            );
        }

        List<AutoMatchingObligationCandidate> matchedCandidates =
                candidates.stream()
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
            AutoMatchingTransaction transaction,
            AutoMatchingObligationCandidate candidate
    ) {
        return isAmountMatched(transaction, candidate)
                && isParticipantNameMatched(transaction, candidate);
    }

    private boolean isAmountMatched(
            AutoMatchingTransaction transaction,
            AutoMatchingObligationCandidate candidate
    ) {
        return transaction.amount()
                .compareTo(candidate.remainingAmount()) == 0;
    }

    private boolean isParticipantNameMatched(
            AutoMatchingTransaction transaction,
            AutoMatchingObligationCandidate candidate
    ) {
        return normalizeName(transaction.counterpartyName())
                .equals(normalizeName(candidate.participantName()));
    }

    //입금자명 부분 개선 예정
    private String normalizeName(String name) {
        return name.replaceAll("\\s+", "");
    }

    private void validateInput(
            AutoMatchingTransaction transaction,
            List<AutoMatchingObligationCandidate> candidates
    ) {
        Objects.requireNonNull(
                transaction,
                "transaction은 null일 수 없습니다."
        );

        Objects.requireNonNull(
                candidates,
                "candidates은 null일 수 없습니다."
        );

        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "candidates에는 null 요소가 포함되어서는 안 됩니다."
            );
        }
    }
}
