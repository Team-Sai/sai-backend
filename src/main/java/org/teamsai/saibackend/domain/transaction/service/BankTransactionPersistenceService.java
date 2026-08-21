package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionResponse;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankTransactionPersistenceService {

    private final BankTransactionMapper bankTransactionMapper;
    private final LinkedBankAccountMapper linkedBankAccountMapper;


    /**
     * 이번 동기화 요청에서 조회/처리한 거래 건수를 반환한다.
     *
     * insertOrGetId()는 이미 저장된 거래(external_transaction_id 중복)를 만나면
     * 새로 INSERT하지 않고 기존 row를 재사용하는 멱등 upsert
     * 반환값은 "이번 요청에서 처리 대상이었던 거래 건수"를 의미한다.
     */
    @Transactional
    public int saveAndAdvanceCursor(Long linkedAccountId, List<BankTransactionResponse> transactions) {
        if (transactions.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();

        for (BankTransactionResponse tx : transactions) {
            bankTransactionMapper.insertOrGetId(toDto(linkedAccountId, tx, now));
        }

        Long latestTransactionId = transactions.stream()
                .map(BankTransactionResponse::transactionId)
                .max(Long::compareTo)
                .orElseThrow();

        BankTransactionResponse latestTransaction = transactions.stream()
                .max(Comparator.comparing(BankTransactionResponse::transactionId))
                .orElseThrow();

        if (latestTransaction.balanceAfter() != null) {
            linkedBankAccountMapper.updateBalance(
                    linkedAccountId,
                    latestTransaction.balanceAfter()
            );
        }

        linkedBankAccountMapper.updateLastSyncedTransactionId(linkedAccountId, latestTransactionId);

        return transactions.size();
    }

    private BankTransactionDTO toDto(Long linkedAccountId, BankTransactionResponse tx, LocalDateTime syncedAt) {
        return BankTransactionDTO.builder()
                .linkedAccountId(linkedAccountId)
                .externalTransactionId(tx.transactionKey())
                .amount(tx.amount())
                .transactionType(toTransactionType(linkedAccountId, tx))
                .transactionAt(tx.transactionAt())
                .counterpartyName(tx.counterpartyName())
                .memo(tx.memo())
                .syncedAt(syncedAt)
                .build();
    }

    private BankTransactionType toTransactionType(Long linkedAccountId, BankTransactionResponse tx) {
        try {
            return switch (tx.transactionType()) {
                case "DEPOSIT" -> BankTransactionType.DEPOSIT;
                case "WITHDRAW", "WITHDRAWAL" -> BankTransactionType.WITHDRAWAL;
                default -> throw new IllegalArgumentException(
                        "Unsupported bank transaction type: " + tx.transactionType()
                );
            };
        } catch (IllegalArgumentException e) {
            log.error(
                    "[BankTransactionPersistenceService] 사이은행 응답의 거래유형이 올바르지 않음 - "
                            + "linkedAccountId: {}, externalTransactionId: {}, transactionType: {}",
                    linkedAccountId, tx.transactionKey(), tx.transactionType()
            );
            throw AccountErrorCode.INVALID_BANK_RESPONSE.toException();
        }
    }
}
