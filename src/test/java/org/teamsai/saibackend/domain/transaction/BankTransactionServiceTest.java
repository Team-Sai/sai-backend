package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.exception.BankTransactionErrorCode;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankTransactionService 단위 테스트")
class BankTransactionServiceTest {

    private static final Long BANK_TRANSACTION_ID = 101L;
    private static final Long LINKED_ACCOUNT_ID = 1L;
    private static final String EXTERNAL_TRANSACTION_ID = "external-tx-001";

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @InjectMocks
    private BankTransactionService bankTransactionService;

    @Nested
    @DisplayName("은행 거래 저장")
    class SaveIfNotExists {

        @Test
        @DisplayName("이미 저장된 외부 거래 키이면 기존 은행 거래 ID를 반환한다")
        void returnsExistingTransactionIdWhenExternalKeyAlreadyExists() {
            BankTransactionDTO existingTransaction =
                    transaction(BANK_TRANSACTION_ID);
            BankTransactionDTO newTransaction = transaction(null);

            given(bankTransactionMapper.findByExternalKey(
                    LINKED_ACCOUNT_ID,
                    EXTERNAL_TRANSACTION_ID
            )).willReturn(Optional.of(existingTransaction));

            Long result =
                    bankTransactionService.saveIfNotExists(newTransaction);

            assertThat(result).isEqualTo(BANK_TRANSACTION_ID);
            verify(bankTransactionMapper, never()).insert(newTransaction);
        }

        @Test
        @DisplayName("저장된 외부 거래 키가 없으면 새 거래를 저장하고 생성된 ID를 반환한다")
        void insertsAndReturnsNewTransactionIdWhenExternalKeyDoesNotExist() {
            BankTransactionDTO newTransaction = transaction(null);

            given(bankTransactionMapper.findByExternalKey(
                    LINKED_ACCOUNT_ID,
                    EXTERNAL_TRANSACTION_ID
            )).willReturn(Optional.empty());
            given(bankTransactionMapper.insert(newTransaction))
                    .willAnswer(invocation -> {
                        ReflectionTestUtils.setField(
                                newTransaction,
                                "bankTransactionId",
                                BANK_TRANSACTION_ID
                        );
                        return 1;
                    });

            Long result =
                    bankTransactionService.saveIfNotExists(newTransaction);

            assertThat(result).isEqualTo(BANK_TRANSACTION_ID);
            verify(bankTransactionMapper).insert(newTransaction);
        }

        @Test
        @DisplayName("거래 저장 후 생성된 ID가 없으면 생성 실패 예외가 발생한다")
        void throwsCreateFailedWhenGeneratedIdIsNullAfterInsert() {
            BankTransactionDTO newTransaction = transaction(null);

            given(bankTransactionMapper.findByExternalKey(
                    LINKED_ACCOUNT_ID,
                    EXTERNAL_TRANSACTION_ID
            )).willReturn(Optional.empty());
            given(bankTransactionMapper.insert(newTransaction)).willReturn(1);

            assertTransactionExceptionThrownBy(
                    () -> bankTransactionService.saveIfNotExists(
                            newTransaction
                    ),
                    BankTransactionErrorCode.BANK_TRANSACTION_CREATE_FAILED
            );
        }

        @Test
        @DisplayName("거래 저장 결과가 1건이 아니면 생성 실패 예외가 발생한다")
        void throwsCreateFailedWhenInsertCountIsNotOne() {
            BankTransactionDTO newTransaction = transaction(null);

            given(bankTransactionMapper.findByExternalKey(
                    LINKED_ACCOUNT_ID,
                    EXTERNAL_TRANSACTION_ID
            )).willReturn(Optional.empty());
            given(bankTransactionMapper.insert(newTransaction)).willReturn(0);

            assertTransactionExceptionThrownBy(
                    () -> bankTransactionService.saveIfNotExists(
                            newTransaction
                    ),
                    BankTransactionErrorCode.BANK_TRANSACTION_CREATE_FAILED
            );
        }

        @Test
        @DisplayName("저장 중 중복 키가 발생하면 기존 거래를 다시 조회해 ID를 반환한다")
        void returnsExistingIdWhenDuplicateKeyOccursDuringInsert() {
            BankTransactionDTO newTransaction = transaction(null);
            BankTransactionDTO existingTransaction =
                    transaction(BANK_TRANSACTION_ID);

            given(bankTransactionMapper.findByExternalKey(
                    LINKED_ACCOUNT_ID,
                    EXTERNAL_TRANSACTION_ID
            )).willReturn(Optional.empty(), Optional.of(existingTransaction));
            given(bankTransactionMapper.insert(newTransaction))
                    .willThrow(new DuplicateKeyException("duplicate"));

            Long result =
                    bankTransactionService.saveIfNotExists(newTransaction);

            assertThat(result).isEqualTo(BANK_TRANSACTION_ID);
        }

        @Test
        @DisplayName("중복 키 발생 후 기존 거래를 찾지 못하면 생성 실패 예외가 발생한다")
        void throwsCreateFailedWhenDuplicateKeyCannotBeResolved() {
            BankTransactionDTO newTransaction = transaction(null);

            given(bankTransactionMapper.findByExternalKey(
                    LINKED_ACCOUNT_ID,
                    EXTERNAL_TRANSACTION_ID
            )).willReturn(Optional.empty(), Optional.empty());
            given(bankTransactionMapper.insert(newTransaction))
                    .willThrow(new DuplicateKeyException("duplicate"));

            assertTransactionExceptionThrownBy(
                    () -> bankTransactionService.saveIfNotExists(
                            newTransaction
                    ),
                    BankTransactionErrorCode.BANK_TRANSACTION_CREATE_FAILED
            );
        }
    }

    @Nested
    @DisplayName("자동매칭 대상 거래 조회")
    class FindPendingDeposits {

        @Test
        @DisplayName("처리 대기 중인 입금 거래 목록을 반환한다")
        void returnsPendingDepositTransactions() {
            BankTransactionDTO transaction = transaction(BANK_TRANSACTION_ID);

            given(bankTransactionMapper.findPendingDeposits())
                    .willReturn(List.of(transaction));

            List<BankTransactionDTO> result =
                    bankTransactionService.findPendingDeposits();

            assertThat(result).containsExactly(transaction);
        }
    }

    @Nested
    @DisplayName("은행 거래 처리 상태 변경")
    class UpdateStatus {

        @Test
        @DisplayName("은행 거래 처리 상태를 변경한다")
        void updatesTransactionProcessingStatus() {
            given(bankTransactionMapper.updateStatus(
                    BANK_TRANSACTION_ID,
                    BankTransactionProcessingStatus.APPLIED
            )).willReturn(1);

            bankTransactionService.updateStatus(
                    BANK_TRANSACTION_ID,
                    BankTransactionProcessingStatus.APPLIED
            );

            verify(bankTransactionMapper).updateStatus(
                    BANK_TRANSACTION_ID,
                    BankTransactionProcessingStatus.APPLIED
            );
        }

        @Test
        @DisplayName("상태 변경 결과가 1건이 아니면 상태 변경 실패 예외가 발생한다")
        void throwsStatusUpdateFailedWhenUpdateCountIsNotOne() {
            given(bankTransactionMapper.updateStatus(
                    BANK_TRANSACTION_ID,
                    BankTransactionProcessingStatus.APPLIED
            )).willReturn(0);

            assertTransactionExceptionThrownBy(
                    () -> bankTransactionService.updateStatus(
                            BANK_TRANSACTION_ID,
                            BankTransactionProcessingStatus.APPLIED
                    ),
                    BankTransactionErrorCode
                            .BANK_TRANSACTION_STATUS_UPDATE_FAILED
            );
        }
    }

    private BankTransactionDTO transaction(Long bankTransactionId) {
        return BankTransactionDTO.builder()
                .bankTransactionId(bankTransactionId)
                .linkedAccountId(LINKED_ACCOUNT_ID)
                .externalTransactionId(EXTERNAL_TRANSACTION_ID)
                .amount(new BigDecimal("10000.00"))
                .transactionType(BankTransactionType.DEPOSIT)
                .transactionAt(LocalDateTime.of(2026, 8, 4, 10, 0))
                .counterpartyName("Hong Gil Dong")
                .memo("deposit")
                .syncedAt(LocalDateTime.of(2026, 8, 4, 10, 5))
                .build();
    }

    private void assertTransactionExceptionThrownBy(
            Runnable operation,
            BankTransactionErrorCode errorCode
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(errorCode)
                );
    }
}
