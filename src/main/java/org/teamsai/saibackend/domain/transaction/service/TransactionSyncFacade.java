package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.service.BankMatchingService;

@Service
@RequiredArgsConstructor
public class TransactionSyncFacade {

    private final TransactionSyncService transactionSyncService;
    private final BankMatchingService bankMatchingService;

    public AutoMatchingExecutionResult syncAndMatch(Long linkedAccountId) {
        transactionSyncService.syncTransactions(linkedAccountId);
        return bankMatchingService.execute(linkedAccountId);
    }
}