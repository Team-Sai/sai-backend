package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateDTO;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.NotificationType;
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
    private final BankTransactionMatchCandidateService candidateService;
    private final NotificationService notificationService;

    @Transactional
    public AutoMatchingTransactionResult process(
            Long userId,
            Long linkedAccountId,
            BankTransactionDTO bankTransaction
    ) {
        AutoMatchingTransactionResult result =
                processMatching(linkedAccountId, bankTransaction);

        createMatchingReviewNotificationIfRequired(
                userId,
                linkedAccountId,
                bankTransaction,
                result
        );

        bankTransactionService.updateStatus(
                bankTransaction.getBankTransactionId(),
                BankTransactionProcessingStatus.PENDING,
                toBankTransactionProcessingStatus(result.processStatus())
        );

        return result;
    }

    private void createMatchingReviewNotificationIfRequired(
            Long userId,
            Long linkedAccountId,
            BankTransactionDTO bankTransaction,
            AutoMatchingTransactionResult result
    ) {
        if (result.processStatus()
                != AutoMatchingProcessStatus.NEEDS_CHECK) {
            return;
        }

        List<BankTransactionMatchCandidateDTO> candidates =
                candidateService.findAllByBankTransactionId(
                        bankTransaction.getBankTransactionId()
                );

        boolean hasSettlement = candidates.stream()
                .anyMatch(candidate -> candidate.getTargetType()
                        == MatchingTargetType.SETTLEMENT);
        boolean hasLoan = candidates.stream()
                .anyMatch(candidate -> candidate.getTargetType()
                        == MatchingTargetType.LOAN);

        if (!hasSettlement || !hasLoan) {
            return;
        }

        notificationService.createIfAbsent(
                userId,
                NotificationType.BANK_TRANSACTION_MATCHING_REVIEW,
                "입금 거래 확인이 필요합니다.",
                createNotificationContent(bankTransaction),
                bankTransaction.getBankTransactionId(),
                linkedAccountId
        );
    }

    private String createNotificationContent(
            BankTransactionDTO bankTransaction
    ) {
        String counterpartyName = bankTransaction.getCounterpartyName();

        if (counterpartyName == null || counterpartyName.isBlank()) {
            counterpartyName = "입금자 미상";
        }

        return counterpartyName
                + "님의 "
                + bankTransaction.getAmount().toPlainString()
                + "원 입금에 정산과 차용증 후보가 모두 발견되었습니다.";
    }

    private AutoMatchingTransactionResult processMatching(
            Long linkedAccountId,
            BankTransactionDTO bankTransaction
    ) {
        if (!hasMatchableCounterpartyName(bankTransaction)) {
            return new AutoMatchingTransactionResult(
                    bankTransaction.getBankTransactionId(),
                    AutoMatchingProcessStatus.UNMATCHED
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
