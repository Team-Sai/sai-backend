package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankMatchingService {

    private final BankTransactionService bankTransactionService;
    private final PaymentObligationMapper paymentObligationMapper;
    private final AutoMatchingService autoMatchingService;

    public AutoMatchingExecutionResult execute(Long linkedAccountId) {
        validateLinkedAccountId(linkedAccountId);

        List<BankTransactionDTO> bankTransactions =
                bankTransactionService.findPendingDepositsByLinkedAccountId(
                        linkedAccountId
                );

        if (bankTransactions.isEmpty()) {
            return emptyResult();
        }

        if (bankTransactions.stream()
                .noneMatch(this::hasMatchableCounterpartyName)) {
            AutoMatchingExecutionResult executionResult =
                    toExecutionResult(
                            toNeedsCheckResults(bankTransactions)
                    );
            updateBankTransactionStatuses(executionResult);

            return executionResult;
        }

        AutoMatchingExecutionResult executionResult =
                toExecutionResult(
                        processTransactions(
                                linkedAccountId,
                                bankTransactions
                        )
                );
        updateBankTransactionStatuses(executionResult);

        return executionResult;
    }

    private void validateLinkedAccountId(Long linkedAccountId) {
        if (linkedAccountId == null || linkedAccountId <= 0) {
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
            Long linkedAccountId,
            List<BankTransactionDTO> bankTransactions
    ) {
        return bankTransactions.stream()
                .map(bankTransaction -> processTransaction(
                        linkedAccountId,
                        bankTransaction
                ))
                .toList();
    }

    private AutoMatchingTransactionResult processTransaction(
            Long linkedAccountId,
            BankTransactionDTO bankTransaction
    ) {
        if (!hasMatchableCounterpartyName(bankTransaction)) {
            return new AutoMatchingTransactionResult(
                    bankTransaction.getBankTransactionId(),
                    AutoMatchingProcessStatus.NEEDS_CHECK
            );
        }

        MatchingTransaction matchingTransaction =
                toMatchingTransaction(bankTransaction);

        List<MatchingCandidate> candidates =
                paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                        linkedAccountId,
                        matchingTransaction.transactionAt()
                );

        AutoMatchingExecutionResult matchingResult =
                autoMatchingService.execute(
                        List.of(matchingTransaction),
                        candidates
                );

        if (matchingResult.transactionResults().size() != 1) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        return matchingResult.transactionResults().get(0);
    }

    private boolean hasMatchableCounterpartyName(
            BankTransactionDTO bankTransaction
    ) {
        String counterpartyName = bankTransaction.getCounterpartyName();

        return counterpartyName != null && !counterpartyName.isBlank();
    }

    private MatchingTransaction toMatchingTransaction(
            BankTransactionDTO bankTransaction
    ) {
        return new MatchingTransaction(
                bankTransaction.getBankTransactionId(),
                toMatchingTransactionType(bankTransaction.getTransactionType()),
                bankTransaction.getAmount(),
                bankTransaction.getCounterpartyName(),
                bankTransaction.getTransactionAt()
        );
    }

    private AutoMatchingTransactionType toMatchingTransactionType(
            BankTransactionType transactionType
    ) {
        return switch (transactionType) {
            case DEPOSIT -> AutoMatchingTransactionType.DEPOSIT;
            case WITHDRAWAL -> AutoMatchingTransactionType.WITHDRAWAL;
        };
    }

    private List<AutoMatchingTransactionResult> toNeedsCheckResults(
            List<BankTransactionDTO> bankTransactions
    ) {
        return bankTransactions.stream()
                .map(transaction ->
                        new AutoMatchingTransactionResult(
                                transaction.getBankTransactionId(),
                                AutoMatchingProcessStatus.NEEDS_CHECK
                        )
                )
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

    private void updateBankTransactionStatuses(
            AutoMatchingExecutionResult executionResult
    ) {
        for (AutoMatchingTransactionResult transactionResult
                : executionResult.transactionResults()) {
            bankTransactionService.updateStatus(
                    transactionResult.transactionId(),
                    BankTransactionProcessingStatus.PENDING,
                    toBankTransactionProcessingStatus(
                            transactionResult.processStatus()
                    )
            );
        }
    }

    private BankTransactionProcessingStatus toBankTransactionProcessingStatus(
            AutoMatchingProcessStatus processStatus
    ) {
        return switch (processStatus) {
            case APPLIED, DUPLICATE ->
                    BankTransactionProcessingStatus.APPLIED;
            case NEEDS_CHECK ->
                    BankTransactionProcessingStatus.NEEDS_CHECK;
            case UNMATCHED ->
                    BankTransactionProcessingStatus.UNMATCHED;
            case FAILED ->
                    BankTransactionProcessingStatus.FAILED;
        };
    }
}
