package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.service.BankMatchingService;
import org.teamsai.saibackend.domain.transaction.dto.response.TransactionSyncAllResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionSyncFacade {

    private final TransactionSyncService transactionSyncService;
    private final BankMatchingService bankMatchingService;
    private final LinkedBankAccountService linkedBankAccountService;

    public AutoMatchingExecutionResult syncAndMatch(Long userId, Long linkedAccountId) {
        transactionSyncService.syncTransactions(userId, linkedAccountId);
        return bankMatchingService.execute(linkedAccountId);
    }

    public TransactionSyncAllResponse syncAll(Long userId){
        List<LinkedBankAccountResponse> accounts =
                linkedBankAccountService.getLinkedAccounts(userId);

        int totalTransactionCount = 0;
        int appliedCount = 0;
        int needsCheckCount = 0;
        int unmatchedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        for(LinkedBankAccountResponse account : accounts){
            AutoMatchingExecutionResult result =
                    syncAndMatch(userId, account.linkedAccountId());

            totalTransactionCount += result.totalTransactionCount();
            appliedCount += result.appliedCount();
            needsCheckCount += result.needsCheckCount();
            unmatchedCount += result.unmatchedCount();
            duplicateCount += result.duplicateCount();
            failedCount += result.failedCount();
        }

        return new TransactionSyncAllResponse(
                accounts.size(),
                totalTransactionCount,
                appliedCount,
                needsCheckCount,
                unmatchedCount,
                duplicateCount,
                failedCount
        );
    }
}
