package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.service.BankMatchingService;
import org.teamsai.saibackend.domain.matching.service.BankMatchingTransactionService;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankMatchingService 단위 테스트")
class BankMatchingServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long LINKED_ACCOUNT_ID = 1L;

    @Mock
    private BankTransactionService bankTransactionService;

    @Mock
    private BankMatchingTransactionService transactionService;

    @InjectMocks
    private BankMatchingService bankMatchingService;

    @Test
    @DisplayName("처리 대기 거래가 없으면 빈 결과를 반환한다")
    void returnsEmptyResultWhenPendingDepositsDoNotExist() {
        given(bankTransactionService
                .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                .willReturn(List.of());

        AutoMatchingExecutionResult result =
                bankMatchingService.execute(USER_ID, LINKED_ACCOUNT_ID, false);

        assertThat(result.totalTransactionCount()).isZero();
        assertThat(result.transactionResults()).isEmpty();
        verify(transactionService, never()).process(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean()
        );
    }

    @Test
    @DisplayName("각 거래를 조회 순서대로 처리하고 결과를 집계한다")
    void processesTransactionsInOrderAndAggregatesResults() {
        BankTransactionDTO first = bankTransaction(101L);
        BankTransactionDTO second = bankTransaction(102L);
        BankTransactionDTO third = bankTransaction(103L);

        given(bankTransactionService
                .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                .willReturn(List.of(first, second, third));
        given(transactionService.process(USER_ID, LINKED_ACCOUNT_ID, first, false))
                .willReturn(result(101L, AutoMatchingProcessStatus.APPLIED));
        given(transactionService.process(USER_ID, LINKED_ACCOUNT_ID, second, false))
                .willReturn(result(102L, AutoMatchingProcessStatus.NEEDS_CHECK));
        given(transactionService.process(USER_ID, LINKED_ACCOUNT_ID, third, false))
                .willReturn(result(103L, AutoMatchingProcessStatus.UNMATCHED));

        AutoMatchingExecutionResult result =
                bankMatchingService.execute(USER_ID, LINKED_ACCOUNT_ID, false);

        assertThat(result.totalTransactionCount()).isEqualTo(3);
        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.needsCheckCount()).isEqualTo(1);
        assertThat(result.unmatchedCount()).isEqualTo(1);
        assertThat(result.transactionResults())
                .extracting(
                        AutoMatchingTransactionResult::transactionId,
                        AutoMatchingTransactionResult::processStatus
                )
                .containsExactly(
                        tuple(101L, AutoMatchingProcessStatus.APPLIED),
                        tuple(102L, AutoMatchingProcessStatus.NEEDS_CHECK),
                        tuple(103L, AutoMatchingProcessStatus.UNMATCHED)
                );
    }

    @Test
    @DisplayName("배치 실행 여부를 거래 처리 단계까지 그대로 전달한다")
    void passesBatchFlagDownToTransactionProcessing() {
        BankTransactionDTO transaction = bankTransaction(101L);

        given(bankTransactionService
                .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                .willReturn(List.of(transaction));
        given(transactionService.process(USER_ID, LINKED_ACCOUNT_ID, transaction, true))
                .willReturn(result(101L, AutoMatchingProcessStatus.NEEDS_CHECK));

        bankMatchingService.execute(USER_ID, LINKED_ACCOUNT_ID, true);

        verify(transactionService).process(USER_ID, LINKED_ACCOUNT_ID, transaction, true);
    }

    @Test
    @DisplayName("단건 처리 실패를 그대로 전파한다")
    void propagatesTransactionProcessingFailure() {
        BankTransactionDTO transaction = bankTransaction(101L);
        DomainException exception = MatchingErrorCode
                .INVALID_MATCHING_REQUEST
                .toException();

        given(bankTransactionService
                .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                .willReturn(List.of(transaction));
        willThrow(exception)
                .given(transactionService)
                .process(USER_ID, LINKED_ACCOUNT_ID, transaction, false);

        assertThatThrownBy(
                () -> bankMatchingService.execute(USER_ID, LINKED_ACCOUNT_ID, false)
        ).isSameAs(exception);
    }

    @Test
    @DisplayName("연결 계좌 ID가 유효하지 않으면 예외가 발생한다")
    void throwsExceptionWhenLinkedAccountIdIsInvalid() {
        assertThatThrownBy(() -> bankMatchingService.execute(USER_ID, 0L, false))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        MatchingErrorCode.INVALID_MATCHING_REQUEST
                                )
                );
    }

    private BankTransactionDTO bankTransaction(Long bankTransactionId) {
        return BankTransactionDTO.builder()
                .bankTransactionId(bankTransactionId)
                .linkedAccountId(LINKED_ACCOUNT_ID)
                .externalTransactionId("external-" + bankTransactionId)
                .amount(new BigDecimal("10000.00"))
                .transactionType(BankTransactionType.DEPOSIT)
                .processingStatus(BankTransactionProcessingStatus.PENDING)
                .transactionAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                .counterpartyName("Hong Gil Dong")
                .syncedAt(LocalDateTime.of(2026, 8, 5, 10, 5))
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
}
