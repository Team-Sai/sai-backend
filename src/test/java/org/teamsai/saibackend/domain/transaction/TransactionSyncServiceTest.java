package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionResponse;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.service.TransactionSyncService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionSyncService 단위 테스트")
class TransactionSyncServiceTest {

    @Mock
    private LinkedBankAccountMapper linkedBankAccountMapper;

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @Mock
    private MockBankClient mockBankClient;

    @Mock
    private UserService userService;

    @InjectMocks
    private TransactionSyncService transactionSyncService;

    private static final Long LINKED_ACCOUNT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long BANK_ACCOUNT_ID = 3L;
    private static final String USER_KEY = "mb_rawUserKey1234";

    private LinkedBankAccountDTO createLinkedAccount() {
        return LinkedBankAccountDTO.builder()
                .linkedAccountId(LINKED_ACCOUNT_ID)
                .userId(USER_ID)
                .accountId(BANK_ACCOUNT_ID)
                .build();
    }

    private BankTransactionResponse createTransactionResponse(
            Long transactionId,
            String transactionKey
    ) {
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
    @DisplayName("syncTransactions(linkedAccountId)")
    class SyncTransactions {

        @Test
        @DisplayName("커서가 없으면(null) 0부터 조회하고, 새 거래를 저장한 뒤 커서를 최신 거래 ID로 갱신한다")
        void syncsNewTransactionsWhenNoCursorExists() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();
            BankTransactionResponse tx1 = createTransactionResponse(6L, "MOCK-TX-0001");
            BankTransactionResponse tx2 = createTransactionResponse(7L, "MOCK-TX-0002");

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(null);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 0L))
                    .willReturn(List.of(tx1, tx2));

            int result = transactionSyncService.syncTransactions(LINKED_ACCOUNT_ID);

            assertThat(result).isEqualTo(2);

            ArgumentCaptor<BankTransactionDTO> dtoCaptor = ArgumentCaptor.forClass(BankTransactionDTO.class);
            verify(bankTransactionMapper, times(2)).insertOrGetId(dtoCaptor.capture());

            List<BankTransactionDTO> savedDtos = dtoCaptor.getAllValues();
            assertThat(savedDtos)
                    .extracting(BankTransactionDTO::getExternalTransactionId)
                    .containsExactly("MOCK-TX-0001", "MOCK-TX-0002");
            assertThat(savedDtos)
                    .allMatch(dto -> dto.getLinkedAccountId().equals(LINKED_ACCOUNT_ID))
                    .allMatch(dto -> dto.getTransactionType() == BankTransactionType.DEPOSIT)
                    .allMatch(dto -> dto.getCounterpartyName().equals("홍길동"))
                    .allMatch(dto -> dto.getSyncedAt() != null);

            verify(linkedBankAccountMapper).updateLastSyncedTransactionId(LINKED_ACCOUNT_ID, 7L);
        }

        @Test
        @DisplayName("이미 커서가 있으면 그 값 이후로만 조회한다")
        void usesExistingCursorAsAfterTransactionId() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(5L);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 5L)).willReturn(List.of());

            transactionSyncService.syncTransactions(LINKED_ACCOUNT_ID);

            verify(mockBankClient).getTransactions(BANK_ACCOUNT_ID, USER_KEY, 5L);
        }

        @Test
        @DisplayName("새 거래가 없으면 저장/커서 갱신 없이 0을 반환한다")
        void doesNothingWhenNoNewTransactions() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(5L);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 5L)).willReturn(List.of());

            int result = transactionSyncService.syncTransactions(LINKED_ACCOUNT_ID);

            assertThat(result).isZero();
            verify(bankTransactionMapper, never()).insertOrGetId(any());
            verify(linkedBankAccountMapper, never()).updateLastSyncedTransactionId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("연동계좌를 찾을 수 없으면 LINKED_ACCOUNT_NOT_FOUND 예외를 던지고 이후 로직은 실행되지 않는다")
        void throwsWhenLinkedAccountNotFound() {
            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> transactionSyncService.syncTransactions(LINKED_ACCOUNT_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.LINKED_ACCOUNT_NOT_FOUND);

            verify(userService, never()).getUserKeyByUserId(any());
            verify(mockBankClient, never()).getTransactions(any(), any(), any());
        }

        @Test
        @DisplayName("사이은행 통신 실패 시 BANK_SERVER_UNAVAILABLE 예외로 변환하고 저장/커서 갱신은 실행되지 않는다")
        void throwsWhenBankServerUnavailable() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(0L);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 0L))
                    .willThrow(new RestClientException("연결 실패"));

            assertThatThrownBy(() -> transactionSyncService.syncTransactions(LINKED_ACCOUNT_ID))
                    .isInstanceOf(DomainException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.BANK_SERVER_UNAVAILABLE);

            verify(bankTransactionMapper, never()).insertOrGetId(any());
            verify(linkedBankAccountMapper, never()).updateLastSyncedTransactionId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("여러 거래를 가져오면 그 중 가장 마지막(리스트 끝) 거래의 ID로 커서를 갱신한다")
        void updatesCursorToLastTransactionInList() {
            LinkedBankAccountDTO linkedAccount = createLinkedAccount();
            BankTransactionResponse tx1 = createTransactionResponse(11L, "MOCK-TX-A");
            BankTransactionResponse tx2 = createTransactionResponse(12L, "MOCK-TX-B");
            BankTransactionResponse tx3 = createTransactionResponse(13L, "MOCK-TX-C");

            given(linkedBankAccountMapper.findById(LINKED_ACCOUNT_ID)).willReturn(Optional.of(linkedAccount));
            given(userService.getUserKeyByUserId(USER_ID)).willReturn(USER_KEY);
            given(linkedBankAccountMapper.findLastSyncedTransactionIdById(LINKED_ACCOUNT_ID)).willReturn(10L);
            given(mockBankClient.getTransactions(BANK_ACCOUNT_ID, USER_KEY, 10L))
                    .willReturn(List.of(tx1, tx2, tx3));

            int result = transactionSyncService.syncTransactions(LINKED_ACCOUNT_ID);

            assertThat(result).isEqualTo(3);
            verify(linkedBankAccountMapper).updateLastSyncedTransactionId(eq(LINKED_ACCOUNT_ID), eq(13L));
        }
    }
}