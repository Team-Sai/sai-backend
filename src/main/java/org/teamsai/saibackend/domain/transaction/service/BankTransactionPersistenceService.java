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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankTransactionPersistenceService {

    private final BankTransactionMapper bankTransactionMapper;
    private final LinkedBankAccountMapper linkedBankAccountMapper;

    @Transactional
    public int saveAndAdvanceCursor(Long linkedAccountId, List<BankTransactionResponse> transactions) {
        if (transactions.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();

        for (BankTransactionResponse tx : transactions) {
            bankTransactionMapper.insertOrGetId(toDto(linkedAccountId, tx, now));
        }

        // 정렬 순서(응답 리스트 순서)에 의존하지 않고, 실제 최댓값을 기준으로 커서를 갱신한다.
        Long latestTransactionId = transactions.stream()
                .map(BankTransactionResponse::transactionId)
                .max(Long::compareTo)
                .orElseThrow();

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
            return BankTransactionType.valueOf(tx.transactionType());
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