package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.service.BankMatchingService;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.transaction.dto.response.TransactionSyncAllResponse;
import org.teamsai.saibackend.domain.transaction.dto.response.AccountSyncFailureResponse;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSyncFacade {

    private final TransactionSyncService transactionSyncService;
    private final BankMatchingService bankMatchingService;
    private final LinkedBankAccountService linkedBankAccountService;

    public AutoMatchingExecutionResult syncAndMatch(Long userId, Long linkedAccountId) {
        transactionSyncService.syncTransactions(userId, linkedAccountId);
        return bankMatchingService.execute(userId, linkedAccountId);
    }

    public AutoMatchingExecutionResult syncAndMatch(
            Long userId,
            Long linkedAccountId,
            MatchingTargetType targetType,
            Long aggregateId
    ) {
        transactionSyncService.syncTransactions(userId, linkedAccountId);
        return bankMatchingService.execute(
                userId,
                linkedAccountId,
                targetType,
                aggregateId
        );
    }

    public TransactionSyncAllResponse syncAll(Long userId){
        List<LinkedBankAccountResponse> accounts =
                linkedBankAccountService.getLinkedAccounts(userId);

        int syncedAccountCount = 0;
        int totalTransactionCount = 0;
        int appliedCount = 0;
        int needsCheckCount = 0;
        int unmatchedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;
        List<AccountSyncFailureResponse> failedAccounts = new ArrayList<>();

        for(LinkedBankAccountResponse account : accounts){
            try {
                AutoMatchingExecutionResult result =
                        syncAndMatch(userId, account.linkedAccountId());

                syncedAccountCount++;
                totalTransactionCount += result.totalTransactionCount();
                appliedCount += result.appliedCount();
                needsCheckCount += result.needsCheckCount();
                unmatchedCount += result.unmatchedCount();
                duplicateCount += result.duplicateCount();
                failedCount += result.failedCount();
            } catch (DomainException e) {
                String errorCode = e.getErrorCode().toString();
                failedAccounts.add(new AccountSyncFailureResponse(
                        account.linkedAccountId(),
                        errorCode
                ));
                log.warn(
                        "Account sync failed - linkedAccountId: {}, errorCode: {}",
                        account.linkedAccountId(),
                        errorCode,
                        e
                );
            }
        }

        return new TransactionSyncAllResponse(
                syncedAccountCount,
                List.copyOf(failedAccounts),
                totalTransactionCount,
                appliedCount,
                needsCheckCount,
                unmatchedCount,
                duplicateCount,
                failedCount
        );
    }
}
