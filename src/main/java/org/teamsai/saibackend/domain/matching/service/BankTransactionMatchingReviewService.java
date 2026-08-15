package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contractrepaymentschedule.exception.RepaymentScheduleErrorCode;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateDTO;
import org.teamsai.saibackend.domain.matching.dto.response.MatchingReviewProcessResponse;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.service.LoanPaymentService;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionDetailResponse;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionQueryService;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

@Service
@RequiredArgsConstructor
public class BankTransactionMatchingReviewService {

    private final BankTransactionQueryService bankTransactionQueryService;
    private final BankTransactionMatchCandidateService candidateService;
    private final SettlementPaymentService settlementPaymentService;
    private final LoanPaymentService loanPaymentService;
    private final BankTransactionService bankTransactionService;

    @Transactional
    public MatchingReviewProcessResponse applyCandidate(
            Long userId,
            Long linkedAccountId,
            Long bankTransactionId,
            Long matchCandidateId
    ) {
        BankTransactionDetailResponse transaction =
                getReviewableTransaction(
                        userId,
                        linkedAccountId,
                        bankTransactionId
                );

        BankTransactionMatchCandidateDTO candidate =
                candidateService.findByIdAndBankTransactionId(
                        matchCandidateId,
                        bankTransactionId
                );

        try {
            applyPayment(transaction, candidate);
        } catch (DomainException exception) {
            if (exception.getErrorCode()
                    == PaymentErrorCode.DUPLICATE_PAYMENT_RECORD) {
                return updateStatus(
                        bankTransactionId,
                        BankTransactionProcessingStatus.APPLIED
                );
            }

            if (isIrrecoverableTargetError(exception)) {
                return updateStatus(
                        bankTransactionId,
                        BankTransactionProcessingStatus.FAILED
                );
            }

            throw exception;
        }

        return updateStatus(
                bankTransactionId,
                BankTransactionProcessingStatus.APPLIED
        );
    }

    @Transactional
    public MatchingReviewProcessResponse rejectCandidates(
            Long userId,
            Long linkedAccountId,
            Long bankTransactionId
    ) {
        getReviewableTransaction(
                userId,
                linkedAccountId,
                bankTransactionId
        );

        return updateStatus(
                bankTransactionId,
                BankTransactionProcessingStatus.UNMATCHED
        );
    }

    private BankTransactionDetailResponse getReviewableTransaction(
            Long userId,
            Long linkedAccountId,
            Long bankTransactionId
    ) {
        BankTransactionDetailResponse transaction =
                bankTransactionQueryService.getTransactionDetailForUpdate(
                        userId,
                        linkedAccountId,
                        bankTransactionId
                );

        if (transaction.processingStatus()
                != BankTransactionProcessingStatus.NEEDS_CHECK
                || transaction.transactionType()
                != BankTransactionType.DEPOSIT) {
            throw MatchingErrorCode
                    .MATCHING_REVIEW_NOT_REQUIRED
                    .toException();
        }

        return transaction;
    }

    private void applyPayment(
            BankTransactionDetailResponse transaction,
            BankTransactionMatchCandidateDTO candidate
    ) {
        if (candidate.getTargetType() == MatchingTargetType.SETTLEMENT) {
            settlementPaymentService.applyManuallyMatchedPayment(
                    candidate.getTargetId(),
                    transaction.bankTransactionId(),
                    transaction.amount()
            );
            return;
        }

        if (candidate.getTargetType() == MatchingTargetType.LOAN) {
            loanPaymentService.applyManuallyMatchedPayment(
                    candidate.getTargetId(),
                    transaction.bankTransactionId(),
                    transaction.amount()
            );
            return;
        }

        throw MatchingErrorCode.MATCHING_TARGET_NOT_FOUND.toException();
    }

    private boolean isIrrecoverableTargetError(
            DomainException exception
    ) {
        return exception.getErrorCode()
                == PaymentErrorCode.PAYMENT_OBLIGATION_NOT_FOUND
                || exception.getErrorCode()
                == PaymentErrorCode.PAYMENT_OBLIGATION_NOT_ACTIVE
                || exception.getErrorCode()
                == RepaymentScheduleErrorCode.SCHEDULE_NOT_FOUND
                || exception.getErrorCode()
                == RepaymentScheduleErrorCode.SCHEDULE_NOT_PENDING;
    }

    private MatchingReviewProcessResponse updateStatus(
            Long bankTransactionId,
            BankTransactionProcessingStatus nextStatus
    ) {
        bankTransactionService.updateStatus(
                bankTransactionId,
                BankTransactionProcessingStatus.NEEDS_CHECK,
                nextStatus
        );

        return new MatchingReviewProcessResponse(
                bankTransactionId,
                nextStatus
        );
    }
}
