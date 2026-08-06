package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.service.BankMatchingService;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.transaction.service.TransactionSyncFacade;
import org.teamsai.saibackend.domain.transaction.service.TransactionSyncService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionSyncFacade 단위 테스트")
class TransactionSyncFacadeTest {

    @Mock
    private TransactionSyncService transactionSyncService;

    @Mock
    private BankMatchingService bankMatchingService;

    @InjectMocks
    private TransactionSyncFacade transactionSyncFacade;

    private static final Long USER_ID = 10L;
    private static final Long LINKED_ACCOUNT_ID = 1L;

    @Test
    @DisplayName("동기화를 먼저 실행한 뒤 매칭을 실행하고, 매칭 결과를 그대로 반환한다")
    void syncsBeforeMatchingAndReturnsMatchingResult() {
        AutoMatchingTransactionResult transactionResult =
                new AutoMatchingTransactionResult(100L, AutoMatchingProcessStatus.UNMATCHED);

        AutoMatchingExecutionResult expectedResult = new AutoMatchingExecutionResult(
                1, 0, 0, 1, 0, 0, List.of(transactionResult)
        );

        given(transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID)).willReturn(1);
        given(bankMatchingService.execute(LINKED_ACCOUNT_ID)).willReturn(expectedResult);

        AutoMatchingExecutionResult result = transactionSyncFacade.syncAndMatch(USER_ID, LINKED_ACCOUNT_ID);

        assertThat(result).isEqualTo(expectedResult);

        InOrder inOrder = inOrder(transactionSyncService, bankMatchingService);
        inOrder.verify(transactionSyncService).syncTransactions(USER_ID, LINKED_ACCOUNT_ID);
        inOrder.verify(bankMatchingService).execute(LINKED_ACCOUNT_ID);
    }

    @Test
    @DisplayName("동기화된 거래가 없어도 매칭은 항상 실행된다")
    void alwaysRunsMatchingEvenWhenNoNewTransactionsSynced() {
        AutoMatchingExecutionResult emptyResult = new AutoMatchingExecutionResult(
                0, 0, 0, 0, 0, 0, List.of()
        );

        given(transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID)).willReturn(0);
        given(bankMatchingService.execute(LINKED_ACCOUNT_ID)).willReturn(emptyResult);

        AutoMatchingExecutionResult result = transactionSyncFacade.syncAndMatch(USER_ID, LINKED_ACCOUNT_ID);

        assertThat(result.totalTransactionCount()).isZero();
        verify(bankMatchingService).execute(LINKED_ACCOUNT_ID);
    }
}