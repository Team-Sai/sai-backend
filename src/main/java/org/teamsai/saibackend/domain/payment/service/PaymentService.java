package org.teamsai.saibackend.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.dto.PaymentRecordDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.mapper.PaymentRecordMapper;
import org.teamsai.saibackend.domain.payment.type.ObligationStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;
import org.teamsai.saibackend.domain.payment.type.RecordStatus;
import org.teamsai.saibackend.domain.payment.type.SourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentObligationMapper paymentObligationMapper;
    private final PaymentRecordMapper paymentRecordMapper;

    @Transactional
    public void applyPayment(
            Long paymentObligationId,
            Long settlementBankTransactionId,
            BigDecimal amount,
            SourceType sourceType
    ) {
        validatePaymentAmount(amount);
        validateSourceType(sourceType);
        validateSettlementBankTransactionId(settlementBankTransactionId);

        PaymentObligationDTO obligation =
                paymentObligationMapper.findByIdForUpdate(paymentObligationId)
                        .orElseThrow(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_FOUND::toException);

        validateActiveObligation(obligation);
        validateNotDuplicatePaymentRecord(settlementBankTransactionId);

        BigDecimal paidAmount = paymentRecordMapper
                .sumConfirmedAmountByObligationId(paymentObligationId);

        BigDecimal remainingAmount =
                obligation.getExpectedAmount().subtract(paidAmount);

        if (amount.compareTo(remainingAmount) > 0) {
            throw PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING_AMOUNT.toException();
        }

        PaymentRecordDTO paymentRecord = PaymentRecordDTO.builder()
                .settlementBankTransactionId(settlementBankTransactionId)
                .obligationId(paymentObligationId)
                .amount(amount)
                .sourceType(sourceType)
                .recordStatus(RecordStatus.CONFIRMED)
                .recordedAt(LocalDateTime.now())
                .build();

        try {
            int insertedCount = paymentRecordMapper.insert(paymentRecord);
            if (insertedCount != 1) {
                throw PaymentErrorCode.PAYMENT_RECORD_CREATE_FAILED.toException();
            }
        } catch (DuplicateKeyException exception) {
            throw PaymentErrorCode.DUPLICATE_PAYMENT_RECORD.toException();
        }

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

    private void validateSourceType(SourceType sourceType) {
        if (sourceType == null) {
            throw PaymentErrorCode.INVALID_PAYMENT_SOURCE_TYPE.toException();
        }
    }

    private void validateSettlementBankTransactionId(
            Long settlementBankTransactionId
    ) {
        if (settlementBankTransactionId == null) {
            throw PaymentErrorCode.INVALID_SETTLEMENT_BANK_TRANSACTION_ID.toException();
        }
    }

    private void validateNotDuplicatePaymentRecord(
            Long settlementBankTransactionId
    ) {
        if (paymentRecordMapper.existsBySettlementBankTransactionId(
                settlementBankTransactionId
        )) {
            throw PaymentErrorCode.DUPLICATE_PAYMENT_RECORD.toException();
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
}
