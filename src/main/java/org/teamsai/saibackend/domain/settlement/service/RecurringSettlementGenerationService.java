package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.RecurringSettlementMapper;


import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringSettlementGenerationService {
    private final RecurringSettlementMapper recurringSettlementMapper;
    private final RecurringSettlementCycleGenerator cycleGenerator;

    public void generateTodaySettlements(LocalDate baseDate) {
        List<RecurringSettlementDTO> candidates = recurringSettlementMapper.findActiveInRange(baseDate);

        for (RecurringSettlementDTO recurring : candidates) {
            try {
                cycleGenerator.generateNextCycle(recurring, baseDate); // 다른 Bean 통해 호출 → 프록시 거침
            } catch (Exception e) {
                log.error("정산 회차 생성 실패 recurringId={}", recurring.getRecurringSettlementId(), e);
            }
        }
    }
}
