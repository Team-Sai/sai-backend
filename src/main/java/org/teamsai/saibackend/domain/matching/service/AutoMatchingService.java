package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
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
import org.teamsai.saibackend.domain.payment.service.PaymentService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnBean({
        MatchingTransactionReader.class,
        MatchingCandidateReader.class
})
@RequiredArgsConstructor
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
        Set<Long> appliedObligationIds = new HashSet<>();

        for (MatchingTransaction transaction : transactions) {
            List<MatchingCandidate> availableCandidates =
                    excludeAppliedCandidates(candidates, appliedObligationIds);

            AutoMatchingProcessResult processResult =
                    processTransaction(transaction, availableCandidates);

            switch (processResult.status()) {
                case APPLIED -> {
                    appliedCount++;
                    appliedObligationIds.add(
                            processResult.appliedObligationId()
                    );
                }
                case NEEDS_CHECK -> needsCheckCount++;
                case UNMATCHED -> unmatchedCount++;
            }
        }

        return new AutoMatchingExecutionResult(
                transactions.size(),
                appliedCount,
                needsCheckCount,
                unmatchedCount
        );
    }

    private List<MatchingCandidate> excludeAppliedCandidates(
            List<MatchingCandidate> candidates,
            Set<Long> appliedObligationIds
    ) {
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
    }

    private enum AutoMatchingProcessStatus {
        APPLIED,
        NEEDS_CHECK,
        UNMATCHED
    }
}
