package org.teamsai.saibackend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleStatus;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;
import org.teamsai.saibackend.domain.payment.service.PaymentRecordService;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.payment.type.SourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanPaymentService {

    private final RepaymentScheduleService repaymentScheduleService;
    private final PaymentRecordService paymentRecordService;

    @Transactional
    public void applyAutoMatchedPayment(Long targetId, Long bankTransactionId, BigDecimal amount) {

        Optional<RepaymentScheduleDTO> scheduleOpt = repaymentScheduleService.findNextPendingSchedule(targetId);

        if (scheduleOpt.isEmpty()) {
            throw new IllegalArgumentException("유효한 상환 스케줄이 존재하지 않습니다. scheduleId=" + targetId);
        }

        RepaymentScheduleDTO schedule = scheduleOpt.get();

        if (schedule.getStatus() != RepaymentScheduleStatus.PENDING) {
            throw new IllegalStateException("이미 상환 완료되었거나 처리 불가능한 스케줄입니다.");
        }

        BigDecimal existingConfirmedAmount = paymentRecordService.sumConfirmedAmountByTarget(
                PaymentTargetType.LOAN,
                targetId
        );

        if (existingConfirmedAmount == null) {
            existingConfirmedAmount = BigDecimal.ZERO;
        }

        // 4. 초과 상환 검증 (기존 납부금 + 이번 입금액 > 상환 예정 금액)
        BigDecimal expectedTotalAmount = schedule.getTotalPaymentDue();
        BigDecimal totalPaidAfterThis = existingConfirmedAmount.add(amount);

        if (totalPaidAfterThis.compareTo(expectedTotalAmount) > 0) {
            log.warn("[LoanPaymentService] 초과 상환 발생 - scheduleId: {}, 예정금액: {}, 시도금액: {}",
                    targetId, expectedTotalAmount, totalPaidAfterThis);
            throw new IllegalArgumentException("상환 예정 금액을 초과하여 결제할 수 없습니다.");
        }

        // 5. PaymentRecord 저장 (PaymentRecordService의 실제 메서드 createConfirmedRecord 사용)
        Long paymentRecordId = paymentRecordService.createConfirmedRecord(
                bankTransactionId,       // 은행 거래 내역 ID
                PaymentTargetType.LOAN,  // LOAN
                targetId,               // schedule_id
                amount,                 // 상환 금액
                SourceType.AUTO         // 자동 매칭인 경우 SourceType.AUTO (상황에 맞춰 SourceType 지정)
        );

        log.info("[LoanPaymentService] PaymentRecord 생성 완료 - paymentRecordId: {}", paymentRecordId);

        // 6. 완납 조건 충족 시 RepaymentScheduleService를 통한 상태 변경 (PAID)
        if (totalPaidAfterThis.compareTo(expectedTotalAmount) == 0) {
            repaymentScheduleService.markAsPaid(targetId, LocalDateTime.now());
            log.info("[LoanPaymentService] 상환 상태 완료(PAID) 처리 - scheduleId: {}", targetId);
        }
    }
}