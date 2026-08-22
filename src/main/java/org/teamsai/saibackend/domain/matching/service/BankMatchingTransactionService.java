package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankMatchingTransactionService {

    private final PaymentObligationMapper paymentObligationMapper;
    private final AutoMatchingService autoMatchingService;
    private final BankTransactionService bankTransactionService;

    @Transactional
    public AutoMatchingTransactionResult process(
            Long userId,
            Long linkedAccountId,
            BankTransactionDTO bankTransaction
    ) {
        return process(userId, linkedAccountId, bankTransaction, null, null);
    }

    @Transactional
    public AutoMatchingTransactionResult process(
            Long userId,
            Long linkedAccountId,
            BankTransactionDTO bankTransaction,
            MatchingTargetType targetType,
            Long aggregateId
    ) {
        BankTransactionDTO lockedTransaction =
                bankTransactionService
                        .findByIdAndLinkedAccountIdForUpdate(
                                bankTransaction.getBankTransactionId(),
                                linkedAccountId
                        );

        if (lockedTransaction.getProcessingStatus()
                != BankTransactionProcessingStatus.PENDING) {
            return new AutoMatchingTransactionResult(
                    lockedTransaction.getBankTransactionId(),
                    AutoMatchingProcessStatus.DUPLICATE
            );
        }

        AutoMatchingTransactionResult result =
                processMatching(
                        linkedAccountId,
                        lockedTransaction,
                        targetType,
                        aggregateId
                );

        if (result == null) {
            return null;
        }

        bankTransactionService.updateStatus(
                lockedTransaction.getBankTransactionId(),
                BankTransactionProcessingStatus.PENDING,
                toBankTransactionProcessingStatus(result.processStatus())
        );

        return result;
    }

    private AutoMatchingTransactionResult processMatching(
            Long linkedAccountId,
            BankTransactionDTO bankTransaction,
            MatchingTargetType targetType,
            Long aggregateId
    ) {
        if (!hasMatchableCounterpartyName(bankTransaction)) {
            if (targetType != null) {
                return null;
            }
            return new AutoMatchingTransactionResult(
                    bankTransaction.getBankTransactionId(),
                    AutoMatchingProcessStatus.UNMATCHED
            );
        }

        MatchingTransaction matchingTransaction =
                toMatchingTransaction(bankTransaction);

        List<MatchingCandidate> candidates = findCandidates(
                linkedAccountId,
                matchingTransaction,
                targetType,
                aggregateId
        );

        if (targetType != null && candidates.isEmpty()) {
            return null;
        }

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

    private List<MatchingCandidate> findCandidates(
            Long linkedAccountId,
            MatchingTransaction transaction,
            MatchingTargetType targetType,
            Long aggregateId
    ) {
        if (targetType == null) {
            return paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                    linkedAccountId,
                    transaction.transactionAt()
            );
        }

        return paymentObligationMapper.findMatchCandidatesByLinkedAccountIdAndTarget(
                linkedAccountId,
                transaction.transactionAt(),
                targetType,
                aggregateId
        );
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
