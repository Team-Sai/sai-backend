package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contract.exception.RepaymentScheduleErrorCode;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateDTO;
import org.teamsai.saibackend.domain.matching.dto.response.MatchingReviewProcessResponse;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.policy.MatchingReviewValidator;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchCandidateService;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchingReviewService;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;
import org.teamsai.saibackend.domain.matching.type.MatchingCandidateInvalidationReason;
import org.teamsai.saibackend.domain.matching.type.MatchingCandidateStatus;
import org.teamsai.saibackend.domain.matching.type.MatchingReviewResult;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.service.LoanPaymentService;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionDetailResponse;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionQueryService;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankTransactionMatchingReviewService 단위 테스트")
class BankTransactionMatchingReviewServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long LINKED_ACCOUNT_ID = 2L;
    private static final Long BANK_TRANSACTION_ID = 3L;
    private static final Long MATCH_CANDIDATE_ID = 4L;

    @Mock
    private BankTransactionQueryService bankTransactionQueryService;

    @Mock
    private BankTransactionMatchCandidateService candidateService;

    @Mock
    private SettlementPaymentService settlementPaymentService;

    @Mock
    private LoanPaymentService loanPaymentService;

    @Mock
    private BankTransactionService bankTransactionService;

    private BankTransactionMatchingReviewService matchingReviewService;

    @BeforeEach
    void setUp() {
        matchingReviewService = new BankTransactionMatchingReviewService(
                bankTransactionQueryService,
                candidateService,
                settlementPaymentService,
                loanPaymentService,
                bankTransactionService,
                new MatchingReviewValidator()
        );
    }

    @Test
    @DisplayName("선택한 정산 후보를 수동 납부로 반영하고 거래를 완료한다")
    void appliesSelectedSettlementCandidate() {
        givenReviewableTransaction();
        given(candidateService.findByIdAndBankTransactionId(
                MATCH_CANDIDATE_ID,
                BANK_TRANSACTION_ID
        )).willReturn(candidate(MatchingTargetType.SETTLEMENT, 10L));

        MatchingReviewProcessResponse response =
                matchingReviewService.applyCandidate(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID,
                        MATCH_CANDIDATE_ID
                );

        verify(settlementPaymentService).applyManuallyMatchedPayment(
                10L,
                BANK_TRANSACTION_ID,
                new BigDecimal("5000")
        );
        verify(loanPaymentService, never()).applyManuallyMatchedPayment(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verifyStatusUpdatedTo(BankTransactionProcessingStatus.APPLIED);
        assertThat(response.processingStatus())
                .isEqualTo(BankTransactionProcessingStatus.APPLIED);
        assertThat(response.reviewResult())
                .isEqualTo(MatchingReviewResult.APPLIED);
    }

    @Test
    @DisplayName("선택한 차용증 후보를 수동 상환으로 반영한다")
    void appliesSelectedLoanCandidate() {
        givenReviewableTransaction();
        given(candidateService.findByIdAndBankTransactionId(
                MATCH_CANDIDATE_ID,
                BANK_TRANSACTION_ID
        )).willReturn(candidate(MatchingTargetType.LOAN, 20L));

        matchingReviewService.applyCandidate(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID,
                MATCH_CANDIDATE_ID
        );

        verify(loanPaymentService).applyManuallyMatchedPayment(
                20L,
                BANK_TRANSACTION_ID,
                new BigDecimal("5000")
        );
        verifyStatusUpdatedTo(BankTransactionProcessingStatus.APPLIED);
    }

    @Test
    @DisplayName("어느 후보도 선택하지 않으면 미매칭으로 처리한다")
    void rejectsAllCandidatesAsUnmatched() {
        givenReviewableTransaction();

        MatchingReviewProcessResponse response =
                matchingReviewService.rejectCandidates(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID
                );

        verifyStatusUpdatedTo(BankTransactionProcessingStatus.UNMATCHED);
        assertThat(response.processingStatus())
                .isEqualTo(BankTransactionProcessingStatus.UNMATCHED);
        assertThat(response.reviewResult())
                .isEqualTo(MatchingReviewResult.REJECTED);
    }

    @Test
    @DisplayName("일시적인 납부 실패는 확인 필요 상태를 유지하도록 예외를 전파한다")
    void keepsReviewOpenForRetryablePaymentFailure() {
        givenReviewableTransaction();
        given(candidateService.findByIdAndBankTransactionId(
                MATCH_CANDIDATE_ID,
                BANK_TRANSACTION_ID
        )).willReturn(candidate(MatchingTargetType.SETTLEMENT, 10L));
        DomainException exception = PaymentErrorCode
                .PAYMENT_RECORD_CREATE_FAILED
                .toException();
        willThrow(exception)
                .given(settlementPaymentService)
                .applyManuallyMatchedPayment(
                        10L,
                        BANK_TRANSACTION_ID,
                        new BigDecimal("5000")
                );

        assertThatThrownBy(() -> matchingReviewService.applyCandidate(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID,
                MATCH_CANDIDATE_ID
        )).isSameAs(exception);

        verify(bankTransactionService, never()).updateStatus(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(candidateService, never()).invalidateCandidate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("선택한 후보가 무효여도 다른 후보가 남으면 확인 필요 상태를 유지한다")
    void keepsReviewOpenWhenAnotherCandidateRemains() {
        givenReviewableTransaction();
        given(candidateService.findByIdAndBankTransactionId(
                MATCH_CANDIDATE_ID,
                BANK_TRANSACTION_ID
        )).willReturn(candidate(MatchingTargetType.LOAN, 20L));
        willThrow(RepaymentScheduleErrorCode.SCHEDULE_NOT_PENDING.toException())
                .given(loanPaymentService)
                .applyManuallyMatchedPayment(
                        20L,
                        BANK_TRANSACTION_ID,
                        new BigDecimal("5000")
                );
        given(candidateService.countAvailableCandidates(BANK_TRANSACTION_ID))
                .willReturn(1);

        MatchingReviewProcessResponse response =
                matchingReviewService.applyCandidate(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID,
                        MATCH_CANDIDATE_ID
                );

        verifyCandidateInvalidatedAs(
                MatchingCandidateInvalidationReason.TARGET_NOT_AVAILABLE
        );
        verify(bankTransactionService, never()).updateStatus(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(response.processingStatus())
                .isEqualTo(BankTransactionProcessingStatus.NEEDS_CHECK);
        assertThat(response.reviewResult())
                .isEqualTo(MatchingReviewResult.CANDIDATE_INVALIDATED);
    }

    @Test
    @DisplayName("마지막 후보가 더 이상 처리 가능하지 않으면 미매칭으로 처리한다")
    void marksTransactionAsUnmatchedWhenLastCandidateIsUnavailable() {
        givenReviewableTransaction();
        given(candidateService.findByIdAndBankTransactionId(
                MATCH_CANDIDATE_ID,
                BANK_TRANSACTION_ID
        )).willReturn(candidate(MatchingTargetType.SETTLEMENT, 10L));
        willThrow(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_ACTIVE.toException())
                .given(settlementPaymentService)
                .applyManuallyMatchedPayment(
                        10L,
                        BANK_TRANSACTION_ID,
                        new BigDecimal("5000")
                );
        given(candidateService.countAvailableCandidates(BANK_TRANSACTION_ID))
                .willReturn(0);

        MatchingReviewProcessResponse response =
                matchingReviewService.applyCandidate(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID,
                        MATCH_CANDIDATE_ID
                );

        verifyCandidateInvalidatedAs(
                MatchingCandidateInvalidationReason.TARGET_NOT_AVAILABLE
        );
        verifyStatusUpdatedTo(BankTransactionProcessingStatus.UNMATCHED);
        assertThat(response.processingStatus())
                .isEqualTo(BankTransactionProcessingStatus.UNMATCHED);
        assertThat(response.reviewResult())
                .isEqualTo(MatchingReviewResult.CANDIDATE_INVALIDATED);
    }

    @Test
    @DisplayName("마지막 후보의 실제 대상이 없으면 실패로 처리한다")
    void marksTransactionAsFailedWhenLastCandidateTargetIsMissing() {
        givenReviewableTransaction();
        given(candidateService.findByIdAndBankTransactionId(
                MATCH_CANDIDATE_ID,
                BANK_TRANSACTION_ID
        )).willReturn(candidate(MatchingTargetType.LOAN, 20L));
        willThrow(RepaymentScheduleErrorCode.SCHEDULE_NOT_FOUND.toException())
                .given(loanPaymentService)
                .applyManuallyMatchedPayment(
                        20L,
                        BANK_TRANSACTION_ID,
                        new BigDecimal("5000")
                );
        given(candidateService.countAvailableCandidates(BANK_TRANSACTION_ID))
                .willReturn(0);

        MatchingReviewProcessResponse response =
                matchingReviewService.applyCandidate(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID,
                        MATCH_CANDIDATE_ID
                );

        verifyCandidateInvalidatedAs(
                MatchingCandidateInvalidationReason.TARGET_NOT_FOUND
        );
        verifyStatusUpdatedTo(BankTransactionProcessingStatus.FAILED);
        assertThat(response.processingStatus())
                .isEqualTo(BankTransactionProcessingStatus.FAILED);
        assertThat(response.reviewResult())
                .isEqualTo(MatchingReviewResult.CANDIDATE_INVALIDATED);
    }

    @Test
    @DisplayName("중복 납부기록이면 이미 반영된 거래로 처리한다")
    void marksDuplicatedPaymentAsApplied() {
        givenReviewableTransaction();
        given(candidateService.findByIdAndBankTransactionId(
                MATCH_CANDIDATE_ID,
                BANK_TRANSACTION_ID
        )).willReturn(candidate(MatchingTargetType.SETTLEMENT, 10L));
        willThrow(PaymentErrorCode.DUPLICATE_PAYMENT_RECORD.toException())
                .given(settlementPaymentService)
                .applyManuallyMatchedPayment(
                        10L,
                        BANK_TRANSACTION_ID,
                        new BigDecimal("5000")
                );

        MatchingReviewProcessResponse response =
                matchingReviewService.applyCandidate(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID,
                        MATCH_CANDIDATE_ID
                );

        verifyStatusUpdatedTo(BankTransactionProcessingStatus.APPLIED);
        assertThat(response.processingStatus())
                .isEqualTo(BankTransactionProcessingStatus.APPLIED);
        assertThat(response.reviewResult())
                .isEqualTo(MatchingReviewResult.APPLIED);
    }

    @Test
    @DisplayName("확인 필요 상태가 아닌 거래는 선택 처리하지 않는다")
    void rejectsTransactionThatDoesNotNeedReview() {
        given(bankTransactionQueryService.getTransactionDetailForUpdate(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).willReturn(transaction(BankTransactionProcessingStatus.APPLIED));

        assertThatThrownBy(() -> matchingReviewService.applyCandidate(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID,
                MATCH_CANDIDATE_ID
        )).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                MatchingErrorCode.MATCHING_REVIEW_NOT_REQUIRED
                        )
        );

        verify(candidateService, never())
                .findByIdAndBankTransactionId(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    private void givenReviewableTransaction() {
        given(bankTransactionQueryService.getTransactionDetailForUpdate(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).willReturn(transaction(
                BankTransactionProcessingStatus.NEEDS_CHECK
        ));
    }

    private BankTransactionDetailResponse transaction(
            BankTransactionProcessingStatus processingStatus
    ) {
        return new BankTransactionDetailResponse(
                BANK_TRANSACTION_ID,
                LINKED_ACCOUNT_ID,
                new BigDecimal("5000"),
                BankTransactionType.DEPOSIT,
                processingStatus,
                LocalDateTime.of(2026, 8, 15, 10, 0),
                "홍길동",
                null,
                LocalDateTime.of(2026, 8, 15, 10, 5)
        );
    }

    private BankTransactionMatchCandidateDTO candidate(
            MatchingTargetType targetType,
            Long targetId
    ) {
        return BankTransactionMatchCandidateDTO.builder()
                .matchCandidateId(MATCH_CANDIDATE_ID)
                .bankTransactionId(BANK_TRANSACTION_ID)
                .targetType(targetType)
                .targetId(targetId)
                .expectedRemainingAmount(new BigDecimal("10000"))
                .amountMatchType(MatchingAmountType.PARTIAL)
                .candidateStatus(MatchingCandidateStatus.AVAILABLE)
                .createdAt(LocalDateTime.of(2026, 8, 15, 10, 5))
                .build();
    }

    private void verifyCandidateInvalidatedAs(
            MatchingCandidateInvalidationReason invalidationReason
    ) {
        verify(candidateService).invalidateCandidate(
                MATCH_CANDIDATE_ID,
                BANK_TRANSACTION_ID,
                invalidationReason
        );
    }

    private void verifyStatusUpdatedTo(
            BankTransactionProcessingStatus nextStatus
    ) {
        verify(bankTransactionService).updateStatus(
                BANK_TRANSACTION_ID,
                BankTransactionProcessingStatus.NEEDS_CHECK,
                nextStatus
        );
    }
}
