package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("BankTransactionMapper 통합 테스트")
@ActiveProfiles("dev")
@Sql(scripts = "/db/bank_transaction.sql")
class BankTransactionMapperTest {

    private static final Long LINKED_ACCOUNT_ID = 1L;
    private static final Long OTHER_LINKED_ACCOUNT_ID = 2L;

    @Autowired
    private BankTransactionMapper bankTransactionMapper;

    @Test
    @Transactional
    @DisplayName("신규 은행 거래를 저장하면 DTO에 생성된 ID가 채워진다")
    void insertOrGetIdAssignsGeneratedKeyToNewTransaction() {
        BankTransactionDTO bankTransaction = transaction(
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 10, 0)
        );

        bankTransactionMapper.insertOrGetId(bankTransaction);

        assertThat(bankTransaction.getBankTransactionId()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("같은 외부 거래 키를 다시 저장하면 기존 은행 거래 ID가 채워진다")
    void insertOrGetIdAssignsExistingIdToDuplicatedTransaction() {
        String externalTransactionId = uniqueExternalTransactionId();
        BankTransactionDTO firstTransaction = transaction(
                externalTransactionId,
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 10, 0)
        );
        BankTransactionDTO duplicatedTransaction = transaction(
                externalTransactionId,
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 10, 1)
        );

        bankTransactionMapper.insertOrGetId(firstTransaction);
        bankTransactionMapper.insertOrGetId(duplicatedTransaction);

        assertThat(duplicatedTransaction.getBankTransactionId())
                .isEqualTo(firstTransaction.getBankTransactionId());
    }

    @Test
    @Transactional
    @DisplayName("신규 은행 거래는 PENDING 상태로 저장된다")
    void insertOrGetIdSavesNewTransactionAsPending() {
        BankTransactionDTO bankTransaction = transaction(
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 10, 0)
        );

        bankTransactionMapper.insertOrGetId(bankTransaction);

        BankTransactionDTO savedTransaction =
                bankTransactionMapper.findById(
                        bankTransaction.getBankTransactionId()
                ).orElseThrow();

        assertThat(savedTransaction.getProcessingStatus())
                .isEqualTo(BankTransactionProcessingStatus.PENDING);
    }

    @Test
    @Transactional
    @DisplayName("현재 상태가 일치할 때만 은행 거래 처리 상태를 변경한다")
    void updateStatusUpdatesOnlyWhenCurrentStatusMatches() {
        BankTransactionDTO bankTransaction = transaction(
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 10, 0)
        );

        bankTransactionMapper.insertOrGetId(bankTransaction);

        int updatedCount = bankTransactionMapper.updateStatus(
                bankTransaction.getBankTransactionId(),
                BankTransactionProcessingStatus.PENDING,
                BankTransactionProcessingStatus.APPLIED
        );
        int staleUpdatedCount = bankTransactionMapper.updateStatus(
                bankTransaction.getBankTransactionId(),
                BankTransactionProcessingStatus.PENDING,
                BankTransactionProcessingStatus.FAILED
        );

        assertThat(updatedCount).isEqualTo(1);
        assertThat(staleUpdatedCount).isZero();

        BankTransactionDTO savedTransaction =
                bankTransactionMapper.findById(
                        bankTransaction.getBankTransactionId()
                ).orElseThrow();

        assertThat(savedTransaction.getProcessingStatus())
                .isEqualTo(BankTransactionProcessingStatus.APPLIED);
    }

    @Test
    @Transactional
    @DisplayName("처리 대기 중인 입금 거래만 거래 시각과 ID 순서로 조회한다")
    void findPendingDepositsReturnsOnlyPendingDepositsInOrder() {
        BankTransactionDTO laterDeposit = transaction(
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 11, 0)
        );
        BankTransactionDTO earlierDeposit = transaction(
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 10, 0)
        );
        BankTransactionDTO withdrawal = transaction(
                uniqueExternalTransactionId(),
                BankTransactionType.WITHDRAWAL,
                LocalDateTime.of(2026, 8, 4, 9, 0)
        );
        BankTransactionDTO appliedDeposit = transaction(
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 8, 0)
        );

        bankTransactionMapper.insertOrGetId(laterDeposit);
        bankTransactionMapper.insertOrGetId(earlierDeposit);
        bankTransactionMapper.insertOrGetId(withdrawal);
        bankTransactionMapper.insertOrGetId(appliedDeposit);
        bankTransactionMapper.updateStatus(
                appliedDeposit.getBankTransactionId(),
                BankTransactionProcessingStatus.PENDING,
                BankTransactionProcessingStatus.APPLIED
        );

        List<BankTransactionDTO> result =
                bankTransactionMapper.findPendingDeposits();

        assertThat(result)
                .extracting(BankTransactionDTO::getBankTransactionId)
                .containsSubsequence(
                        earlierDeposit.getBankTransactionId(),
                        laterDeposit.getBankTransactionId()
                );
        assertThat(result)
                .extracting(BankTransactionDTO::getBankTransactionId)
                .doesNotContain(
                        withdrawal.getBankTransactionId(),
                        appliedDeposit.getBankTransactionId()
                );
    }

    @Test
    @Transactional
    @DisplayName("입금자명이 없거나 공백이어도 연결 계좌 기준 처리 대기 입금 거래로 조회한다")
    void findPendingDepositsByLinkedAccountIdIncludesNullOrBlankCounterpartyName() {
        BankTransactionDTO nullCounterpartyName = transaction(
                LINKED_ACCOUNT_ID,
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 10, 0),
                null
        );
        BankTransactionDTO blankCounterpartyName = transaction(
                LINKED_ACCOUNT_ID,
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 11, 0),
                " "
        );

        bankTransactionMapper.insertOrGetId(nullCounterpartyName);
        bankTransactionMapper.insertOrGetId(blankCounterpartyName);

        List<BankTransactionDTO> result =
                bankTransactionMapper.findPendingDepositsByLinkedAccountId(
                        LINKED_ACCOUNT_ID
                );

        assertThat(result)
                .extracting(BankTransactionDTO::getBankTransactionId)
                .contains(
                        nullCounterpartyName.getBankTransactionId(),
                        blankCounterpartyName.getBankTransactionId()
                );
    }

    @Test
    @Transactional
    @DisplayName("연결 계좌 기준 처리 대기 입금 거래만 거래 시각과 ID 순서로 조회한다")
    void findPendingDepositsByLinkedAccountIdReturnsOnlyMatchingAccountDeposits() {
        BankTransactionDTO laterDeposit = transaction(
                LINKED_ACCOUNT_ID,
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 11, 0)
        );
        BankTransactionDTO earlierDeposit = transaction(
                LINKED_ACCOUNT_ID,
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 10, 0)
        );
        BankTransactionDTO otherAccountDeposit = transaction(
                OTHER_LINKED_ACCOUNT_ID,
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 9, 0)
        );
        BankTransactionDTO withdrawal = transaction(
                LINKED_ACCOUNT_ID,
                uniqueExternalTransactionId(),
                BankTransactionType.WITHDRAWAL,
                LocalDateTime.of(2026, 8, 4, 8, 0)
        );
        BankTransactionDTO appliedDeposit = transaction(
                LINKED_ACCOUNT_ID,
                uniqueExternalTransactionId(),
                BankTransactionType.DEPOSIT,
                LocalDateTime.of(2026, 8, 4, 7, 0)
        );

        bankTransactionMapper.insertOrGetId(laterDeposit);
        bankTransactionMapper.insertOrGetId(earlierDeposit);
        bankTransactionMapper.insertOrGetId(otherAccountDeposit);
        bankTransactionMapper.insertOrGetId(withdrawal);
        bankTransactionMapper.insertOrGetId(appliedDeposit);
        bankTransactionMapper.updateStatus(
                appliedDeposit.getBankTransactionId(),
                BankTransactionProcessingStatus.PENDING,
                BankTransactionProcessingStatus.APPLIED
        );

        List<BankTransactionDTO> result =
                bankTransactionMapper.findPendingDepositsByLinkedAccountId(
                        LINKED_ACCOUNT_ID
                );

        assertThat(result)
                .extracting(BankTransactionDTO::getBankTransactionId)
                .containsSubsequence(
                        earlierDeposit.getBankTransactionId(),
                        laterDeposit.getBankTransactionId()
                );
        assertThat(result)
                .extracting(BankTransactionDTO::getBankTransactionId)
                .doesNotContain(
                        otherAccountDeposit.getBankTransactionId(),
                        withdrawal.getBankTransactionId(),
                        appliedDeposit.getBankTransactionId()
                );
    }

    private BankTransactionDTO transaction(
            String externalTransactionId,
            BankTransactionType transactionType,
            LocalDateTime transactionAt
    ) {
        return transaction(
                LINKED_ACCOUNT_ID,
                externalTransactionId,
                transactionType,
                transactionAt
        );
    }

    private BankTransactionDTO transaction(
            Long linkedAccountId,
            String externalTransactionId,
            BankTransactionType transactionType,
            LocalDateTime transactionAt
    ) {
        return transaction(
                linkedAccountId,
                externalTransactionId,
                transactionType,
                transactionAt,
                "Hong Gil Dong"
        );
    }

    private BankTransactionDTO transaction(
            Long linkedAccountId,
            String externalTransactionId,
            BankTransactionType transactionType,
            LocalDateTime transactionAt,
            String counterpartyName
    ) {
        return BankTransactionDTO.builder()
                .linkedAccountId(linkedAccountId)
                .externalTransactionId(externalTransactionId)
                .amount(new BigDecimal("10000.00"))
                .transactionType(transactionType)
                .transactionAt(transactionAt)
                .counterpartyName(counterpartyName)
                .memo("deposit")
                .syncedAt(LocalDateTime.of(2026, 8, 4, 12, 0))
                .build();
    }

    private String uniqueExternalTransactionId() {
        return "test-tx-" + UUID.randomUUID();
    }
}
