package org.teamsai.saibackend.domain.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contractrepaymentschedule.mapper.RepaymentScheduleMapper;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WriteOffTransactionExecutor {

    private final PaymentObligationMapper paymentObligationMapper;
    private final RepaymentScheduleMapper repaymentScheduleMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeOffOneBatch(List<Long> obligationIds) {
        int total = 0;
        for (List<Long> chunk : partition(obligationIds, 500)) {
            total += paymentObligationMapper.writeOffBulk(chunk);
        }
        return total;
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