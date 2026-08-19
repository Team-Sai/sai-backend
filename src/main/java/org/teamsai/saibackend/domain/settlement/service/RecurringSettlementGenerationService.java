package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.RecurringSettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringSettlementGenerationService {

    private static final int MAX_CATCHUP_CYCLES_PER_SETTLEMENT = 31;

    private final RecurringSettlementMapper recurringSettlementMapper;
    private final SettlementMapper settlementMapper;
    private final RecurringSettlementCycleGenerator cycleGenerator;

    public RecurringSettlementBatchResult generateTodaySettlements(LocalDate baseDate) {
        List<RecurringSettlementDTO> candidates =
                recurringSettlementMapper.findActiveInRange(baseDate);

        int succeeded = 0;
        int failed = 0;
        List<Long> failedRecurringIds = new ArrayList<>();

        for (RecurringSettlementDTO recurring : candidates) {
            boolean ok = catchUpCycles(recurring, baseDate);
            if (ok) {
                succeeded++;
            } else {
                failed++;
                failedRecurringIds.add(recurring.getRecurringSettlementId());
            }
        }

        RecurringSettlementBatchResult result =
                new RecurringSettlementBatchResult(candidates.size(), succeeded, failed, failedRecurringIds);

        log.info("정기정산 회차 생성 배치 종료 결과={}", result);
        return result;
    }

    /**
     * @return 이 정기정산 처리 중 예외 없이 정상 종료됐으면 true, 캐치업 도중 예외로 중단됐으면 false
     */
    private boolean catchUpCycles(RecurringSettlementDTO recurring, LocalDate baseDate) {
        SettlementDTO cursorSettlement =
                settlementMapper.findLatestByRecurringIdForUpdate(recurring.getRecurringSettlementId());

        if (cursorSettlement == null) {
            log.warn("직전 회차 없음, 생성 스킵 recurringId={}", recurring.getRecurringSettlementId());
            return true; // 데이터 이상은 아니고 정상적인 "생성 대상 아님" 케이스
        }

        int cycleCount = settlementMapper.countByRecurringId(recurring.getRecurringSettlementId());
        int generatedThisRun = 0;

        while (true) {
            LocalDate theoreticalNextDate =
                    cycleGenerator.calculateNthCycleDate(recurring.getStartDate(), recurring.getCycleRule(), cycleCount);

            if (baseDate.isBefore(theoreticalNextDate)) {
                return true; // 정상 종료
            }
            if (recurring.getEndDate() != null && theoreticalNextDate.isAfter(recurring.getEndDate())) {
                return true; // 종료일 도달, 정상 종료
            }
            if (generatedThisRun >= MAX_CATCHUP_CYCLES_PER_SETTLEMENT) {
                log.warn("정기정산 캐치업 상한 도달, 다음 배치에서 이어서 처리 예정 recurringId={}, 생성={}건",
                        recurring.getRecurringSettlementId(), generatedThisRun);
                return true; // 상한 도달은 실패가 아니라 다음 배치로 이어지는 정상 흐름
            }

            try {
                CycleGenerationOutcome outcome =
                        cycleGenerator.generateOneCycle(recurring, cursorSettlement, theoreticalNextDate);

                switch (outcome.result()) {
                    case CREATED -> {
                        cursorSettlement = outcome.settlement();
                        cycleCount++;
                        generatedThisRun++;
                    }
                    case CONCURRENTLY_SKIPPED -> {
                        return true; // 다른 트랜잭션이 이미 처리 중, 정상
                    }
                    case NO_ACTIVE_PARTICIPANT -> {
                        log.warn("데이터 확인 필요: ACTIVE 참여자 없음 recurringId={}", recurring.getRecurringSettlementId());
                        return true; // 실패는 아니지만 확인이 필요한 상태로 로그만 강조
                    }
                }
            } catch (Exception e) {
                log.error("정산 회차 생성 실패 recurringId={}, targetDate={}",
                        recurring.getRecurringSettlementId(), theoreticalNextDate, e);
                return false; // 진짜 실패 -> 집계에 반영됨
            }
        }
    }
}