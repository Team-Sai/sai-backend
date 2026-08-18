package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateDTO;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingService;
import org.teamsai.saibackend.domain.matching.service.BankMatchingTransactionService;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchCandidateService;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.NotificationType;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.exception.BankTransactionErrorCode;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankMatchingTransactionService 단위 테스트")
class BankMatchingTransactionServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long LINKED_ACCOUNT_ID = 1L;

    @Mock
    private PaymentObligationMapper paymentObligationMapper;

    @Mock
    private AutoMatchingService autoMatchingService;

    @Mock
    private BankTransactionService bankTransactionService;

    @Mock
    private BankTransactionMatchCandidateService candidateService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BankMatchingTransactionService transactionService;

    @Test
    @DisplayName("상대방명이 없으면 후보를 조회하지 않고 미매칭으로 변경한다")
    void classifiesBlankCounterpartyNameAsUnmatched() {
        BankTransactionDTO transaction = bankTransaction(101L, " ");
        givenLockedTransaction(transaction);

        AutoMatchingTransactionResult result =
                transactionService.process(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        transaction
                );

        assertThat(result.processStatus())
                .isEqualTo(AutoMatchingProcessStatus.UNMATCHED);
        verify(paymentObligationMapper, never())
                .findMatchCandidatesByLinkedAccountId(any(), any());
        verify(autoMatchingService, never()).execute(any(), any());
        verify(bankTransactionService).updateStatus(
                101L,
                BankTransactionProcessingStatus.PENDING,
                BankTransactionProcessingStatus.UNMATCHED
        );
    }

    @Test
    @DisplayName("특정 대상 동기화 범위 밖 거래는 PENDING으로 유지한다")
    void keepsOutOfScopeTransactionPending() {
        BankTransactionDTO transaction = bankTransaction(101L, "Hong Gil Dong");
        givenLockedTransaction(transaction);
        given(paymentObligationMapper.findMatchCandidatesByLinkedAccountIdAndTarget(
                LINKED_ACCOUNT_ID,
                transaction.getTransactionAt(),
                MatchingTargetType.SETTLEMENT,
                999L
        )).willReturn(List.of());

        AutoMatchingTransactionResult result = transactionService.process(
                USER_ID,
                LINKED_ACCOUNT_ID,
                transaction,
                MatchingTargetType.SETTLEMENT,
                999L
        );

        assertThat(result).isNull();
        verify(bankTransactionService, never()).updateStatus(any(), any(), any());
        verify(autoMatchingService, never()).execute(any(), any());
    }

    @Test
    @DisplayName("후보를 조회해 자동매칭하고 은행 거래 상태를 변경한다")
    void executesAutoMatchingAndUpdatesStatus() {
        BankTransactionDTO staleTransaction = bankTransaction(
                101L,
                "Old Name"
        );
        BankTransactionDTO lockedTransaction = bankTransaction(
                101L,
                "Hong Gil Dong"
        );
        givenLockedTransaction(lockedTransaction);
        MatchingCandidate candidate = candidate();
        AutoMatchingTransactionResult transactionResult = result(
                101L,
                AutoMatchingProcessStatus.APPLIED
        );

        given(paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                LINKED_ACCOUNT_ID,
                lockedTransaction.getTransactionAt()
        )).willReturn(List.of(candidate));
        given(autoMatchingService.execute(any(), any()))
                .willReturn(executionResult(transactionResult));

        AutoMatchingTransactionResult result =
                transactionService.process(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        staleTransaction
                );

        ArgumentCaptor<List<MatchingTransaction>> transactionsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(autoMatchingService).execute(
                transactionsCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(List.of(candidate))
        );

        MatchingTransaction matchingTransaction =
                transactionsCaptor.getValue().get(0);
        assertThat(matchingTransaction.transactionId()).isEqualTo(101L);
        assertThat(matchingTransaction.transactionType())
                .isEqualTo(AutoMatchingTransactionType.DEPOSIT);
        assertThat(matchingTransaction.amount())
                .isEqualByComparingTo("10000.00");
        assertThat(matchingTransaction.counterpartyName())
                .isEqualTo("Hong Gil Dong");
        assertThat(result).isEqualTo(transactionResult);
        verify(bankTransactionService).updateStatus(
                101L,
                BankTransactionProcessingStatus.PENDING,
                BankTransactionProcessingStatus.APPLIED
        );
    }

    @Test
    @DisplayName("중복 납부 결과는 이미 반영된 거래로 저장한다")
    void updatesDuplicatedResultAsApplied() {
        BankTransactionDTO transaction = bankTransaction(
                101L,
                "Hong Gil Dong"
        );
        givenLockedTransaction(transaction);
        given(paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                LINKED_ACCOUNT_ID,
                transaction.getTransactionAt()
        )).willReturn(List.of(candidate()));
        given(autoMatchingService.execute(any(), any()))
                .willReturn(executionResult(result(
                        101L,
                        AutoMatchingProcessStatus.DUPLICATE
                )));

        transactionService.process(USER_ID, LINKED_ACCOUNT_ID, transaction);

        verify(bankTransactionService).updateStatus(
                101L,
                BankTransactionProcessingStatus.PENDING,
                BankTransactionProcessingStatus.APPLIED
        );
    }

    @Test
    @DisplayName("자동매칭 결과가 거래 한 건이 아니면 예외가 발생한다")
    void throwsExceptionWhenMatchingResultCountIsInvalid() {
        BankTransactionDTO transaction = bankTransaction(
                101L,
                "Hong Gil Dong"
        );
        givenLockedTransaction(transaction);
        given(paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                LINKED_ACCOUNT_ID,
                transaction.getTransactionAt()
        )).willReturn(List.of(candidate()));
        given(autoMatchingService.execute(any(), any()))
                .willReturn(executionResult());

        assertThatThrownBy(
                () -> transactionService.process(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        transaction
                )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MatchingErrorCode.INVALID_MATCHING_REQUEST)
        );

        verify(bankTransactionService, never()).updateStatus(
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("상태 변경 실패를 그대로 전파한다")
    void propagatesStatusUpdateFailure() {
        BankTransactionDTO transaction = bankTransaction(
                101L,
                "Hong Gil Dong"
        );
        givenLockedTransaction(transaction);
        given(paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                LINKED_ACCOUNT_ID,
                transaction.getTransactionAt()
        )).willReturn(List.of(candidate()));
        given(autoMatchingService.execute(any(), any()))
                .willReturn(executionResult(result(
                        101L,
                        AutoMatchingProcessStatus.NEEDS_CHECK
                )));
        willThrow(BankTransactionErrorCode
                .BANK_TRANSACTION_STATUS_UPDATE_FAILED
                .toException())
                .given(bankTransactionService)
                .updateStatus(
                        101L,
                        BankTransactionProcessingStatus.PENDING,
                        BankTransactionProcessingStatus.NEEDS_CHECK
                );

        assertThatThrownBy(
                () -> transactionService.process(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        transaction
                )
        ).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("정산과 차용증 후보가 모두 있으면 매칭 검토 알림을 생성한다")
    void createsNotificationForCrossDomainCandidates() {
        BankTransactionDTO transaction = bankTransaction(
                101L,
                "Hong Gil Dong"
        );
        givenLockedTransaction(transaction);
        given(paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                LINKED_ACCOUNT_ID,
                transaction.getTransactionAt()
        )).willReturn(List.of(candidate()));
        given(autoMatchingService.execute(any(), any()))
                .willReturn(executionResult(result(
                        101L,
                        AutoMatchingProcessStatus.NEEDS_CHECK
                )));
        given(candidateService.findAllByBankTransactionId(101L))
                .willReturn(List.of(
                        candidateDto(
                                1L,
                                MatchingTargetType.SETTLEMENT
                        ),
                        candidateDto(2L, MatchingTargetType.LOAN)
                ));

        transactionService.process(
                USER_ID,
                LINKED_ACCOUNT_ID,
                transaction
        );

        verify(notificationService).createIfAbsent(
                USER_ID,
                NotificationType.BANK_TRANSACTION_MATCHING_REVIEW,
                "입금 거래 확인이 필요합니다.",
                "Hong Gil Dong님의 10000.00원 입금에 정산과 차용증 후보가 모두 발견되었습니다.",
                101L,
                LINKED_ACCOUNT_ID
        );
    }

    @Test
    @DisplayName("한 도메인의 후보만 있으면 매칭 검토 알림을 생성하지 않는다")
    void doesNotCreateNotificationForSingleDomainCandidates() {
        BankTransactionDTO transaction = bankTransaction(
                101L,
                "Hong Gil Dong"
        );
        givenLockedTransaction(transaction);
        given(paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                LINKED_ACCOUNT_ID,
                transaction.getTransactionAt()
        )).willReturn(List.of(candidate()));
        given(autoMatchingService.execute(any(), any()))
                .willReturn(executionResult(result(
                        101L,
                        AutoMatchingProcessStatus.NEEDS_CHECK
                )));
        given(candidateService.findAllByBankTransactionId(101L))
                .willReturn(List.of(candidateDto(
                        1L,
                        MatchingTargetType.SETTLEMENT
                )));

        transactionService.process(
                USER_ID,
                LINKED_ACCOUNT_ID,
                transaction
        );

        verify(notificationService, never()).createIfAbsent(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("잠금 조회한 거래가 이미 처리됐으면 자동매칭을 다시 실행하지 않는다")
    void skipsTransactionAlreadyProcessedByConcurrentRequest() {
        BankTransactionDTO transaction = bankTransaction(
                101L,
                "Hong Gil Dong",
                BankTransactionProcessingStatus.APPLIED
        );
        givenLockedTransaction(transaction);

        AutoMatchingTransactionResult result = transactionService.process(
                USER_ID,
                LINKED_ACCOUNT_ID,
                transaction
        );

        assertThat(result.processStatus())
                .isEqualTo(AutoMatchingProcessStatus.DUPLICATE);
        verify(paymentObligationMapper, never())
                .findMatchCandidatesByLinkedAccountId(any(), any());
        verify(autoMatchingService, never()).execute(any(), any());
        verify(candidateService, never())
                .findAllByBankTransactionId(any());
        verify(notificationService, never()).createIfAbsent(
                any(), any(), any(), any(), any(), any()
        );
        verify(bankTransactionService, never()).updateStatus(
                any(), any(), any()
        );
    }

    private BankTransactionDTO bankTransaction(
            Long bankTransactionId,
            String counterpartyName
    ) {
        return bankTransaction(
                bankTransactionId,
                counterpartyName,
                BankTransactionProcessingStatus.PENDING
        );
    }

    private BankTransactionDTO bankTransaction(
            Long bankTransactionId,
            String counterpartyName,
            BankTransactionProcessingStatus processingStatus
    ) {
        return BankTransactionDTO.builder()
                .bankTransactionId(bankTransactionId)
                .linkedAccountId(LINKED_ACCOUNT_ID)
                .externalTransactionId("external-" + bankTransactionId)
                .amount(new BigDecimal("10000.00"))
                .transactionType(BankTransactionType.DEPOSIT)
                .processingStatus(processingStatus)
                .transactionAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                .counterpartyName(counterpartyName)
                .syncedAt(LocalDateTime.of(2026, 8, 5, 10, 5))
                .build();
    }

    private void givenLockedTransaction(BankTransactionDTO transaction) {
        given(bankTransactionService.findByIdAndLinkedAccountIdForUpdate(
                transaction.getBankTransactionId(),
                LINKED_ACCOUNT_ID
        )).willReturn(transaction);
    }

    private MatchingCandidate candidate() {
        return new MatchingCandidate(
                MatchingTargetType.SETTLEMENT,
                1L,
                1L,
                "Hong Gil Dong",
                new BigDecimal("10000.00")
        );
    }

    private BankTransactionMatchCandidateDTO candidateDto(
            Long matchCandidateId,
            MatchingTargetType targetType
    ) {
        return BankTransactionMatchCandidateDTO.builder()
                .matchCandidateId(matchCandidateId)
                .bankTransactionId(101L)
                .targetType(targetType)
                .targetId(matchCandidateId * 10)
                .expectedRemainingAmount(new BigDecimal("10000.00"))
                .amountMatchType(MatchingAmountType.EXACT)
                .createdAt(LocalDateTime.of(2026, 8, 5, 10, 5))
                .build();
    }

    private AutoMatchingTransactionResult result(
            Long transactionId,
            AutoMatchingProcessStatus processStatus
    ) {
        return new AutoMatchingTransactionResult(
                transactionId,
                processStatus
        );
    }

    private AutoMatchingExecutionResult executionResult(
            AutoMatchingTransactionResult... transactionResults
    ) {
        int appliedCount = 0;
        int needsCheckCount = 0;
        int unmatchedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        for (AutoMatchingTransactionResult transactionResult
                : transactionResults) {
            switch (transactionResult.processStatus()) {
                case APPLIED -> appliedCount++;
                case NEEDS_CHECK -> needsCheckCount++;
                case UNMATCHED -> unmatchedCount++;
                case DUPLICATE -> duplicateCount++;
                case FAILED -> failedCount++;
            }
        }

        return new AutoMatchingExecutionResult(
                transactionResults.length,
                appliedCount,
                needsCheckCount,
                unmatchedCount,
                duplicateCount,
                failedCount,
                List.of(transactionResults)
        );
    }
}
