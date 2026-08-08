package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionResponse;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionPersistenceService;
import org.teamsai.saibackend.domain.transaction.service.TransactionSyncService;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TransactionSyncService 단위 테스트.
 *
 * 소유권 검증, 커서 조회, 사이은행 API 호출 조율만 담당하므로
 * 실제 DB 저장 로직은 목으로 대체하고,
 * "저장 서비스가 올바른 인자로 호출되는지"까지만 검증한다.
 * 저장/커서 갱신 로직 자체의 검증은 BankTransactionPersistenceServiceTest에서 다룬다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionSyncService 단위 테스트")
class TransactionSyncServiceTest {

    @Mock
    private LinkedBankAccountMapper linkedBankAccountMapper;

    @Mock
    private MockBankClient mockBankClient;

    @Mock
    private UserService userService;

    @Mock
    private BankTransactionPersistenceService bankTransactionPersistenceService;

    @InjectMocks
    private TransactionSyncService transactionSyncService;

    private static final Long LINKED_ACCOUNT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long BANK_ACCOUNT_ID = 3L;
    private static final String USER_KEY = "mb_rawUserKey1234";

    private LinkedBankAccountDTO createLinkedAccount() {
        return LinkedBankAccountDTO.builder()
                .linkedAccountId(LINKED_ACCOUNT_ID)
                .userId(USER_ID)
                .accountId(BANK_ACCOUNT_ID)
                .build();
    }

    private BankTransactionResponse createTransactionResponse(Long transactionId, String transactionKey) {
        return new BankTransactionResponse(
                transactionId,
                transactionKey,
                BANK_ACCOUNT_ID,
                "DEPOSIT",
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(150_000),
                "홍길동",
                "110-***-1234",
                "테스트 입금",
                LocalDateTime.now(),
                1L
        );
    }

    @Nested
    @DisplayName("syncTransactions(userId, linkedAccountId)")
    class SyncTransactions {

        @Test
        @DisplayName("커서가 없으면(null) 0부터 조회하고, 조회 결과를 저장 서비스에 그대로 위임한다")
        void syncsNewTransactionsWhenNoCursorExists() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();
            List<BankTransactionResponse> transactions = List.of(
                    createTransactionResponse(6L, "MOCK-TX-0001"),
                    createTransactionResponse(7L, "MOCK-TX-0002")
            );

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(null);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 0L)).willReturn(transactions);
            given(bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions))
                    .willReturn(2);

            int result = transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID);

            assertThat(result).isEqualTo(2);
            verify(bankTransactionPersistenceService).saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions);
        }

        @Test
        @DisplayName("이미 커서가 있으면 그 값 이후로만 조회한다")
        void usesExistingCursorAsAfterTransactionId() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(5L);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 5L)).willReturn(List.of());
            given(bankTransactionPersistenceService.saveAndAdvanceCursor(eq(LINKED_ACCOUNT_ID), any()))
                    .willReturn(0);

            transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID);

            verify(mockBankClient).getTransactions(BANK_ACCOUNT_ID, USER_KEY, 5L);
        }

        @Test
        @DisplayName("연동계좌를 찾을 수 없으면 LINKED_ACCOUNT_NOT_FOUND 예외를 던지고 이후 로직은 실행되지 않는다")
        void throwsWhenLinkedAccountNotFound() {
            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.LINKED_ACCOUNT_NOT_FOUND);

            verify(userService, never()).getUserKeyByUserId(any());
            verify(mockBankClient, never()).getTransactions(any(), any(), any());
            verify(bankTransactionPersistenceService, never()).saveAndAdvanceCursor(any(), any());
        }

        @Test
        @DisplayName("요청자가 연동계좌의 소유자가 아니면 ACCOUNT_ACCESS_DENIED 예외를 던지고 이후 로직은 실행되지 않는다")
        void throwsWhenRequesterIsNotOwner() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount(); // userId = USER_ID(10L) 소유

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));

            assertThatThrownBy(() -> transactionSyncService.syncTransactions(OTHER_USER_ID, LINKED_ACCOUNT_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);

            verify(userService, never()).getUserKeyByUserId(any());
            verify(mockBankClient, never()).getTransactions(any(), any(), any());
            verify(bankTransactionPersistenceService, never()).saveAndAdvanceCursor(any(), any());
        }

        @Test
        @DisplayName("사이은행 통신 실패 시 BANK_SERVER_UNAVAILABLE 예외로 변환하고 저장은 시도하지 않는다")
        void throwsWhenBankServerUnavailable() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(0L);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 0L))
                    .willThrow(new RestClientException("연결 실패"));

            assertThatThrownBy(() -> transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.BANK_SERVER_UNAVAILABLE);

            verify(bankTransactionPersistenceService, never()).saveAndAdvanceCursor(any(), any());
        }

        @Test
        @DisplayName("저장 단계(BankTransactionPersistenceService)에서 예외가 발생하면 그대로 전파한다")
        void propagatesExceptionWhenPersistenceFails() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();
            List<BankTransactionResponse> transactions = List.of(
                    createTransactionResponse(6L, "MOCK-TX-0001")
            );
            DomainException persistenceFailure = mock(DomainException.class);

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(5L);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 5L)).willReturn(transactions);
            willThrow(persistenceFailure)
                    .given(bankTransactionPersistenceService)
                    .saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions);

            assertThatThrownBy(() -> transactionSyncService.syncTransactions(USER_ID, LINKED_ACCOUNT_ID))
                    .isSameAs(persistenceFailure);

            // 사이은행 조회 자체는 이미 성공적으로 끝난 뒤, 저장 단계에서만 실패한 상황임을 확인한다.
            verify(mockBankClient).getTransactions(BANK_ACCOUNT_ID, USER_KEY, 5L);
        }
    }
}