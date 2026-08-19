package org.teamsai.saibackend.domain.batch.writeoff.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contractrepaymentschedule.mapper.RepaymentScheduleMapper;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementCloseService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WriteOffBatchService {

    private static final int WRITE_OFF_DAYS_AFTER_OVERDUE = 30;

    private final PaymentObligationMapper paymentObligationMapper;
    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final SettlementCloseService settlementCloseService;

    public WriteOffResult writeOffSettlementObligations(LocalDate baseDate) {
        LocalDateTime cutoff = baseDate.minusDays(WRITE_OFF_DAYS_AFTER_OVERDUE).atStartOfDay();

        List<Long> candidateIds = paymentObligationMapper.findWriteOffCandidateIds(cutoff);
        if (candidateIds.isEmpty()) {
            return new WriteOffResult(0, 0);
        }
        int actuallyWrittenOff = writeOffOneBatch(candidateIds);
        log.info("정산 결제의무 상각 처리, 후보 {}건 중 {}건 실제 처리", candidateIds.size(), actuallyWrittenOff);

        if (actuallyWrittenOff == 0) {
            return new WriteOffResult(0, 0);
        }

        List<Long> affectedSettlementIds = paymentObligationMapper.findSettlementIdsByObligationIds(candidateIds);

        int closedCount = 0;
        for (Long settlementId : affectedSettlementIds) {
            try {
                if (settlementCloseService.autoCloseIfAllResolved(settlementId)) {
                    closedCount++;
                }
            } catch (Exception e) {
                log.error("정산 자동종결 실패, 다음 정산 계속 진행 settlementId={}", settlementId, e);
            }
        }

        return new WriteOffResult(actuallyWrittenOff, closedCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeOffOneBatch(List<Long> obligationIds) {
        int total = 0;
        for (List<Long> chunk : partition(obligationIds, 500)) {
            total += paymentObligationMapper.writeOffBulk(chunk);
        }
        return total;
    }

    public int writeOffRepaymentSchedules(LocalDate baseDate) {
        LocalDate cutoffDate = baseDate.minusDays(WRITE_OFF_DAYS_AFTER_OVERDUE);
        List<Long> candidateIds = repaymentScheduleMapper.findWriteOffCandidateIds(cutoffDate);
        if (candidateIds.isEmpty()) {
            return 0;
        }
        return writeOffSchedulesInNewTransaction(candidateIds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeOffSchedulesInNewTransaction(List<Long> scheduleIds) {
        int total = 0;
        for (List<Long> chunk : partition(scheduleIds, 500)) {
            total += repaymentScheduleMapper.writeOffBulk(chunk);
        }
        log.info("상환 스케줄 상각 처리, {}건", total);
        return total;
    }

    private List<List<Long>> partition(List<Long> list, int size) {
        List<List<Long>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}