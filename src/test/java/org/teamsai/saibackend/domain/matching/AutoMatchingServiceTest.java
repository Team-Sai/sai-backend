package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.policy.AutoMatchingJudge;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingService;
import org.teamsai.saibackend.domain.matching.service.LoanPaymentService;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoMatchingService 단위 테스트")
class AutoMatchingServiceTest {

    @Mock
    private SettlementPaymentService paymentService;

    @Mock
    private LoanPaymentService loanPaymentService;

    private final AutoMatchingJudge autoMatchingJudge = new AutoMatchingJudge();

    private AutoMatchingService autoMatchingService;

    @BeforeEach
    void setUp() {
        autoMatchingService = new AutoMatchingService(
                autoMatchingJudge,
                paymentService,
                loanPaymentService
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

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(transaction),
                    List.of(candidate)
            );

            verify(paymentService).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isEqualTo(1);
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isZero();
            assertThat(result.transactionResults())
                    .extracting(
                            transactionResult -> transactionResult
                                    .transactionId(),
                            transactionResult -> transactionResult
                                    .processStatus()
                    )
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    101L,
                                    AutoMatchingProcessStatus.APPLIED
                            )
                    );
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

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(transaction),
                    List.of(candidate)
            );

            verify(paymentService, never()).applyAutoMatchedPayment(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isZero();
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isEqualTo(1);
            assertThat(result.transactionResults())
                    .extracting(
                            transactionResult -> transactionResult
                                    .transactionId(),
                            transactionResult -> transactionResult
                                    .processStatus()
                    )
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    101L,
                                    AutoMatchingProcessStatus.UNMATCHED
                            )
                    );
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

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(transaction),
                    List.of(firstCandidate, secondCandidate)
            );

            verify(paymentService, never()).applyAutoMatchedPayment(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isZero();
            assertThat(result.needsCheckCount()).isEqualTo(1);
            assertThat(result.unmatchedCount()).isZero();
            assertThat(result.transactionResults())
                    .extracting(
                            transactionResult -> transactionResult
                                    .transactionId(),
                            transactionResult -> transactionResult
                                    .processStatus()
                    )
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    101L,
                                    AutoMatchingProcessStatus.NEEDS_CHECK
                            )
                    );
        }

        @Test
        @DisplayName("매칭 가능한 후보가 대여금이면 대여금 납부 서비스를 통해 자동 반영을 호출한다")
        void executeAppliesLoanPaymentWhenLoanCandidateIsMatchable() {
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

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(transaction),
                    List.of(candidate)
            );

            verify(loanPaymentService).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
            );
            verify(paymentService, never()).applyAutoMatchedPayment(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isEqualTo(1);
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isZero();
        }

        @Test
        @DisplayName("대여금 납부 반영 중 비즈니스 예외가 발생하면 확인 필요로 분류한다")
        void executeClassifiesLoanPaymentBusinessExceptionAsNeedsCheck() {
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

            doThrow(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_ACTIVE.toException())
                    .when(loanPaymentService)
                    .applyAutoMatchedPayment(
                            1L,
                            101L,
                            new BigDecimal("10000")
                    );

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(transaction),
                    List.of(candidate)
            );

            verify(loanPaymentService).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
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

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(firstTransaction, secondTransaction),
                    List.of(candidate)
            );

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

        @Test
        @DisplayName("같은 ID라도 대상 유형이 다르면 이미 반영된 후보로 제외하지 않는다")
        void executeDoesNotExcludeDifferentTargetTypeCandidateWithSameId() {
            MatchingTransaction firstTransaction = transaction(
                    101L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );
            MatchingTransaction secondTransaction = transaction(
                    102L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "KimChulSoo",
                    "20000"
            );

            MatchingCandidate settlementCandidate = candidate(
                    MatchingTargetType.SETTLEMENT,
                    1L,
                    "HongGilDong",
                    "10000"
            );
            MatchingCandidate loanCandidate = candidate(
                    MatchingTargetType.LOAN,
                    1L,
                    "KimChulSoo",
                    "20000"
            );

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(firstTransaction, secondTransaction),
                    List.of(settlementCandidate, loanCandidate)
            );

            verify(paymentService, times(1)).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
            );
            verify(loanPaymentService, times(1)).applyAutoMatchedPayment(
                    1L,
                    102L,
                    new BigDecimal("20000")
            );

            assertThat(result.totalTransactionCount()).isEqualTo(2);
            assertThat(result.appliedCount()).isEqualTo(2);
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isZero();
        }

        @Test
        @DisplayName("납부 반영 중 시스템 오류가 발생해도 해당 거래만 실패로 처리하고 다음 거래를 계속 처리한다")
        void executeContinuesWhenApplyPaymentThrowsException() {
            MatchingTransaction firstTransaction = transaction(
                    101L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "HongGilDong",
                    "10000"
            );
            MatchingTransaction secondTransaction = transaction(
                    102L,
                    AutoMatchingTransactionType.DEPOSIT,
                    "KimChulSoo",
                    "20000"
            );

            MatchingCandidate firstCandidate = candidate(
                    MatchingTargetType.SETTLEMENT,
                    1L,
                    "HongGilDong",
                    "10000"
            );
            MatchingCandidate secondCandidate = candidate(
                    MatchingTargetType.SETTLEMENT,
                    2L,
                    "KimChulSoo",
                    "20000"
            );

            doThrow(PaymentErrorCode.PAYMENT_STATUS_UPDATE_FAILED.toException())
                    .when(paymentService)
                    .applyAutoMatchedPayment(
                            1L,
                            101L,
                            new BigDecimal("10000")
                    );

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(firstTransaction, secondTransaction),
                    List.of(firstCandidate, secondCandidate)
            );

            verify(paymentService).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
            );
            verify(paymentService).applyAutoMatchedPayment(
                    2L,
                    102L,
                    new BigDecimal("20000")
            );

            assertThat(result.totalTransactionCount()).isEqualTo(2);
            assertThat(result.appliedCount()).isEqualTo(1);
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isZero();
            assertThat(result.duplicateCount()).isZero();
            assertThat(result.failedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("납부 반영 중 비즈니스 예외가 발생하면 확인 필요로 분류한다")
        void executeClassifiesBusinessDomainExceptionAsNeedsCheck() {
            MatchingTransaction transaction = transaction(
                    101L,
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

            doThrow(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_ACTIVE.toException())
                    .when(paymentService)
                    .applyAutoMatchedPayment(
                            1L,
                            101L,
                            new BigDecimal("10000")
                    );

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(transaction),
                    List.of(candidate)
            );

            verify(paymentService).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isZero();
            assertThat(result.needsCheckCount()).isEqualTo(1);
            assertThat(result.unmatchedCount()).isZero();
            assertThat(result.duplicateCount()).isZero();
            assertThat(result.failedCount()).isZero();
        }

        @Test
        @DisplayName("이미 반영된 은행 거래이면 중복으로 분류하고 확인 필요로 집계하지 않는다")
        void executeClassifiesDuplicatePaymentRecordSeparately() {
            MatchingTransaction transaction = transaction(
                    101L,
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

            doThrow(PaymentErrorCode.DUPLICATE_PAYMENT_RECORD.toException())
                    .when(paymentService)
                    .applyAutoMatchedPayment(
                            1L,
                            101L,
                            new BigDecimal("10000")
                    );

            AutoMatchingExecutionResult result = autoMatchingService.execute(
                    List.of(transaction),
                    List.of(candidate)
            );

            verify(paymentService).applyAutoMatchedPayment(
                    1L,
                    101L,
                    new BigDecimal("10000")
            );

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.appliedCount()).isZero();
            assertThat(result.needsCheckCount()).isZero();
            assertThat(result.unmatchedCount()).isZero();
            assertThat(result.duplicateCount()).isEqualTo(1);
            assertThat(result.failedCount()).isZero();
            assertThat(result.transactionResults())
                    .extracting(
                            transactionResult -> transactionResult
                                    .transactionId(),
                            transactionResult -> transactionResult
                                    .processStatus()
                    )
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    101L,
                                    AutoMatchingProcessStatus.DUPLICATE
                            )
                    );
        }

        @Test
        @DisplayName("거래 목록이 null이면 예외가 발생한다")
        void executeFailsWhenTransactionsIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> autoMatchingService.execute(
                            null,
                            List.of()
                    )
            );
        }

        @Test
        @DisplayName("후보 목록이 null이면 예외가 발생한다")
        void executeFailsWhenCandidatesIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> autoMatchingService.execute(
                            List.of(),
                            null
                    )
            );
        }

        @Test
        @DisplayName("거래 목록에 null 요소가 있으면 예외가 발생한다")
        void executeFailsWhenTransactionsContainsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> autoMatchingService.execute(
                            Arrays.asList((MatchingTransaction) null),
                            List.of()
                    )
            );
        }

        @Test
        @DisplayName("후보 목록에 null 요소가 있으면 예외가 발생한다")
        void executeFailsWhenCandidatesContainsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> autoMatchingService.execute(
                            List.of(),
                            Arrays.asList((MatchingCandidate) null)
                    )
            );
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
                counterpartyName,
                LocalDateTime.of(2026, 8, 5, 10, 0)
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

    private void assertInvalidMatchingRequestThrownBy(
            Runnable operation
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        MatchingErrorCode.INVALID_MATCHING_REQUEST
                                )
                );
    }
}
