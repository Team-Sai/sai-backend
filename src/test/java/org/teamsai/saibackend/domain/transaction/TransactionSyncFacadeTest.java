package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.service.BankMatchingService;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.transaction.dto.response.TransactionSyncAllResponse;
import org.teamsai.saibackend.domain.transaction.service.TransactionSyncFacade;
import org.teamsai.saibackend.domain.transaction.service.TransactionSyncService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionSyncFacade 단위 테스트")
class TransactionSyncFacadeTest {

    @Mock
    private TransactionSyncService transactionSyncService;

    @Mock
    private BankMatchingService bankMatchingService;

    @Mock
    private LinkedBankAccountService linkedBankAccountService;

    @InjectMocks
    private TransactionSyncFacade transactionSyncFacade;

    private static final Long USER_ID = 10L;
    private static final Long LINKED_ACCOUNT_ID = 1L;
    private static final Long SECOND_LINKED_ACCOUNT_ID = 2L;

    @Test
    @DisplayName("동기화를 먼저 실행한 뒤 매칭을 실행하고, 매칭 결과를 그대로 반환한다")
    void syncsBeforeMatchingAndReturnsMatchingResult() {
        AutoMatchingTransactionResult transactionResult =
                new AutoMatchingTransactionResult(100L, AutoMatchingProcessStatus.UNMATCHED);

        AutoMatchingExecutionResult expectedResult = new AutoMatchingExecutionResult(
                1, 0, 0, 1, 0, 0, List.of(transactionResult)
        );

        given(transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID)).willReturn(1);
        given(bankMatchingService.execute(USER_ID, LINKED_ACCOUNT_ID, false)).willReturn(expectedResult);

        AutoMatchingExecutionResult result = transactionSyncFacade.syncAndMatch(USER_ID, LINKED_ACCOUNT_ID, false);

        assertThat(result).isEqualTo(expectedResult);

        InOrder inOrder = inOrder(transactionSyncService, bankMatchingService);
        inOrder.verify(transactionSyncService).syncTransactions(USER_ID, LINKED_ACCOUNT_ID);
        inOrder.verify(bankMatchingService).execute(USER_ID, LINKED_ACCOUNT_ID, false);
    }

    @Test
    @DisplayName("배치로 호출되면 매칭 단계에도 배치 여부를 그대로 전달한다")
    void passesBatchFlagToMatchingWhenTriggeredByBatch() {
        AutoMatchingExecutionResult expectedResult = new AutoMatchingExecutionResult(
                0, 0, 0, 0, 0, 0, List.of()
        );

        given(transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID)).willReturn(0);
        given(bankMatchingService.execute(USER_ID, LINKED_ACCOUNT_ID, true)).willReturn(expectedResult);

        transactionSyncFacade.syncAndMatch(USER_ID, LINKED_ACCOUNT_ID, true);

        verify(bankMatchingService).execute(USER_ID, LINKED_ACCOUNT_ID, true);
    }

    @Test
    @DisplayName("동기화된 거래가 없어도 매칭은 항상 실행된다")
    void alwaysRunsMatchingEvenWhenNoNewTransactionsSynced() {
        AutoMatchingExecutionResult emptyResult = new AutoMatchingExecutionResult(
                0, 0, 0, 0, 0, 0, List.of()
        );

        given(transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID)).willReturn(0);
        given(bankMatchingService.execute(USER_ID, LINKED_ACCOUNT_ID, false)).willReturn(emptyResult);

        AutoMatchingExecutionResult result = transactionSyncFacade.syncAndMatch(USER_ID, LINKED_ACCOUNT_ID, false);

        assertThat(result.totalTransactionCount()).isZero();
        verify(bankMatchingService).execute(USER_ID, LINKED_ACCOUNT_ID, false);
    }

    @Test
    @DisplayName("동기화 단계에서 예외가 발생하면 매칭을 실행하지 않고 예외를 그대로 전파한다")
    void propagatesExceptionWhenSyncFails() {
        DomainException syncFailure = mock(DomainException.class);

        willThrow(syncFailure)
                .given(transactionSyncService).syncTransactions(USER_ID, LINKED_ACCOUNT_ID);

        assertThatThrownBy(() -> transactionSyncFacade.syncAndMatch(USER_ID, LINKED_ACCOUNT_ID, false))
                .isSameAs(syncFailure);

        verify(bankMatchingService, never()).execute(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("동기화는 성공했지만 매칭 단계에서 예외가 발생하면 예외를 그대로 전파한다")
    void propagatesExceptionWhenMatchingFails() {
        DomainException matchingFailure = mock(DomainException.class);

        given(transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID)).willReturn(2);
        willThrow(matchingFailure)
                .given(bankMatchingService).execute(USER_ID, LINKED_ACCOUNT_ID, false);

        assertThatThrownBy(() -> transactionSyncFacade.syncAndMatch(USER_ID, LINKED_ACCOUNT_ID, false))
                .isSameAs(matchingFailure);

        verify(transactionSyncService).syncTransactions(USER_ID, LINKED_ACCOUNT_ID);
    }

    @Test
    @DisplayName("전체 동기화는 사용자의 모든 연동계좌를 계좌별로 동기화하고 매칭 결과를 합산한다")
    void syncAllSyncsEveryLinkedAccountAndAggregatesMatchingResults() {
        given(linkedBankAccountService.getLinkedAccounts(USER_ID))
                .willReturn(List.of(
                        linkedAccount(LINKED_ACCOUNT_ID),
                        linkedAccount(SECOND_LINKED_ACCOUNT_ID)
                ));

        AutoMatchingExecutionResult firstResult =
                new AutoMatchingExecutionResult(
                        2,
                        1,
                        1,
                        0,
                        0,
                        0,
                        List.of(
                                new AutoMatchingTransactionResult(
                                        100L,
                                        AutoMatchingProcessStatus.APPLIED
                                ),
                                new AutoMatchingTransactionResult(
                                        101L,
                                        AutoMatchingProcessStatus.NEEDS_CHECK
                                )
                        )
                );

        AutoMatchingExecutionResult secondResult =
                new AutoMatchingExecutionResult(
                        3,
                        1,
                        0,
                        1,
                        1,
                        0,
                        List.of(
                                new AutoMatchingTransactionResult(
                                        200L,
                                        AutoMatchingProcessStatus.APPLIED
                                ),
                                new AutoMatchingTransactionResult(
                                        201L,
                                        AutoMatchingProcessStatus.UNMATCHED
                                ),
                                new AutoMatchingTransactionResult(
                                        202L,
                                        AutoMatchingProcessStatus.DUPLICATE
                                )
                        )
                );

        given(transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID))
                .willReturn(2);
        given(transactionSyncService.syncTransactions(USER_ID, SECOND_LINKED_ACCOUNT_ID))
                .willReturn(3);
        given(bankMatchingService.execute(USER_ID, LINKED_ACCOUNT_ID, false))
                .willReturn(firstResult);
        given(bankMatchingService.execute(USER_ID, SECOND_LINKED_ACCOUNT_ID, false))
                .willReturn(secondResult);

        TransactionSyncAllResponse result =
                transactionSyncFacade.syncAll(USER_ID);

        assertThat(result.syncedAccountCount()).isEqualTo(2);
        assertThat(result.totalTransactionCount()).isEqualTo(5);
        assertThat(result.appliedCount()).isEqualTo(2);
        assertThat(result.needsCheckCount()).isEqualTo(1);
        assertThat(result.unmatchedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();

        InOrder inOrder = inOrder(
                linkedBankAccountService,
                transactionSyncService,
                bankMatchingService
        );
        inOrder.verify(linkedBankAccountService).getLinkedAccounts(USER_ID);
        inOrder.verify(transactionSyncService)
                .syncTransactions(USER_ID, LINKED_ACCOUNT_ID);
        inOrder.verify(bankMatchingService).execute(USER_ID, LINKED_ACCOUNT_ID, false);
        inOrder.verify(transactionSyncService)
                .syncTransactions(USER_ID, SECOND_LINKED_ACCOUNT_ID);
        inOrder.verify(bankMatchingService).execute(
                USER_ID,
                SECOND_LINKED_ACCOUNT_ID,
                false
        );
    }

    @Test
    @DisplayName("계좌 하나의 동기화가 실패해도 나머지 계좌를 계속 처리한다")
    void syncAllContinuesWhenOneAccountFails() {
        given(linkedBankAccountService.getLinkedAccounts(USER_ID))
                .willReturn(List.of(
                        linkedAccount(LINKED_ACCOUNT_ID),
                        linkedAccount(SECOND_LINKED_ACCOUNT_ID)
                ));

        DomainException syncFailure =
                AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        AutoMatchingExecutionResult successResult =
                new AutoMatchingExecutionResult(
                        1, 1, 0, 0, 0, 0,
                        List.of(new AutoMatchingTransactionResult(
                                200L,
                                AutoMatchingProcessStatus.APPLIED
                        ))
                );

        willThrow(syncFailure)
                .given(transactionSyncService)
                .syncTransactions(USER_ID, LINKED_ACCOUNT_ID);
        given(transactionSyncService.syncTransactions(USER_ID, SECOND_LINKED_ACCOUNT_ID))
                .willReturn(1);
        given(bankMatchingService.execute(USER_ID, SECOND_LINKED_ACCOUNT_ID, false))
                .willReturn(successResult);

        TransactionSyncAllResponse result = transactionSyncFacade.syncAll(USER_ID);

        assertThat(result.syncedAccountCount()).isEqualTo(1);
        assertThat(result.failedAccounts()).hasSize(1);
        assertThat(result.failedAccounts().get(0).linkedAccountId())
                .isEqualTo(LINKED_ACCOUNT_ID);
        assertThat(result.failedAccounts().get(0).errorCode())
                .isEqualTo("BANK_SERVER_UNAVAILABLE");
        assertThat(result.totalTransactionCount()).isEqualTo(1);
        assertThat(result.appliedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 동기화는 연동계좌가 없으면 동기화와 매칭을 실행하지 않고 빈 결과를 반환한다")
    void syncAllReturnsEmptyResultWhenUserHasNoLinkedAccounts() {
        given(linkedBankAccountService.getLinkedAccounts(USER_ID))
                .willReturn(List.of());

        TransactionSyncAllResponse result =
                transactionSyncFacade.syncAll(USER_ID);

        assertThat(result.syncedAccountCount()).isZero();
        assertThat(result.totalTransactionCount()).isZero();
        assertThat(result.appliedCount()).isZero();
        assertThat(result.needsCheckCount()).isZero();
        assertThat(result.unmatchedCount()).isZero();
        assertThat(result.duplicateCount()).isZero();
        assertThat(result.failedCount()).isZero();

        verify(transactionSyncService, never()).syncTransactions(any(), any());
        verify(bankMatchingService, never()).execute(any(), any(), anyBoolean());
    }

    private LinkedBankAccountResponse linkedAccount(Long linkedAccountId) {
        return new LinkedBankAccountResponse(
                linkedAccountId,
                "088",
                "신한은행",
                "110-***-123456",
                "정산 계좌",
                "홍길동",
                BigDecimal.ZERO,
                "AVAILABLE"
        );
    }
}
