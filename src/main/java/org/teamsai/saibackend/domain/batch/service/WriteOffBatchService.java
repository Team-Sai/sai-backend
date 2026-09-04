package org.teamsai.saibackend.domain.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contract.mapper.RepaymentScheduleMapper;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementCloseService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WriteOffBatchService {

    private static final int WRITE_OFF_DAYS_AFTER_OVERDUE = 30;

    private final PaymentObligationMapper paymentObligationMapper;
    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final SettlementCloseService settlementCloseService;
    private final WriteOffTransactionExecutor writeOffTransactionExecutor;  // 추가

    @Transactional
    public WriteOffResult writeOffSettlementObligations(LocalDate baseDate) {
        LocalDateTime cutoff = baseDate.minusDays(WRITE_OFF_DAYS_AFTER_OVERDUE).atStartOfDay();
        List<Long> candidateIds = paymentObligationMapper.findWriteOffCandidateIds(cutoff);
        if (candidateIds.isEmpty()) {
            return new WriteOffResult(0, 0);
        }

        int actuallyWrittenOff = writeOffTransactionExecutor.writeOffOneBatch(candidateIds);  // 프록시를 거치는 진짜 외부 호출
        log.info("정산 결제의무 상각 처리, 후보 {}건 중 {}건 실제 처리", candidateIds.size(), actuallyWrittenOff);

        if (actuallyWrittenOff == 0) {
            return new WriteOffResult(0, 0);
        }

        List<Long> affectedSettlementIds = paymentObligationMapper.findSettlementIdsByObligationIds(candidateIds);
        int closedCount = 0;
        for (Long settlementId : affectedSettlementIds) {
            try {
                boolean closed = settlementCloseService.autoCloseIfAllResolved(settlementId);
                log.info("자동종결 결과 settlementId={}, closed={}", settlementId, closed);
                if (closed) {
                    closedCount++;
                }
            } catch (Exception e) {
                log.error("정산 자동종결 실패, 다음 정산 계속 진행 settlementId={}", settlementId, e);
            }
        }
        return new WriteOffResult(actuallyWrittenOff, closedCount);
    }

    @Transactional
    public int writeOffRepaymentSchedules(LocalDate baseDate) {
        LocalDate cutoffDate = baseDate.minusDays(WRITE_OFF_DAYS_AFTER_OVERDUE);
        List<Long> candidateIds = repaymentScheduleMapper.findWriteOffCandidateIds(cutoffDate);
        if (candidateIds.isEmpty()) {
            return 0;
        }
        return writeOffTransactionExecutor.writeOffSchedulesInNewTransaction(candidateIds);  // 마찬가지
    }
}