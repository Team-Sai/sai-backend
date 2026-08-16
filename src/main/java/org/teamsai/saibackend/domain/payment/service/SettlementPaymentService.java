package org.teamsai.saibackend.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    @Transactional
    public void applyAutoMatchedPayment(
            Long paymentObligationId,
            Long bankTransactionId,
            BigDecimal amount
    ) {
        applyPayment(
                paymentObligationId,
                bankTransactionId,
                amount,
                SourceType.AUTO_MATCH
        );
    }

    private void applyPayment(
            Long paymentObligationId,
            Long bankTransactionId,
            BigDecimal amount,
            SourceType sourceType
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

        if (amount.compareTo(remainingAmount) > 0) {
            throw PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING_AMOUNT.toException();
        }

        paymentRecordService.createConfirmedRecord(
                bankTransactionId,
                PaymentTargetType.SETTLEMENT,
                paymentObligationId,
                amount,
                sourceType
        );

        BigDecimal newPaidAmount = paidAmount.add(amount);
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

        if (newPaymentStatus == PaymentStatus.PAID) {
            paymentObligationMapper.clearOverdueSince(paymentObligationId);
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
        if (obligation.getObligationStatus() != ObligationStatus.ACTIVE) {
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
