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
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentStatusService;
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
    private final SettlementPaymentStatusService settlementPaymentStatusService;

    @Transactional
    public AutoMatchingTransactionResult process(
            Long userId,
            Long linkedAccountId,
            BankTransactionDTO bankTransaction,
            boolean isBatch
    ) {
        return process(userId, linkedAccountId, bankTransaction, null, null, isBatch);
    }

    @Transactional
    public AutoMatchingTransactionResult process(
            Long userId,
            Long linkedAccountId,
            BankTransactionDTO bankTransaction,
            MatchingTargetType targetType,
            Long aggregateId,
            boolean isBatch
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
        createMatchingReviewNotificationIfRequired(
                userId,
                linkedAccountId,
                lockedTransaction,
                result,
                isBatch
        );
        bankTransactionService.updateStatus(
                lockedTransaction.getBankTransactionId(),
                BankTransactionProcessingStatus.PENDING,
                toBankTransactionProcessingStatus(result.processStatus())
        );
        return result;
    }

    private void createMatchingReviewNotificationIfRequired(
            Long userId,
            Long linkedAccountId,
            BankTransactionDTO bankTransaction,
            AutoMatchingTransactionResult result,
            boolean isBatch
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
        // 정산+차용증 후보가 동시에 발견된 경우가 최우선
        if (hasSettlement && hasLoan) {
            notificationService.createIfAbsent(
                    userId,
                    NotificationType.BANK_TRANSACTION_MATCHING_REVIEW,
                    "입금 거래 확인이 필요합니다.",
                    createBothFoundContent(bankTransaction),
                    bankTransaction.getBankTransactionId(),
                    linkedAccountId
            );
            return;
        }
        // 그 외에는 배치 실행일 때만, 정산 후보가 완납되지 않았으면 알림
        if (isBatch) {
            List<Long> settlementObligationIds = candidates.stream()
                    .filter(c -> c.getTargetType() == MatchingTargetType.SETTLEMENT)
                    .map(BankTransactionMatchCandidateDTO::getTargetId)
                    .toList();

            if (!settlementObligationIds.isEmpty()) {
                List<Long> settlementIds =
                        paymentObligationMapper.findSettlementIdsByObligationIds(settlementObligationIds);

                boolean hasUnresolvedSettlement = settlementIds.stream()
                        .anyMatch(settlementId ->
                                !settlementPaymentStatusService.areAllObligationsResolved(settlementId));

                if (hasUnresolvedSettlement) {
                    notificationService.createIfAbsent(
                            userId, NotificationType.BANK_TRANSACTION_MATCHING_REVIEW,
                            "정산이 완납되지 않았습니다.",
                            createUnresolvedSettlementContent(bankTransaction),
                            bankTransaction.getBankTransactionId(), linkedAccountId
                    );
                }
            }
        }
    }

    private String createBothFoundContent(
            BankTransactionDTO bankTransaction
    ) {
        String counterpartyName = resolveCounterpartyName(bankTransaction);
        return counterpartyName
                + "님의 "
                + bankTransaction.getAmount().toPlainString()
                + "원 입금에 정산과 차용증 후보가 모두 발견되었습니다.";
    }

    private String createUnresolvedSettlementContent(
            BankTransactionDTO bankTransaction
    ) {
        String counterpartyName = resolveCounterpartyName(bankTransaction);
        return counterpartyName
                + "님의 "
                + bankTransaction.getAmount().toPlainString()
                + "원 입금이 정산 금액과 일치하지 않아 확인이 필요합니다.";
    }

    private String resolveCounterpartyName(
            BankTransactionDTO bankTransaction
    ) {
        String counterpartyName = bankTransaction.getCounterpartyName();
        if (counterpartyName == null || counterpartyName.isBlank()) {
            return "입금자 미상";
        }
        return counterpartyName;
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