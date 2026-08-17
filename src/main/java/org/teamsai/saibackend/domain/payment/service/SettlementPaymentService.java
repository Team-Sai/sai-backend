package org.teamsai.saibackend.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.type.*;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SettlementPaymentService {

    private final PaymentObligationMapper paymentObligationMapper;
    private final PaymentRecordService paymentRecordService;

    @Transactional(propagation = Propagation.NESTED)
    public void applyAutoMatchedPayment(
            Long paymentObligationId,
            Long bankTransactionId,
            BigDecimal amount
    ) {
        applyPayment(
                paymentObligationId,
                bankTransactionId,
                amount,
                SourceType.AUTO_MATCH,
                false
        );
    }

    @Transactional(propagation = Propagation.NESTED)
    public void applyManuallyMatchedPayment(
            Long paymentObligationId,
            Long bankTransactionId,
            BigDecimal amount
    ) {
        applyPayment(
                paymentObligationId,
                bankTransactionId,
                amount,
                SourceType.MANUAL,
                true
        );
    }

    private void applyPayment(
            Long paymentObligationId,
            Long bankTransactionId,
            BigDecimal amount,
            SourceType sourceType,
            boolean limitToRemainingAmount
    ) {
        validatePaymentAmount(amount);
        validateBankTransactionId(bankTransactionId);
        validateNotDuplicatePaymentRecord(bankTransactionId);

        PaymentObligationDTO obligation =
                paymentObligationMapper.findByIdForUpdate(paymentObligationId)
                        .orElseThrow(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_FOUND::toException);

        validateActiveObligation(obligation);

        BigDecimal paidAmount = paymentRecordService
                .sumConfirmedAmountByTarget(PaymentTargetType.SETTLEMENT,
                        paymentObligationId);

        BigDecimal remainingAmount =
                obligation.getExpectedAmount().subtract(paidAmount);

        if (!limitToRemainingAmount
                && amount.compareTo(remainingAmount) > 0) {
            throw PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING_AMOUNT.toException();
        }

        BigDecimal paymentAmount = limitToRemainingAmount
                ? amount.min(remainingAmount)
                : amount;

        validatePaymentAmount(paymentAmount);

        paymentRecordService.createConfirmedRecord(
                bankTransactionId,
                PaymentTargetType.SETTLEMENT,
                paymentObligationId,
                paymentAmount,
                sourceType
        );

        BigDecimal newPaidAmount = paidAmount.add(paymentAmount);
        PaymentStatus newPaymentStatus = calculatePaymentStatus(
                obligation.getExpectedAmount(),
                newPaidAmount
        );

        int updatedCount = paymentObligationMapper.updatePaymentStatus(
                paymentObligationId,
                newPaymentStatus
        );

        if (updatedCount != 1) {
            throw PaymentErrorCode.PAYMENT_STATUS_UPDATE_FAILED.toException();
        }
    }

    public Long createObligation(Long participantId, BigDecimal expectedAmount){
        validateObligationCreation(participantId,expectedAmount);

        PaymentObligationDTO paymentObligation =
                PaymentObligationDTO.builder()
                        .participantId(participantId)
                        .expectedAmount(expectedAmount)
                        .paymentStatus(PaymentStatus.UNPAID)
                        .reviewStatus(ReviewStatus.NORMAL)
                        .obligationStatus(ObligationStatus.ACTIVE)
                        .build();

        int insertCount = paymentObligationMapper.insert(paymentObligation);

        if(insertCount != 1){
            throw PaymentErrorCode.PAYMENT_OBLIGATION_CREATE_FAILED.toException();
        }
        return paymentObligation.getPaymentObligationId();
    }

    private void validateActiveObligation(PaymentObligationDTO obligation) {
        if (obligation.getObligationStatus() != ObligationStatus.ACTIVE
                || obligation.getPaymentStatus() == PaymentStatus.PAID) {
            throw PaymentErrorCode.PAYMENT_OBLIGATION_NOT_ACTIVE.toException();
        }
    }

    private void validatePaymentAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentErrorCode.INVALID_PAYMENT_AMOUNT.toException();
        }
    }

    private void validateBankTransactionId(
            Long bankTransactionId
    ) {
        if (bankTransactionId == null) {
            throw PaymentErrorCode.INVALID_BANK_TRANSACTION_ID.toException();
        }
    }

    private void validateNotDuplicatePaymentRecord(
            Long bankTransactionId
    ) {
        if (paymentRecordService.existsByBankTransactionId(
                bankTransactionId
        )) {
            throw PaymentErrorCode
                    .DUPLICATE_PAYMENT_RECORD
                    .toException();
        }
    }

    private PaymentStatus calculatePaymentStatus(
            BigDecimal expectedAmount,
            BigDecimal paidAmount
    ) {
        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            return PaymentStatus.UNPAID;
        }

        if (paidAmount.compareTo(expectedAmount) < 0) {
            return PaymentStatus.PARTIALLY_PAID;
        }

        return PaymentStatus.PAID;
    }

    private void validateObligationCreation(
            Long participantId,
            BigDecimal expectedAmount
    ) {
        if (participantId == null || participantId <= 0) {
            throw PaymentErrorCode
                    .INVALID_PAYMENT_OBLIGATION_REQUEST
                    .toException();
        }
        if (expectedAmount == null
                || expectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentErrorCode
                    .INVALID_PAYMENT_OBLIGATION_REQUEST
                    .toException();
        }

    }
}
