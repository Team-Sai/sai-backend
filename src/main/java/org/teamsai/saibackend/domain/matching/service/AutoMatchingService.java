package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.policy.AutoMatchingJudge;
import org.teamsai.saibackend.domain.matching.reader.MatchingCandidateReader;
import org.teamsai.saibackend.domain.matching.reader.MatchingTransactionReader;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.service.PaymentService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnBean({
        MatchingTransactionReader.class,
        MatchingCandidateReader.class
})
@RequiredArgsConstructor
@Slf4j
public class AutoMatchingService {

    private final MatchingTransactionReader matchingTransactionReader;
    private final MatchingCandidateReader matchingCandidateReader;
    private final AutoMatchingJudge autoMatchingJudge;
    private final PaymentService paymentService;

    public AutoMatchingExecutionResult execute() {
        List<MatchingTransaction> transactions =
                matchingTransactionReader.readPendingTransactions();
        List<MatchingCandidate> candidates =
                matchingCandidateReader.readCandidates();

        int appliedCount = 0;
        int needsCheckCount = 0;
        int unmatchedCount = 0;
        int duplicateCount = 0;
        Set<Long> appliedObligationIds = new HashSet<>();

        for (MatchingTransaction transaction : transactions) {
            List<MatchingCandidate> availableCandidates =
                    excludeAppliedCandidates(candidates, appliedObligationIds);

            AutoMatchingProcessResult processResult =
                    processTransactionSafely(transaction, availableCandidates);

            switch (processResult.status()) {
                case APPLIED -> {
                    appliedCount++;
                    appliedObligationIds.add(
                            processResult.appliedObligationId()
                    );
                }
                case NEEDS_CHECK -> needsCheckCount++;
                case UNMATCHED -> unmatchedCount++;
                case DUPLICATE -> duplicateCount++;
            }
        }

        return new AutoMatchingExecutionResult(
                transactions.size(),
                appliedCount,
                needsCheckCount,
                unmatchedCount,
                duplicateCount
        );
    }

    private AutoMatchingProcessResult processTransactionSafely(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        try {
            return processTransaction(transaction, candidates);
        } catch (DomainException exception) {
            log.warn(
                    "Auto matching transaction failed. " +
                            "transactionId={}, errorCode={}",
                    transaction.transactionId(),
                    exception.getErrorCode()
            );

            if (exception.getErrorCode()
                    == PaymentErrorCode.DUPLICATE_PAYMENT_RECORD) {
                return AutoMatchingProcessResult.duplicate();
            }

            // 개별 납부 반영 실패가 전체 자동매칭 실행을 중단하지 않도록 한다.
            return AutoMatchingProcessResult.needsCheck();
        }
    }

    private List<MatchingCandidate> excludeAppliedCandidates(
            List<MatchingCandidate> candidates,
            Set<Long> appliedObligationIds
    ) {
        // MVP에서는 한 정산 납부의무를 같은 실행 안에서 한 번만 자동 반영한다.
        return candidates.stream()
                .filter(candidate -> !appliedObligationIds.contains(
                        candidate.obligationId()
                ))
                .toList();
    }

    private AutoMatchingProcessResult processTransaction(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        AutoMatchingResult result =
                autoMatchingJudge.judge(transaction, candidates);

        if (result.isUnmatched()) {
            return AutoMatchingProcessResult.unmatched();
        }

        if (result.needsCheck()) {
            return AutoMatchingProcessResult.needsCheck();
        }

        MatchingCandidate candidate = result.matchedCandidate();

        if (candidate.targetType() != MatchingTargetType.SETTLEMENT) {
            return AutoMatchingProcessResult.needsCheck();
        }

        paymentService.applyAutoMatchedPayment(
                candidate.obligationId(),
                transaction.transactionId(),
                transaction.amount()
        );

        return AutoMatchingProcessResult.applied(candidate.obligationId());
    }

    private record AutoMatchingProcessResult(
            AutoMatchingProcessStatus status,
            Long appliedObligationId
    ) {

        private AutoMatchingProcessResult {
            if (status == AutoMatchingProcessStatus.APPLIED
                    && appliedObligationId == null) {
                throw new IllegalArgumentException(
                        "appliedObligationId is required when status is APPLIED"
                );
            }

            if (status != AutoMatchingProcessStatus.APPLIED
                    && appliedObligationId != null) {
                throw new IllegalArgumentException(
                        "appliedObligationId is only allowed when status is APPLIED"
                );
            }
        }

        private static AutoMatchingProcessResult applied(
                Long obligationId
        ) {
            return new AutoMatchingProcessResult(
                    AutoMatchingProcessStatus.APPLIED,
                    obligationId
            );
        }

        private static AutoMatchingProcessResult needsCheck() {
            return new AutoMatchingProcessResult(
                    AutoMatchingProcessStatus.NEEDS_CHECK,
                    null
            );
        }

        private static AutoMatchingProcessResult unmatched() {
            return new AutoMatchingProcessResult(
                    AutoMatchingProcessStatus.UNMATCHED,
                    null
            );
        }

        private static AutoMatchingProcessResult duplicate() {
            return new AutoMatchingProcessResult(
                    AutoMatchingProcessStatus.DUPLICATE,
                    null
            );
        }
    }

    private enum AutoMatchingProcessStatus {
        APPLIED,
        NEEDS_CHECK,
        UNMATCHED,
        DUPLICATE
    }
}
