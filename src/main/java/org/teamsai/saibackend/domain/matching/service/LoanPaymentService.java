package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.exception.RepaymentScheduleErrorCode;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.service.PaymentRecordService;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.payment.type.SourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanPaymentService {

    private final RepaymentScheduleService repaymentScheduleService;
    private final PaymentRecordService paymentRecordService;

    @Transactional
    public void applyAutoMatchedPayment(Long targetId, Long bankTransactionId, BigDecimal amount) {

        RepaymentScheduleDTO schedule = repaymentScheduleService.getScheduleByScheduleId(targetId);

        if (schedule.getStatus() != RepaymentScheduleStatus.PENDING) {
            log.warn("[LoanPaymentService] 처리 불가능한 스케줄 상태 - scheduleId: {}, status: {}", targetId, schedule.getStatus());
            throw RepaymentScheduleErrorCode.SCHEDULE_NOT_PENDING.toException();
        }

        BigDecimal existingConfirmedAmount = paymentRecordService.sumConfirmedAmountByTarget(
                PaymentTargetType.LOAN,
                targetId
        );

        if (existingConfirmedAmount == null) {
            existingConfirmedAmount = BigDecimal.ZERO;
        }

        BigDecimal expectedTotalAmount = schedule.getTotalPaymentDue();
        BigDecimal totalPaidAfterThis = existingConfirmedAmount.add(amount);


        if (totalPaidAfterThis.compareTo(expectedTotalAmount) > 0) {
            log.warn("[LoanPaymentService] 초과 상환 발생 - scheduleId: {}, 예정금액: {}, 시도금액: {}",
                    targetId, expectedTotalAmount, totalPaidAfterThis);
            throw PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING_AMOUNT.toException();
        }

        if (totalPaidAfterThis.compareTo(expectedTotalAmount) < 0) {
            log.info("[LoanPaymentService] 회차 금액 미달 (부분 납부 기록만 생성) - scheduleId: {}, 누적납부액: {}, 예정액: {}",
                    targetId, totalPaidAfterThis, expectedTotalAmount);
        }

        Long paymentRecordId = paymentRecordService.createConfirmedRecord(
                bankTransactionId,
                PaymentTargetType.LOAN,
                targetId,
                amount,
                SourceType.AUTO_MATCH
        );

        log.info("[LoanPaymentService] PaymentRecord 생성 완료 - paymentRecordId: {}", paymentRecordId);

        if (totalPaidAfterThis.compareTo(expectedTotalAmount) == 0) {
            repaymentScheduleService.markAsPaid(targetId, LocalDateTime.now());
            log.info("[LoanPaymentService] 상환 상태 완료(PAID) 처리 - scheduleId: {}", targetId);
        }
    }
}