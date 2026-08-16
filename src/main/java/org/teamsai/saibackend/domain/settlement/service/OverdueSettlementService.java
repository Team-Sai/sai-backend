package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueSettlementService {

    private static final int PAGE_SIZE = 200;

    private final OverdueCriteria overdueCriteria;
    private final SettlementMapper settlementMapper;
    private final OverdueSettlementUpdater overdueSettlementUpdater;

    public void updateOverdueStatus(LocalDate baseDate) {
        int offset = 0;

        while (true) {
            List<SettlementDTO> page = settlementMapper.findInProgressSettlements(offset, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }

            for (SettlementDTO settlement : page) {
                if (!overdueCriteria.isOverdue(settlement, baseDate)) {
                    continue;
                }
                try {
                    overdueSettlementUpdater.updateOverdueForSettlement(settlement, baseDate);
                } catch (Exception e) {
                    log.error("연체 상태 갱신 실패, 다음 배치에서 재시도 예정 settlementId={}",
                            settlement.getSettlementId(), e);
                }
            }

            if (page.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
    }
}