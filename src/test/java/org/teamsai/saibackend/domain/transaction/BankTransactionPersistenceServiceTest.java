package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionResponse;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionPersistenceService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 순수 DB 저장/커서 갱신 책임만 검증한다.
 * - 커서는 실제 최댓값 기준으로 갱신
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BankTransactionPersistenceService 단위 테스트")
class BankTransactionPersistenceServiceTest {

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @Mock
    private LinkedBankAccountMapper linkedBankAccountMapper;

    @InjectMocks
    private BankTransactionPersistenceService bankTransactionPersistenceService;

    private static final Long LINKED_ACCOUNT_ID = 1L;
    private static final Long BANK_ACCOUNT_ID = 3L;

    private BankTransactionResponse createTransactionResponse(
            Long transactionId, String transactionKey, String transactionType
    ) {
        return createTransactionResponse(transactionId, transactionKey, transactionType,
                BigDecimal.valueOf(150_000));
    }

    private BankTransactionResponse createTransactionResponse(
            Long transactionId, String transactionKey, String transactionType,
            BigDecimal balanceAfter
    ) {
        return new BankTransactionResponse(
                transactionId,
                transactionKey,
                BANK_ACCOUNT_ID,
                transactionType,
                BigDecimal.valueOf(50_000),
                balanceAfter,
                "홍길동",
                "110-***-1234",
                "테스트 입금",
                LocalDateTime.now(),
                1L
        );
    }

    @Test
    @DisplayName("빈 리스트가 들어오면 저장/커서 갱신 없이 0을 반환한다")
    void returnsZeroWhenTransactionsEmpty() {
        int result = bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, List.of());

        assertThat(result).isZero();
        verify(bankTransactionMapper, never()).insertOrGetId(any());
        verify(linkedBankAccountMapper, never()).updateLastSyncedTransactionId(any(), any());
    }

    @Test
    @DisplayName("모든 거래를 저장하고, 응답 순서와 무관하게 실제 최댓값 transactionId로 커서를 갱신한다")
    void savesAllAndAdvancesCursorToMaxTransactionId() {
        // 정렬을 일부러 깨서 응답: 리스트 마지막 원소는 12, 실제 최댓값은 13
        List<BankTransactionResponse> transactions = List.of(
                createTransactionResponse(11L, "MOCK-TX-A", "DEPOSIT"),
                createTransactionResponse(13L, "MOCK-TX-B", "DEPOSIT"),
                createTransactionResponse(12L, "MOCK-TX-C", "WITHDRAWAL")
        );

        int result = bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions);

        assertThat(result).isEqualTo(3);

        ArgumentCaptor<BankTransactionDTO> dtoCaptor = ArgumentCaptor.forClass(BankTransactionDTO.class);
        verify(bankTransactionMapper, times(3)).insertOrGetId(dtoCaptor.capture());

        List<BankTransactionDTO> savedDtos = dtoCaptor.getAllValues();
        assertThat(savedDtos)
                .extracting(BankTransactionDTO::getExternalTransactionId)
                .containsExactlyInAnyOrder("MOCK-TX-A", "MOCK-TX-B", "MOCK-TX-C");
        assertThat(savedDtos)
                .allMatch(dto -> dto.getLinkedAccountId().equals(LINKED_ACCOUNT_ID))
                .allMatch(dto -> dto.getSyncedAt() != null);

        // 마지막 원소(12)가 아니라 실제 최댓값(13)으로 갱신되어야 한다.
        verify(linkedBankAccountMapper).updateLastSyncedTransactionId(eq(LINKED_ACCOUNT_ID), eq(13L));
        verify(linkedBankAccountMapper).updateBalance(
                eq(LINKED_ACCOUNT_ID), eq(BigDecimal.valueOf(150_000)));
    }

    @Test
    @DisplayName("가장 최근 거래의 balanceAfter를 연결 계좌 잔액으로 갱신한다")
    void updatesBalanceFromLatestTransaction() {
        List<BankTransactionResponse> transactions = List.of(
                createTransactionResponse(11L, "MOCK-TX-A", "DEPOSIT",
                        BigDecimal.valueOf(120_000)),
                createTransactionResponse(13L, "MOCK-TX-B", "DEPOSIT",
                        BigDecimal.valueOf(180_000)),
                createTransactionResponse(12L, "MOCK-TX-C", "WITHDRAWAL",
                        BigDecimal.valueOf(150_000))
        );

        bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions);

        verify(linkedBankAccountMapper).updateBalance(
                eq(LINKED_ACCOUNT_ID), eq(BigDecimal.valueOf(180_000)));
    }

    @Test
    @DisplayName("가장 최근 거래의 balanceAfter가 null이면 잔액을 갱신하지 않는다")
    void skipsBalanceUpdateWhenLatestBalanceIsNull() {
        List<BankTransactionResponse> transactions = List.of(
                createTransactionResponse(11L, "MOCK-TX-A", "DEPOSIT",
                        BigDecimal.valueOf(120_000)),
                createTransactionResponse(13L, "MOCK-TX-B", "DEPOSIT", null)
        );

        bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions);

        verify(linkedBankAccountMapper, never()).updateBalance(any(), any());
        verify(linkedBankAccountMapper).updateLastSyncedTransactionId(
                eq(LINKED_ACCOUNT_ID), eq(13L));
    }

    @Test
    @DisplayName("거래유형 문자열이 올바르지 않으면 INVALID_BANK_RESPONSE 예외를 던지고 커서는 갱신하지 않는다")
    void throwsInvalidBankResponseWhenTransactionTypeIsUnknown() {
        List<BankTransactionResponse> transactions = List.of(
                createTransactionResponse(11L, "MOCK-TX-A", "DEPOSIT"),
                createTransactionResponse(12L, "MOCK-TX-B", "UNKNOWN_TYPE") // 잘못된 값
        );

        assertThatThrownBy(() ->
                bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions)
        )
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_BANK_RESPONSE);

        verify(linkedBankAccountMapper, never()).updateLastSyncedTransactionId(any(), any());
    }

    @Test
    @DisplayName("정상적으로 정의된 거래유형(DEPOSIT/WITHDRAWAL)은 올바르게 매핑된다")
    void mapsKnownTransactionTypesCorrectly() {
        List<BankTransactionResponse> transactions = List.of(
                createTransactionResponse(1L, "MOCK-TX-A", "DEPOSIT"),
                createTransactionResponse(2L, "MOCK-TX-B", "WITHDRAWAL")
        );

        bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions);

        ArgumentCaptor<BankTransactionDTO> dtoCaptor = ArgumentCaptor.forClass(BankTransactionDTO.class);
        verify(bankTransactionMapper, times(2)).insertOrGetId(dtoCaptor.capture());

        List<BankTransactionDTO> savedDtos = dtoCaptor.getAllValues();
        assertThat(savedDtos.get(0).getTransactionType()).isEqualTo(BankTransactionType.DEPOSIT);
        assertThat(savedDtos.get(1).getTransactionType()).isEqualTo(BankTransactionType.WITHDRAWAL);
    }

    @Test
    @DisplayName("거래 저장(insertOrGetId) 중 DB 예외가 발생하면 그대로 전파하고 커서는 갱신하지 않는다")
    void propagatesExceptionWhenInsertFails() {
        List<BankTransactionResponse> transactions = List.of(
                createTransactionResponse(11L, "MOCK-TX-A", "DEPOSIT"),
                createTransactionResponse(12L, "MOCK-TX-B", "DEPOSIT")
        );
        DataIntegrityViolationException insertFailure =
                new DataIntegrityViolationException("제약 위반");

        willThrow(insertFailure).given(bankTransactionMapper).insertOrGetId(any());

        assertThatThrownBy(() ->
                bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions)
        )
                .isSameAs(insertFailure);

        // 첫 거래 저장 시점에 이미 실패했으므로, 커서는 절대 갱신되지 않아야 한다.
        verify(linkedBankAccountMapper, never()).updateLastSyncedTransactionId(any(), any());
    }

    @Test
    @DisplayName("커서 갱신(updateLastSyncedTransactionId) 중 DB 예외가 발생하면 그대로 전파한다")
    void propagatesExceptionWhenCursorUpdateFails() {
        List<BankTransactionResponse> transactions = List.of(
                createTransactionResponse(11L, "MOCK-TX-A", "DEPOSIT")
        );
        DataIntegrityViolationException cursorUpdateFailure =
                new DataIntegrityViolationException("커서 갱신 실패");

        willThrow(cursorUpdateFailure)
                .given(linkedBankAccountMapper)
                .updateLastSyncedTransactionId(eq(LINKED_ACCOUNT_ID), eq(11L));

        assertThatThrownBy(() ->
                bankTransactionPersistenceService.saveAndAdvanceCursor(LINKED_ACCOUNT_ID, transactions)
        )
                .isSameAs(cursorUpdateFailure);

        // 거래 저장 자체는 커서 갱신 이전에 이미 시도되었어야 한다.
        verify(bankTransactionMapper).insertOrGetId(any());
    }
}
