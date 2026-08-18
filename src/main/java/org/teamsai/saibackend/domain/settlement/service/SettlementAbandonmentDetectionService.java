package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.batch.common.notification.SlackNotifier;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementAbandonmentAlertMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementAbandonmentDetectionService {

    private static final int PAGE_SIZE = 100;
    private static final int ABANDONMENT_DAYS_AFTER_DUE = 3;

    private final OverdueCriteria overdueCriteria;
    private final SettlementMapper settlementMapper;
    private final SettlementAbandonmentAlertMapper alertMapper;
    private final SlackNotifier slackNotifier;

    public SettlementAbandonmentResult detectAbandoned(LocalDate baseDate) {
        int totalCount = settlementMapper.countInProgressSettlements();
        log.info("정산 장기방치 감지 배치 시작, 대상 정산 총 {}건, baseDate={}", totalCount, baseDate);

        int detectedCount = 0;
        int offset = 0;

        while (true) {
            List<SettlementDTO> page = settlementMapper.findInProgressSettlements(offset, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }

            for (SettlementDTO settlement : page) {
                LocalDate referenceDate = overdueCriteria.resolveReferenceDate(settlement);

                if (!isAbandoned(referenceDate, baseDate)) {
                    continue;
                }
                if (alertMapper.existsBySettlementId(settlement.getSettlementId())) {
                    continue;
                }

                alertMapper.insertIfAbsent(settlement.getSettlementId(), referenceDate);
                notifyAbandoned(settlement, referenceDate, baseDate);
                detectedCount++;
            }

            if (page.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }

        log.info("정산 장기방치 감지 배치 종료, 신규 방치 감지 {}건 (총 {}건 중)", detectedCount, totalCount);
        return new SettlementAbandonmentResult(detectedCount);
    }

    private boolean isAbandoned(LocalDate referenceDate, LocalDate baseDate) {
        if (referenceDate == null) {
            return false;
        }
        return !referenceDate.plusDays(ABANDONMENT_DAYS_AFTER_DUE).isAfter(baseDate);
    }

    private void notifyAbandoned(SettlementDTO settlement, LocalDate referenceDate, LocalDate baseDate) {
        long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(referenceDate, baseDate);
        slackNotifier.send(String.format(
                "⚠️ *정산 장기 방치 감지* — settlementId=%d, title=%s, 기준일=%s (%d일 경과), 여전히 IN_PROGRESS",
                settlement.getSettlementId(),
                settlement.getTitle(),
                referenceDate,
                daysOverdue
        ));
    }
}