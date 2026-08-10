package org.teamsai.saibackend.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.dto.PaymentRecordDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentRecordMapper;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.payment.type.RecordStatus;
import org.teamsai.saibackend.domain.payment.type.SourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentRecordService {

    private final PaymentRecordMapper paymentRecordMapper;

    @Transactional(readOnly = true)
    public BigDecimal sumConfirmedAmountByTarget(
            PaymentTargetType paymentTargetType,
            Long targetId
    ) {
        validateTarget(paymentTargetType, targetId);

        return paymentRecordMapper.sumConfirmedAmountByTarget(
                paymentTargetType,
                targetId
        );
    }

    @Transactional
    public Long createConfirmedRecord(
            Long bankTransactionId,
            PaymentTargetType paymentTargetType,
            Long targetId,
            BigDecimal amount,
            SourceType sourceType
    ){
        validateBankTransactionId(bankTransactionId);
        validateTarget(paymentTargetType, targetId);
        validateAmount(amount);
        validateSourceType(sourceType);

        validateNotDuplicateBankTransaction(bankTransactionId);

        PaymentRecordDTO paymentRecord = PaymentRecordDTO.builder()
                .bankTransactionId(bankTransactionId)
                .paymentTargetType(paymentTargetType)
                .targetId(targetId)
                .amount(amount)
                .sourceType(sourceType)
                .recordStatus(RecordStatus.CONFIRMED)
                .recordedAt(LocalDateTime.now())
                .build();

        int insertedCount;
        try {
            insertedCount = paymentRecordMapper.insert(paymentRecord);
        } catch (DuplicateKeyException exception) {
            throw PaymentErrorCode.DUPLICATE_PAYMENT_RECORD.toException();
        }

        if (insertedCount != 1) {
            throw PaymentErrorCode.PAYMENT_RECORD_CREATE_FAILED.toException();
        }

        return paymentRecord.getPaymentRecordId();
    }

    @Transactional(readOnly = true)
    public boolean existsByBankTransactionId(Long bankTransactionId) {

        validateBankTransactionId(bankTransactionId);

        return paymentRecordMapper.existsByBankTransactionId(bankTransactionId);
    }

    private void validateNotDuplicateBankTransaction(Long bankTransactionId) {
        if (paymentRecordMapper.existsByBankTransactionId(bankTransactionId)) {
            throw PaymentErrorCode.DUPLICATE_PAYMENT_RECORD.toException();
        }
    }

    private void validateTarget(
            PaymentTargetType paymentTargetType,
            Long targetId
    ) {
        if (paymentTargetType == null || targetId == null) {
            throw PaymentErrorCode.INVALID_PAYMENT_TARGET.toException();
        }
    }

    private void validateBankTransactionId(Long bankTransactionId) {
        if (bankTransactionId == null) {
            throw PaymentErrorCode.INVALID_BANK_TRANSACTION_ID.toException();
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentErrorCode.INVALID_PAYMENT_AMOUNT.toException();
        }
    }

    private void validateSourceType(SourceType sourceType) {
        if (sourceType == null) {
            throw PaymentErrorCode.INVALID_PAYMENT_SOURCE_TYPE.toException();
        }
    }
}
