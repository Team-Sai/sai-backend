package org.teamsai.saibackend.domain.matching;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AutoMatchingJudge 단위 테스트")
class AutoMatchingJudgeTest {

    private final AutoMatchingJudge judge = new AutoMatchingJudge();

    @Nested
    @DisplayName("자동 매칭 판정")
    class Judge {

        @Test
        @DisplayName("출금 거래이면 미매칭으로 판정한다")
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

            assertThat(result.decisionType())
                    .isEqualTo(AutoMatchingDecisionType.UNMATCHED);
            assertThat(result.matchedCandidates()).isEmpty();
        }

        @Test
        @DisplayName("입금 거래에 일치하는 후보가 하나이면 매칭 가능으로 판정한다")
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

            assertThat(result.decisionType())
                    .isEqualTo(AutoMatchingDecisionType.MATCHABLE);
            assertThat(result.matchedCandidates()).containsExactly(candidate);
        }

        @Test
        @DisplayName("입금 거래에 일치하는 후보가 여러 개이면 확인 필요로 판정한다")
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

            assertThat(result.decisionType())
                    .isEqualTo(AutoMatchingDecisionType.NEEDS_CHECK);
            assertThat(result.matchedCandidates())
                    .containsExactly(first, second);
        }

        @Test
        @DisplayName("일치하는 후보가 없으면 미매칭으로 판정한다")
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

            assertThat(result.decisionType())
                    .isEqualTo(AutoMatchingDecisionType.UNMATCHED);
            assertThat(result.matchedCandidates()).isEmpty();
        }

        @Test
        @DisplayName("금액이 다르면 미매칭으로 판정한다")
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

            assertThat(result.decisionType())
                    .isEqualTo(AutoMatchingDecisionType.UNMATCHED);
            assertThat(result.matchedCandidates()).isEmpty();
        }
    }

    @Nested
    @DisplayName("입력값 검증")
    class ValidateInput {

        @Test
        @DisplayName("거래 정보가 null이면 잘못된 매칭 요청 예외가 발생한다")
        void judgeFailsWhenTransactionIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> judge.judge(
                            null,
                            List.of()
                    )
            );
        }

        @Test
        @DisplayName("후보 목록이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void judgeFailsWhenCandidatesIsNull() {
            MatchingTransaction transaction = transaction(
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );

            assertInvalidMatchingRequestThrownBy(
                    () -> judge.judge(
                            transaction,
                            null
                    )
            );
        }

        @Test
        @DisplayName("후보 목록에 null 요소가 있으면 잘못된 매칭 요청 예외가 발생한다")
        void judgeFailsWhenCandidatesContainsNull() {
            MatchingTransaction transaction = transaction(
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );

            assertInvalidMatchingRequestThrownBy(
                    () -> judge.judge(
                            transaction,
                            Arrays.asList((MatchingCandidate) null)
                    )
            );
        }
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

    private void assertInvalidMatchingRequestThrownBy(
            ThrowingCallable callable
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        MatchingErrorCode.INVALID_MATCHING_REQUEST
                                )
                );
    }

}
