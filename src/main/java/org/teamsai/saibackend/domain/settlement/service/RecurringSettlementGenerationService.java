package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.RecurringSettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;


import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringSettlementGenerationService {

    private final RecurringSettlementMapper recurringSettlementMapper;
    private final SettlementMapper settlementMapper;
    private final RecurringSettlementCycleGenerator cycleGenerator;

    public void generateTodaySettlements(LocalDate baseDate) {
        List<RecurringSettlementDTO> candidates =
                recurringSettlementMapper.findActiveInRange(baseDate);

        for (RecurringSettlementDTO recurring : candidates) {
            catchUpCycles(recurring, baseDate);
        }
    }

    private void catchUpCycles(RecurringSettlementDTO recurring, LocalDate baseDate) {
        SettlementDTO cursorSettlement =
                settlementMapper.findLatestByRecurringId(recurring.getRecurringSettlementId());

        if (cursorSettlement == null) {
            log.warn("직전 회차 없음, 생성 스킵 recurringId={}", recurring.getRecurringSettlementId());
            return;
        }

        int cycleCount = settlementMapper.countByRecurringId(recurring.getRecurringSettlementId());

        while (true) {
            LocalDate theoreticalNextDate =
                    cycleGenerator.calculateNthCycleDate(recurring.getStartDate(), recurring.getCycleRule(), cycleCount);

            if (baseDate.isBefore(theoreticalNextDate)) {
                break;
            }

            try {
                SettlementDTO created = cycleGenerator.generateOneCycle(recurring, cursorSettlement, theoreticalNextDate);
                if (created == null) {
                    break;
                }
                cursorSettlement = created;
                cycleCount++;
            } catch (Exception e) {
                log.error("정산 회차 생성 실패, 다음 배치에서 재시도 예정 recurringId={}, targetDate={}",
                        recurring.getRecurringSettlementId(), theoreticalNextDate, e);
                break;
            }
        }
    }
}
