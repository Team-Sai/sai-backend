package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.RecurringSettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementCycleGenerator;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementGenerationService;
import org.teamsai.saibackend.domain.settlement.type.CycleRule;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringSettlementGenerationServiceTest {

    @Mock private RecurringSettlementMapper recurringSettlementMapper;
    @Mock private SettlementMapper settlementMapper;
    @Mock private RecurringSettlementCycleGenerator cycleGenerator;

    @InjectMocks
    private RecurringSettlementGenerationService sut;

    private RecurringSettlementDTO recurring(Long id, LocalDate startDate) {
        return RecurringSettlementDTO.builder()
                .recurringSettlementId(id)
                .cycleRule(CycleRule.MONTHLY)
                .startDate(startDate)
                .build();
    }

    private SettlementDTO settlement(Long id, Long recurringId, LocalDate cycleDate) {
        return SettlementDTO.builder()
                .settlementId(id)
                .recurringSettlementId(recurringId)
                .cycleDate(cycleDate)
                .build();
    }

    @Test
    @DisplayName("직전 회차가 없으면 캐치업을 시도하지 않는다")
    void skipsWhenNoLatestSettlement() {
        LocalDate baseDate = LocalDate.of(2026, 3, 1);
        RecurringSettlementDTO r1 = recurring(1L, LocalDate.of(2026, 1, 31));
        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(null);

        sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator, never()).generateOneCycle(any(), any(), any());
    }

    @Test
    @DisplayName("도래한 회차가 하나면 정확히 1번만 생성을 시도한다")
    void generatesExactlyOneCycleWhenOnlyOneIsDue() {
        LocalDate baseDate = LocalDate.of(2026, 2, 28);
        RecurringSettlementDTO r1 = recurring(1L, LocalDate.of(2026, 1, 31));
        SettlementDTO latest = settlement(10L, 1L, LocalDate.of(2026, 1, 31));
        SettlementDTO created = settlement(11L, 1L, LocalDate.of(2026, 2, 28));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(latest);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1); // 이미 1회차 존재

        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 1))
                .thenReturn(LocalDate.of(2026, 2, 28)); // baseDate와 같음 -> 도래
        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 2))
                .thenReturn(LocalDate.of(2026, 3, 31)); // baseDate 이후 -> 미도래, 루프 종료

        when(cycleGenerator.generateOneCycle(r1, latest, LocalDate.of(2026, 2, 28)))
                .thenReturn(created);

        sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator, times(1)).generateOneCycle(any(), any(), any());
    }

    @Test
    @DisplayName("배치가 밀려 여러 회차가 도래했으면 baseDate까지 연속으로 캐치업한다")
    void catchesUpMultipleOverdueCycles() {
        LocalDate baseDate = LocalDate.of(2026, 4, 15); // 2, 3, 4월 회차가 밀린 상태
        RecurringSettlementDTO r1 = recurring(1L, LocalDate.of(2026, 1, 31));
        SettlementDTO cycle1 = settlement(10L, 1L, LocalDate.of(2026, 1, 31));
        SettlementDTO cycle2 = settlement(11L, 1L, LocalDate.of(2026, 2, 28));
        SettlementDTO cycle3 = settlement(12L, 1L, LocalDate.of(2026, 3, 31));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(cycle1);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1);

        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 1))
                .thenReturn(LocalDate.of(2026, 2, 28));
        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 2))
                .thenReturn(LocalDate.of(2026, 3, 31));
        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 3))
                .thenReturn(LocalDate.of(2026, 4, 30)); // baseDate(4/15) 이후 -> 루프 종료

        when(cycleGenerator.generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28))).thenReturn(cycle2);
        when(cycleGenerator.generateOneCycle(r1, cycle2, LocalDate.of(2026, 3, 31))).thenReturn(cycle3);

        sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator).generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28));
        verify(cycleGenerator).generateOneCycle(r1, cycle2, LocalDate.of(2026, 3, 31));
        verify(cycleGenerator, times(2)).generateOneCycle(any(), any(), any());
    }

    @Test
    @DisplayName("캐치업 도중 한 회차가 실패하면, 이미 성공한 이전 회차는 유지하고 그 이후 회차는 이번 배치에서 중단한다 (부분 커밋)")
    void stopsCatchUpOnFailureButKeepsPreviouslySucceededCycles() {
        LocalDate baseDate = LocalDate.of(2026, 4, 15);
        RecurringSettlementDTO r1 = recurring(1L, LocalDate.of(2026, 1, 31));
        SettlementDTO cycle1 = settlement(10L, 1L, LocalDate.of(2026, 1, 31));
        SettlementDTO cycle2 = settlement(11L, 1L, LocalDate.of(2026, 2, 28));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(cycle1);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1);

        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 1))
                .thenReturn(LocalDate.of(2026, 2, 28));
        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 2))
                .thenReturn(LocalDate.of(2026, 3, 31));

        // 1번째 회차는 성공(이미 별도 트랜잭션에서 커밋된 것으로 간주)
        when(cycleGenerator.generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28))).thenReturn(cycle2);
        // 2번째 회차에서 예외 발생
        when(cycleGenerator.generateOneCycle(r1, cycle2, LocalDate.of(2026, 3, 31)))
                .thenThrow(new IllegalStateException("insert 실패"));

        sut.generateTodaySettlements(baseDate);

        // 1번째는 시도됨(=커밋됨), 2번째도 시도됐지만 실패, 3번째는 시도조차 안 됨(루프 중단)
        verify(cycleGenerator).generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28));
        verify(cycleGenerator).generateOneCycle(r1, cycle2, LocalDate.of(2026, 3, 31));
        verify(cycleGenerator, times(2)).generateOneCycle(any(), any(), any());
        // calculateNthCycleDate(n=3)까지는 호출되지 않아야 함 (예외 이후 즉시 중단)
        verify(cycleGenerator, never()).calculateNthCycleDate(any(), any(), eq(3));
    }

    @Test
    @DisplayName("generateOneCycle이 null을 반환하면(동시성 충돌 또는 참여자 없음) 캐치업을 중단한다")
    void stopsCatchUpWhenGenerateOneCycleReturnsNull() {
        LocalDate baseDate = LocalDate.of(2026, 3, 1);
        RecurringSettlementDTO r1 = recurring(1L, LocalDate.of(2026, 1, 31));
        SettlementDTO cycle1 = settlement(10L, 1L, LocalDate.of(2026, 1, 31));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(cycle1);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1);

        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 1))
                .thenReturn(LocalDate.of(2026, 2, 28));
        when(cycleGenerator.generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28))).thenReturn(null);

        sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator, times(1)).generateOneCycle(any(), any(), any());
        verify(cycleGenerator, never()).calculateNthCycleDate(any(), any(), eq(2));
    }

    @Test
    @DisplayName("한 recurring 건이 실패해도 다른 recurring 건 처리는 계속된다")
    void continuesOtherRecurringsWhenOneFails() {
        LocalDate baseDate = LocalDate.of(2026, 2, 28);
        RecurringSettlementDTO r1 = recurring(1L, LocalDate.of(2026, 1, 31));
        RecurringSettlementDTO r2 = recurring(2L, LocalDate.of(2026, 1, 31));
        SettlementDTO latest1 = settlement(10L, 1L, LocalDate.of(2026, 1, 31));
        SettlementDTO latest2 = settlement(20L, 2L, LocalDate.of(2026, 1, 31));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1, r2));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(latest1);
        when(settlementMapper.findLatestByRecurringId(2L)).thenReturn(latest2);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1);
        when(settlementMapper.countByRecurringId(2L)).thenReturn(1);

        when(cycleGenerator.calculateNthCycleDate(any(), any(), eq(1)))
                .thenReturn(LocalDate.of(2026, 2, 28));

        when(cycleGenerator.generateOneCycle(eq(r1), eq(latest1), any()))
                .thenThrow(new IllegalStateException("r1 실패"));
        when(cycleGenerator.generateOneCycle(eq(r2), eq(latest2), any()))
                .thenReturn(settlement(21L, 2L, LocalDate.of(2026, 2, 28)));

        sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator).generateOneCycle(eq(r1), eq(latest1), any());
        verify(cycleGenerator).generateOneCycle(eq(r2), eq(latest2), any()); // r1 실패와 무관하게 호출됨
    }
}