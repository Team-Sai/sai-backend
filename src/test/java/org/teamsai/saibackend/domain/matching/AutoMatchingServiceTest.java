package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.policy.AutoMatchingJudge;
import org.teamsai.saibackend.domain.matching.reader.MatchingCandidateReader;
import org.teamsai.saibackend.domain.matching.reader.MatchingTransactionReader;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingService;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.service.PaymentService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoMatchingService 단위 테스트")
class AutoMatchingServiceTest {

    @Mock
    private MatchingTransactionReader matchingTransactionReader;

    @Mock
    private MatchingCandidateReader matchingCandidateReader;

    @Mock
    private PaymentService paymentService;

    private final AutoMatchingJudge autoMatchingJudge = new AutoMatchingJudge();

    private AutoMatchingService autoMatchingService;

    @BeforeEach
    void setUp() {
        autoMatchingService = new AutoMatchingService(
                matchingTransactionReader,
                matchingCandidateReader,
                autoMatchingJudge,
                paymentService
        );
    }

    @Nested
    @DisplayName("자동매칭 실행")
    class Execute {

        @Test
        @DisplayName("매칭 가능한 정산 후보이면 자동 납부 반영을 호출한다")
        void executeAppliesPaymentWhenSettlementCandidateIsMatchable() {
            MatchingTransaction transaction = transaction(
                    101L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "Hong GilDong",
                    "10000"
            );

            MatchingCandidate candidate = candidate(
                    MatchingTargetType.SETTLEMENT,
                    1L,
                    "HongGilDong",
                    "10000.00"
            );

            given(matchingTransactionReader.readPendingTransactions())
                    .willReturn(List.of(transaction));
            given(matchingCandidateReader.readCandidates())
                    .willReturn(List.of(candidate));

            AutoMatchingExecutionResult result = autoMatchingService.execute();

            verify(paymentService).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isEqualTo(1);
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isZero();
        }

        @Test
        @DisplayName("일치하는 후보가 없으면 자동 납부 반영을 호출하지 않는다")
        void executeDoesNotApplyPaymentWhenTransactionIsUnmatched() {
            MatchingTransaction transaction = transaction(
                    101L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );

            MatchingCandidate candidate = candidate(
                    MatchingTargetType.SETTLEMENT,
                    1L,
                    "KimChulSoo",
                    "10000"
            );

            given(matchingTransactionReader.readPendingTransactions())
                    .willReturn(List.of(transaction));
            given(matchingCandidateReader.readCandidates())
                    .willReturn(List.of(candidate));

            AutoMatchingExecutionResult result = autoMatchingService.execute();

            verify(paymentService, never()).applyAutoMatchedPayment(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isZero();
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("일치하는 후보가 여러 개이면 확인 필요로 처리하고 자동 납부 반영을 호출하지 않는다")
        void executeDoesNotApplyPaymentWhenMultipleCandidatesAreMatched() {
            MatchingTransaction transaction = transaction(
                    101L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );

            MatchingCandidate firstCandidate = candidate(
                    MatchingTargetType.SETTLEMENT,
                    1L,
                    "Hong GilDong",
                    "10000"
            );
            MatchingCandidate secondCandidate = candidate(
                    MatchingTargetType.SETTLEMENT,
                    2L,
                    "HongGil Dong",
                    "10000"
            );

            given(matchingTransactionReader.readPendingTransactions())
                    .willReturn(List.of(transaction));
            given(matchingCandidateReader.readCandidates())
                    .willReturn(List.of(firstCandidate, secondCandidate));

            AutoMatchingExecutionResult result = autoMatchingService.execute();

            verify(paymentService, never()).applyAutoMatchedPayment(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isZero();
            assertThat(result.needsCheckCount()).isEqualTo(1);
            assertThat(result.unmatchedCount()).isZero();
        }

        @Test
        @DisplayName("매칭 가능한 후보가 대여금이면 자동 납부 반영을 호출하지 않고 확인 필요로 처리한다")
        void executeDoesNotApplyPaymentWhenLoanCandidateIsMatchable() {
            MatchingTransaction transaction = transaction(
                    101L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );

            MatchingCandidate candidate = candidate(
                    MatchingTargetType.LOAN,
                    1L,
                    "HongGilDong",
                    "10000"
            );

            given(matchingTransactionReader.readPendingTransactions())
                    .willReturn(List.of(transaction));
            given(matchingCandidateReader.readCandidates())
                    .willReturn(List.of(candidate));

            AutoMatchingExecutionResult result = autoMatchingService.execute();

            verify(paymentService, never()).applyAutoMatchedPayment(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isZero();
            assertThat(result.needsCheckCount()).isEqualTo(1);
            assertThat(result.unmatchedCount()).isZero();
        }

        @Test
        @DisplayName("이미 자동 반영된 정산 후보는 같은 실행에서 다시 사용하지 않는다")
        void executeDoesNotReuseAlreadyAppliedSettlementCandidate() {
            MatchingTransaction firstTransaction = transaction(
                    101L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );
            MatchingTransaction secondTransaction = transaction(
                    102L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );

            MatchingCandidate candidate = candidate(
                    MatchingTargetType.SETTLEMENT,
                    1L,
                    "HongGilDong",
                    "10000"
            );

            given(matchingTransactionReader.readPendingTransactions())
                    .willReturn(List.of(firstTransaction, secondTransaction));
            given(matchingCandidateReader.readCandidates())
                    .willReturn(List.of(candidate));

            AutoMatchingExecutionResult result = autoMatchingService.execute();

            verify(paymentService, times(1)).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
            );

            assertThat(result.totalTransactionCount()).isEqualTo(2);
            assertThat(result.appliedCount()).isEqualTo(1);
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isEqualTo(1);
        }
    }

    private MatchingTransaction transaction(
            Long transactionId,
            AutoMatchingTransactionType type,
            String counterpartyName,
            String amount
    ) {
        return new MatchingTransaction(
                transactionId,
                type,
                new BigDecimal(amount),
                counterpartyName
        );
    }

    private MatchingCandidate candidate(
            MatchingTargetType targetType,
            Long obligationId,
            String participantName,
            String remainingAmount
    ) {
        return new MatchingCandidate(
                targetType,
                obligationId,
                obligationId,
                participantName,
                new BigDecimal(remainingAmount)
        );
    }
}
