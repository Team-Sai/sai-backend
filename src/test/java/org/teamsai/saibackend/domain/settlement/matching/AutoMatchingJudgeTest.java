package org.teamsai.saibackend.domain.settlement.matching;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoMatchingJudgeTest {

    private final AutoMatchingJudge judge = new AutoMatchingJudge();

    @Test
    void withdrawalTransactionIsUnmatched() {
        AutoMatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.WITHDRAWAL,
                "홍길동",
                "10000"
        );

        AutoMatchingObligationCandidate candidate = candidate(
                1L,
                "홍길동",
                "10000"
        );

        AutoMatchingResult result = judge.judge(transaction,
                List.of(candidate));


        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.UNMATCHED);
        assertThat(result.matchedCandidates()).isEmpty();
    }

    @Test
    void depositWithOneMatchedCandidateIsMatchable() {
        AutoMatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.DEPOSIT,
                "홍 길동",
                "10000.0"
        );

        AutoMatchingObligationCandidate candidate = candidate(
                1L,
                "홍길동",
                "10000.00"
        );

        AutoMatchingResult result = judge.judge(transaction,
                List.of(candidate));


        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.MATCHABLE);

        assertThat(result.matchedCandidates()).containsExactly(candidate);
    }

    @Test
    void depositWithMultipleMatchedCandidatesNeedsCheck() {
        AutoMatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.DEPOSIT,
                "홍길동",
                "10000"
        );

        AutoMatchingObligationCandidate first = candidate(1L, "홍길 동", "10000");
                AutoMatchingObligationCandidate second = candidate(2L, "홍 길 동", "10000");

                        AutoMatchingResult result = judge.judge(transaction,
                                List.of(first, second));


        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.NEEDS_CHECK);
        assertThat(result.matchedCandidates()).containsExactly(first, second);
    }

    @Test
    void depositWithoutMatchedCandidateIsUnmatched() {
        AutoMatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.DEPOSIT,
                "홍길동",
                "10000"
        );

        AutoMatchingObligationCandidate candidate = candidate(
                1L,
                "김철수",
                "10000"
        );

        AutoMatchingResult result = judge.judge(transaction,
                List.of(candidate));


        assertThat(result.decisionType()).isEqualTo(AutoMatchingDecisionType.UNMATCHED);
        assertThat(result.matchedCandidates()).isEmpty();
    }

    @Test
    void depositWithDifferentAmountIsUnmatched() {
        AutoMatchingTransaction transaction = transaction(
                AutoMatchingTransactionType.DEPOSIT,
                "홍길동",
                "10000"
        );

        AutoMatchingObligationCandidate candidate = candidate(
                1L,
                "홍길동",
                "9000"
        );

        AutoMatchingResult result = judge.judge(
                transaction,
                List.of(candidate)
        );

        assertThat(result.decisionType())
                .isEqualTo(AutoMatchingDecisionType.UNMATCHED);

        assertThat(result.matchedCandidates()).isEmpty();
    }

    private AutoMatchingTransaction transaction(
            AutoMatchingTransactionType type,
            String counterpartyName,
            String amount
    ) {
        return new AutoMatchingTransaction(
                1L,
                type,
                new BigDecimal(amount),
                counterpartyName
        );
    }

    private AutoMatchingObligationCandidate candidate(
            Long obligationId,
            String participantName,
            String remainingAmount
    ) {
        return new AutoMatchingObligationCandidate(
                obligationId,
                obligationId,
                participantName,
                new BigDecimal(remainingAmount)
        );
    }
}
