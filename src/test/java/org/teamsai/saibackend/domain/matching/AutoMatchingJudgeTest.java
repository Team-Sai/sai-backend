package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoMatchingJudgeTest {

    private final AutoMatchingJudge judge = new AutoMatchingJudge();

    @Test
    void withdrawalTransactionIsUnmatched() {
        MatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.WITHDRAWAL,
                "HongGilDong",
                "10000"
        );

        MatchingCandidate candidate = candidate(
                1L,
                "HongGilDong",
                "10000"
        );

        AutoMatchingResult result = judge.judge(
                transaction,
                List.of(candidate)
        );

        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.UNMATCHED);
        assertThat(result.matchedCandidates()).isEmpty();
    }

    @Test
    void depositWithOneMatchedCandidateIsMatchable() {
        MatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.DEPOSIT,
                "Hong GilDong",
                "10000.0"
        );

        MatchingCandidate candidate = candidate(
                1L,
                "HongGilDong",
                "10000.00"
        );

        AutoMatchingResult result = judge.judge(
                transaction,
                List.of(candidate)
        );

        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.MATCHABLE);
        assertThat(result.matchedCandidates()).containsExactly(candidate);
    }

    @Test
    void depositWithMultipleMatchedCandidatesNeedsCheck() {
        MatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.DEPOSIT,
                "HongGilDong",
                "10000"
        );

        MatchingCandidate first = candidate(1L, "Hong GilDong", "10000");
        MatchingCandidate second = candidate(2L, "HongGil Dong", "10000");

        AutoMatchingResult result = judge.judge(
                transaction,
                List.of(first, second)
        );

        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.NEEDS_CHECK);
        assertThat(result.matchedCandidates()).containsExactly(first, second);
    }

    @Test
    void depositWithoutMatchedCandidateIsUnmatched() {
        MatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.DEPOSIT,
                "HongGilDong",
                "10000"
        );

        MatchingCandidate candidate = candidate(
                1L,
                "KimChulSoo",
                "10000"
        );

        AutoMatchingResult result = judge.judge(
                transaction,
                List.of(candidate)
        );

        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.UNMATCHED);
        assertThat(result.matchedCandidates()).isEmpty();
    }

    @Test
    void depositWithDifferentAmountIsUnmatched() {
        MatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.DEPOSIT,
                "HongGilDong",
                "10000"
        );

        MatchingCandidate candidate = candidate(
                1L,
                "HongGilDong",
                "9000"
        );

        AutoMatchingResult result = judge.judge(
                transaction,
                List.of(candidate)
        );

        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.UNMATCHED);
        assertThat(result.matchedCandidates()).isEmpty();
    }

    private MatchingTransaction transaction(
            AutoMatchingTransactionType type,
            String counterpartyName,
            String amount
    ) {
        return new MatchingTransaction(
                1L,
                type,
                new BigDecimal(amount),
                counterpartyName
        );
    }

    private MatchingCandidate candidate(
            Long obligationId,
            String participantName,
            String remainingAmount
    ) {
        return new MatchingCandidate(
                obligationId,
                obligationId,
                participantName,
                new BigDecimal(remainingAmount)
        );
    }
}
