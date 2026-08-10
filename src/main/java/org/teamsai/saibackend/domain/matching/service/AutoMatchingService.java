package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.policy.AutoMatchingJudge;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMatchingService {

    private final AutoMatchingJudge autoMatchingJudge;
    private final SettlementPaymentService settlementPaymentService;
    private final LoanPaymentService loanPaymentService;

    public AutoMatchingExecutionResult execute(
            List<MatchingTransaction> transactions,
            List<MatchingCandidate> candidates
    ) {
        validateExecuteInput(transactions, candidates);

        int appliedCount = 0;
        int needsCheckCount = 0;
        int unmatchedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;
        Set<AppliedCandidateKey> appliedCandidateKeys = new HashSet<>();
        List<AutoMatchingTransactionResult> transactionResults =
                new ArrayList<>();

        for (MatchingTransaction transaction : transactions) {
            List<MatchingCandidate> availableCandidates =
                    excludeAppliedCandidates(candidates, appliedCandidateKeys);

            AutoMatchingProcessResult processResult =
                    processTransactionSafely(transaction, availableCandidates);

            transactionResults.add(
                    new AutoMatchingTransactionResult(
                            transaction.transactionId(),
                            processResult.status()
                    )
            );

            switch (processResult.status()) {
                case APPLIED -> {
                    appliedCount++;
                    appliedCandidateKeys.add(
                            processResult.appliedCandidateKey()
                    );
                }
                case NEEDS_CHECK -> needsCheckCount++;
                case UNMATCHED -> unmatchedCount++;
                case DUPLICATE -> duplicateCount++;
                case FAILED -> failedCount++;
            }
        }

        return new AutoMatchingExecutionResult(
                transactions.size(),
                appliedCount,
                needsCheckCount,
                unmatchedCount,
                duplicateCount,
                failedCount,
                transactionResults
        );
    }

    private void validateExecuteInput(
            List<MatchingTransaction> transactions,
            List<MatchingCandidate> candidates
    ) {
        if (transactions == null
                || candidates == null
                || transactions.stream()
                        .anyMatch(transaction -> transaction == null)
                || candidates.stream()
                        .anyMatch(candidate -> candidate == null)) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }
    }

    private AutoMatchingProcessResult processTransactionSafely(
            MatchingTransaction transaction,
            List<MatchingCandidate> candidates
    ) {
        try {
            return processTransaction(transaction, candidates);
        } catch (DomainException exception) {
            return classifyPaymentException(transaction, exception);
        }
    }

    private AutoMatchingProcessResult classifyPaymentException(
            MatchingTransaction transaction,
            DomainException exception
    ) {
        if (exception.getErrorCode()
                == PaymentErrorCode.DUPLICATE_PAYMENT_RECORD) {
            log.warn(
                    "Auto matching transaction duplicated. " +
                            "transactionId={}, errorCode={}",
                    transaction.transactionId(),
                    exception.getErrorCode()
            );
            return AutoMatchingProcessResult.duplicate();
        }

        if (exception.getHttpStatus().is5xxServerError()) {
            log.error(
                    "Auto matching transaction failed. " +
                            "transactionId={}, errorCode={}",
                    transaction.transactionId(),
                    exception.getErrorCode(),
                    exception
            );
            return AutoMatchingProcessResult.failed();
        }

        log.warn(
                "Auto matching transaction needs check. " +
                        "transactionId={}, errorCode={}",
                transaction.transactionId(),
                exception.getErrorCode()
        );

        // 개별 납부 반영 실패가 전체 자동매칭 실행을 중단하지 않도록 한다.
        return AutoMatchingProcessResult.needsCheck();
    }

    private List<MatchingCandidate> excludeAppliedCandidates(
            List<MatchingCandidate> candidates,
            Set<AppliedCandidateKey> appliedCandidateKeys
    ) {
        // MVP에서는 같은 매칭 후보를 같은 실행 안에서 한 번만 자동 반영한다.
        return candidates.stream()
                .filter(candidate -> !appliedCandidateKeys.contains(
                        AppliedCandidateKey.from(candidate)
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

        // 1. SETTLEMENT(정산) 타입 처리
        if (candidate.targetType() == MatchingTargetType.SETTLEMENT) {
            settlementPaymentService.applyAutoMatchedPayment(
                    candidate.targetId(),
                    transaction.transactionId(),
                    transaction.amount()
            );
            return AutoMatchingProcessResult.applied(AppliedCandidateKey.from(candidate));
        }

        // 2. LOAN(차용증) 타입 처리
        if (candidate.targetType() == MatchingTargetType.LOAN) {
            loanPaymentService.applyAutoMatchedPayment(
                    candidate.targetId(), // 👈 repayment_schedule의 schedule_id가 들어옴
                    transaction.transactionId(),
                    transaction.amount()
            );
            return AutoMatchingProcessResult.applied(AppliedCandidateKey.from(candidate));
        }

        return AutoMatchingProcessResult.needsCheck();
    }

    private record AppliedCandidateKey(
            MatchingTargetType targetType,
            Long targetId
    ) {

        private static AppliedCandidateKey from(
                MatchingCandidate candidate
        ) {
            return new AppliedCandidateKey(
                    candidate.targetType(),
                    candidate.targetId()
            );
        }
    }

    private record AutoMatchingProcessResult(
            AutoMatchingProcessStatus status,
            AppliedCandidateKey appliedCandidateKey
    ) {

        private AutoMatchingProcessResult {
            if (status == AutoMatchingProcessStatus.APPLIED
                    && appliedCandidateKey == null) {
                throw new IllegalArgumentException(
                        "appliedCandidateKey is required when status is APPLIED"
                );
            }

            if (status != AutoMatchingProcessStatus.APPLIED
                    && appliedCandidateKey != null) {
                throw new IllegalArgumentException(
                        "appliedCandidateKey is only allowed when status is APPLIED"
                );
            }
        }

        private static AutoMatchingProcessResult applied(
                AppliedCandidateKey appliedCandidateKey
        ) {
            return new AutoMatchingProcessResult(
                    AutoMatchingProcessStatus.APPLIED,
                    appliedCandidateKey
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

        private static AutoMatchingProcessResult failed() {
            return new AutoMatchingProcessResult(
                    AutoMatchingProcessStatus.FAILED,
                    null
            );
        }
    }
}
