package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankMatchingService {

    private final BankTransactionService bankTransactionService;
    private final BankMatchingTransactionService transactionService;

    public AutoMatchingExecutionResult execute(
            Long userId,
            Long linkedAccountId,
            boolean isBatch
    ) {
        return execute(userId, linkedAccountId, null, null, isBatch);
    }

    public AutoMatchingExecutionResult execute(
            Long userId,
            Long linkedAccountId,
            MatchingTargetType targetType,
            Long aggregateId,
            boolean isBatch
    ) {
        validateLinkedAccountId(linkedAccountId);
        validateMatchingScope(targetType, aggregateId);

        List<BankTransactionDTO> bankTransactions =
                bankTransactionService.findPendingDepositsByLinkedAccountId(
                        linkedAccountId
                );

        if (bankTransactions.isEmpty()) {
            return emptyResult();
        }

        return toExecutionResult(
                processTransactions(
                        userId,
                        linkedAccountId,
                        bankTransactions,
                        targetType,
                        aggregateId,
                        isBatch
                )
        );
    }

    private void validateLinkedAccountId(Long linkedAccountId) {
        if (linkedAccountId == null || linkedAccountId <= 0) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }

    private void validateMatchingScope(
            MatchingTargetType targetType,
            Long aggregateId
    ) {
        if ((targetType == null) != (aggregateId == null)
                || aggregateId != null && aggregateId <= 0) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }

    private AutoMatchingExecutionResult emptyResult() {
        return new AutoMatchingExecutionResult(
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    private List<AutoMatchingTransactionResult> processTransactions(
            Long userId,
            Long linkedAccountId,
            List<BankTransactionDTO> bankTransactions,
            MatchingTargetType targetType,
            Long aggregateId,
            boolean isBatch
    ) {
        return bankTransactions.stream()
                .map(bankTransaction -> targetType == null
                        ? transactionService.process(
                                userId,
                                linkedAccountId,
                                bankTransaction,
                                isBatch
                        )
                        : transactionService.process(
                                userId,
                                linkedAccountId,
                                bankTransaction,
                                targetType,
                                aggregateId,
                                isBatch
                        ))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private AutoMatchingExecutionResult toExecutionResult(
            List<AutoMatchingTransactionResult> transactionResults
    ) {
        int appliedCount = 0;
        int needsCheckCount = 0;
        int unmatchedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        for (AutoMatchingTransactionResult result : transactionResults) {
            switch (result.processStatus()) {
                case APPLIED -> appliedCount++;
                case NEEDS_CHECK -> needsCheckCount++;
                case UNMATCHED -> unmatchedCount++;
                case DUPLICATE -> duplicateCount++;
                case FAILED -> failedCount++;
            }
        }

        return new AutoMatchingExecutionResult(
                transactionResults.size(),
                appliedCount,
                needsCheckCount,
                unmatchedCount,
                duplicateCount,
                failedCount,
                transactionResults
        );
    }
}
