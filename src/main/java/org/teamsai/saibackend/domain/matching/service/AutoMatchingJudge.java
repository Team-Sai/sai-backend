package org.teamsai.saibackend.domain.matching.service;

import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingResult;
import org.teamsai.saibackend.domain.matching.service.EvaluatedMatchingCandidate;
import org.teamsai.saibackend.domain.matching.service.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.service.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class AutoMatchingJudge {

    private static final BigDecimal MINIMUM_MATCH_RATIO =
            new BigDecimal("0.10");
    private static final BigDecimal MAXIMUM_MATCH_RATIO =
            new BigDecimal("1.10");

    public AutoMatchingResult judge(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        validateInput(transaction, candidates);

        if (transaction.transactionType()
                != AutoMatchingTransactionType.DEPOSIT) {
            return new AutoMatchingResult(List.of());
        }

        List<EvaluatedMatchingCandidate> evaluatedCandidates =
                candidates.stream()
                        .filter(candidate ->
                                isParticipantNameMatched(
                                        transaction,
                                        candidate
                                ))
                        .map(candidate ->
                                evaluateCandidate(transaction, candidate))
                        .flatMap(Optional::stream)
                        .toList();

        return new AutoMatchingResult(evaluatedCandidates);
    }

    private Optional<EvaluatedMatchingCandidate> evaluateCandidate(
            MatchingTransaction transaction,
            MatchingCandidate candidate
    ) {
        BigDecimal transactionAmount = transaction.amount();
        BigDecimal expectedAmount = candidate.remainingAmount();
        BigDecimal minimumAmount = expectedAmount
                .multiply(MINIMUM_MATCH_RATIO);
        BigDecimal maximumAmount = expectedAmount
                .multiply(MAXIMUM_MATCH_RATIO);

        if (transactionAmount.compareTo(minimumAmount) < 0
                || transactionAmount.compareTo(maximumAmount) > 0) {
            return Optional.empty();
        }

        MatchingAmountType amountMatchType =
                determineAmountMatchType(
                        transactionAmount,
                        expectedAmount
                );

        return Optional.of(
                new EvaluatedMatchingCandidate(
                        candidate,
                        amountMatchType
                )
        );
    }

    private MatchingAmountType determineAmountMatchType(
            BigDecimal transactionAmount,
            BigDecimal expectedAmount
    ) {
        int comparison = transactionAmount.compareTo(expectedAmount);

        if (comparison < 0) {
            return MatchingAmountType.PARTIAL;
        }

        if (comparison > 0) {
            return MatchingAmountType.EXCESS;
        }

        return MatchingAmountType.EXACT;
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
