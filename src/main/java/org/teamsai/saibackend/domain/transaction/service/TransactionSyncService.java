package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionResponse;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSyncService {

    private final LinkedBankAccountMapper linkedBankAccountMapper;
    private final BankTransactionMapper bankTransactionMapper;
    private final MockBankClient mockBankClient;
    private final UserService userService;

    @Transactional
    public int syncTransactions(Long linkedAccountId) {
        LinkedBankAccountDTO linkedAccount = linkedBankAccountMapper.findById(linkedAccountId)
                .orElseThrow(AccountErrorCode.LINKED_ACCOUNT_NOT_FOUND::toException);

        String userKey = userService.getUserKeyByUserId(linkedAccount.getUserId());

        Long lastSyncedId = linkedBankAccountMapper.findLastSyncedTransactionIdById(linkedAccountId);
        long afterTransactionId = lastSyncedId == null ? 0L : lastSyncedId;

        List<BankTransactionResponse> transactions;
        try {
            transactions = mockBankClient.getTransactions(
                    linkedAccount.getAccountId(),
                    userKey,
                    afterTransactionId
            );
        } catch (RestClientException e) {
            log.warn("[TransactionSyncService] 거래내역 조회 실패 - linkedAccountId: {}", linkedAccountId, e);
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }

        LocalDateTime now = LocalDateTime.now();

        for (BankTransactionResponse tx : transactions) {
            BankTransactionDTO dto = BankTransactionDTO.builder()
                    .linkedAccountId(linkedAccountId)
                    .externalTransactionId(tx.transactionKey())
                    .amount(tx.amount())
                    .transactionType(BankTransactionType.valueOf(tx.transactionType()))
                    .transactionAt(tx.transactionAt())
                    .counterpartyName(tx.counterpartyName())
                    .memo(tx.memo())
                    .syncedAt(now)
                    .build();

            bankTransactionMapper.insertOrGetId(dto);
        }

        if (!transactions.isEmpty()) {
            Long latestTransactionId = transactions.get(transactions.size() - 1).transactionId();
            linkedBankAccountMapper.updateLastSyncedTransactionId(linkedAccountId, latestTransactionId);
        }

        return transactions.size();
    }
}