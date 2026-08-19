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
import org.teamsai.saibackend.domain.settlement.service.*;
import org.teamsai.saibackend.domain.settlement.type.CycleRule;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringSettlementGenerationServiceTest {

    @Mock private RecurringSettlementMapper recurringSettlementMapper;
    @Mock private SettlementMapper settlementMapper;
    @Mock private RecurringSettlementCycleGenerator cycleGenerator;

    @InjectMocks
    private RecurringSettlementGenerationService sut;

    private RecurringSettlementDTO recurring(Long id, LocalDate startDate) {
        return recurring(id, startDate, null);
    }

    private RecurringSettlementDTO recurring(Long id, LocalDate startDate, LocalDate endDate) {
        return RecurringSettlementDTO.builder()
                .recurringSettlementId(id)
                .cycleRule(CycleRule.MONTHLY)
                .startDate(startDate)
                .endDate(endDate)
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

        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator, never()).generateOneCycle(any(), any(), any());
        assertThat(result.succeeded()).isEqualTo(1); // 직전 회차 없음도 정상 종료로 집계됨
        assertThat(result.failed()).isEqualTo(0);
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
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1);

        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 1))
                .thenReturn(LocalDate.of(2026, 2, 28));
        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 2))
                .thenReturn(LocalDate.of(2026, 3, 31));

        when(cycleGenerator.generateOneCycle(r1, latest, LocalDate.of(2026, 2, 28)))
                .thenReturn(CycleGenerationOutcome.created(created));

        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator, times(1)).generateOneCycle(any(), any(), any());
        assertThat(result.succeeded()).isEqualTo(1);
    }

    @Test
    @DisplayName("배치가 밀려 여러 회차가 도래했으면 baseDate까지 연속으로 캐치업한다")
    void catchesUpMultipleOverdueCycles() {
        LocalDate baseDate = LocalDate.of(2026, 4, 15);
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
                .thenReturn(LocalDate.of(2026, 4, 30));

        when(cycleGenerator.generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28)))
                .thenReturn(CycleGenerationOutcome.created(cycle2));
        when(cycleGenerator.generateOneCycle(r1, cycle2, LocalDate.of(2026, 3, 31)))
                .thenReturn(CycleGenerationOutcome.created(cycle3));

        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator).generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28));
        verify(cycleGenerator).generateOneCycle(r1, cycle2, LocalDate.of(2026, 3, 31));
        verify(cycleGenerator, times(2)).generateOneCycle(any(), any(), any());
        assertThat(result.succeeded()).isEqualTo(1);
    }

    @Test
    @DisplayName("캐치업 도중 한 회차가 실패하면, 이미 성공한 이전 회차는 유지하고 그 이후 회차는 이번 배치에서 중단하며 실패로 집계된다")
    void stopsCatchUpOnFailureAndCountsAsFailed() {
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

        when(cycleGenerator.generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28)))
                .thenReturn(CycleGenerationOutcome.created(cycle2));
        when(cycleGenerator.generateOneCycle(r1, cycle2, LocalDate.of(2026, 3, 31)))
                .thenThrow(new IllegalStateException("insert 실패"));

        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator).generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28));
        verify(cycleGenerator).generateOneCycle(r1, cycle2, LocalDate.of(2026, 3, 31));
        verify(cycleGenerator, times(2)).generateOneCycle(any(), any(), any());
        verify(cycleGenerator, never()).calculateNthCycleDate(any(), any(), eq(3));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedRecurringIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("동시성 충돌로 CONCURRENTLY_SKIPPED가 반환되면 캐치업을 중단하되 실패로 집계하지 않는다")
    void stopsCatchUpWhenConcurrentlySkipped() {
        LocalDate baseDate = LocalDate.of(2026, 3, 1);
        RecurringSettlementDTO r1 = recurring(1L, LocalDate.of(2026, 1, 31));
        SettlementDTO cycle1 = settlement(10L, 1L, LocalDate.of(2026, 1, 31));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(cycle1);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1);

        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 1))
                .thenReturn(LocalDate.of(2026, 2, 28));
        when(cycleGenerator.generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28)))
                .thenReturn(CycleGenerationOutcome.concurrentlySkipped());

        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator, times(1)).generateOneCycle(any(), any(), any());
        verify(cycleGenerator, never()).calculateNthCycleDate(any(), any(), eq(2));
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    @DisplayName("ACTIVE 참여자가 없어 NO_ACTIVE_PARTICIPANT가 반환되면 캐치업을 중단하되 실패로 집계하지 않는다")
    void stopsCatchUpWhenNoActiveParticipant() {
        LocalDate baseDate = LocalDate.of(2026, 3, 1);
        RecurringSettlementDTO r1 = recurring(1L, LocalDate.of(2026, 1, 31));
        SettlementDTO cycle1 = settlement(10L, 1L, LocalDate.of(2026, 1, 31));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(cycle1);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1);

        when(cycleGenerator.calculateNthCycleDate(r1.getStartDate(), CycleRule.MONTHLY, 1))
                .thenReturn(LocalDate.of(2026, 2, 28));
        when(cycleGenerator.generateOneCycle(r1, cycle1, LocalDate.of(2026, 2, 28)))
                .thenReturn(CycleGenerationOutcome.noActiveParticipant());

        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator, times(1)).generateOneCycle(any(), any(), any());
        verify(cycleGenerator, never()).calculateNthCycleDate(any(), any(), eq(2));
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    @DisplayName("한 recurring 건이 실패해도 다른 recurring 건 처리는 계속되고, 결과에 실패/성공이 각각 집계된다")
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
        when(cycleGenerator.calculateNthCycleDate(any(), any(), eq(2)))
                .thenReturn(LocalDate.of(2026, 3, 31));

        when(cycleGenerator.generateOneCycle(eq(r1), eq(latest1), any()))
                .thenThrow(new IllegalStateException("r1 실패"));
        when(cycleGenerator.generateOneCycle(eq(r2), eq(latest2), any()))
                .thenReturn(CycleGenerationOutcome.created(settlement(21L, 2L, LocalDate.of(2026, 2, 28))));

        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator).generateOneCycle(eq(r1), eq(latest1), any());
        verify(cycleGenerator).generateOneCycle(eq(r2), eq(latest2), any());
        verify(cycleGenerator, times(1)).generateOneCycle(eq(r2), any(), any());

        assertThat(result.totalCandidates()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedRecurringIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("종료일이 지난 정기정산도 종료일까지 미생성 회차가 있으면 계속 조회 대상이 된다")
    void includesEndedRecurringSettlementWithUngeneratedCyclesUpToEndDate() {
        LocalDate baseDate = LocalDate.of(2026, 8, 17);
        RecurringSettlementDTO recurring = recurring(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 15));
        SettlementDTO lastGenerated = settlement(10L, 1L, LocalDate.of(2026, 8, 13));
        SettlementDTO generated = settlement(11L, 1L, LocalDate.of(2026, 8, 14));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(recurring));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(lastGenerated);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(13);

        when(cycleGenerator.calculateNthCycleDate(recurring.getStartDate(), recurring.getCycleRule(), 13))
                .thenReturn(LocalDate.of(2026, 8, 14));
        when(cycleGenerator.calculateNthCycleDate(recurring.getStartDate(), recurring.getCycleRule(), 14))
                .thenReturn(LocalDate.of(2026, 8, 15));
        when(cycleGenerator.calculateNthCycleDate(recurring.getStartDate(), recurring.getCycleRule(), 15))
                .thenReturn(LocalDate.of(2026, 8, 16));

        when(cycleGenerator.generateOneCycle(recurring, lastGenerated, LocalDate.of(2026, 8, 14)))
                .thenReturn(CycleGenerationOutcome.created(generated));
        when(cycleGenerator.generateOneCycle(eq(recurring), eq(generated), any()))
                .thenReturn(CycleGenerationOutcome.created(settlement(12L, 1L, LocalDate.of(2026, 8, 15))));

        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator).generateOneCycle(recurring, lastGenerated, LocalDate.of(2026, 8, 14));
        verify(cycleGenerator).generateOneCycle(eq(recurring), eq(generated), eq(LocalDate.of(2026, 8, 15)));
        verify(cycleGenerator, never()).generateOneCycle(any(), any(), eq(LocalDate.of(2026, 8, 16)));
        assertThat(result.succeeded()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 정기정산의 캐치업이 상한(31회)에 도달하면 그 지점에서 멈추고 다른 정기정산 처리로 넘어간다")
    void stopsAtCatchUpLimitAndProcessesOtherRecurrings() {
        LocalDate baseDate = LocalDate.of(2026, 12, 31);
        RecurringSettlementDTO heavilyDelayed = recurring(1L, LocalDate.of(2026, 1, 1));
        RecurringSettlementDTO normal = recurring(2L, LocalDate.of(2026, 12, 30));

        SettlementDTO delayedLast = settlement(10L, 1L, LocalDate.of(2026, 1, 1));
        SettlementDTO normalLast = settlement(20L, 2L, LocalDate.of(2026, 12, 30));

        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(heavilyDelayed, normal));
        when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(delayedLast);
        when(settlementMapper.findLatestByRecurringId(2L)).thenReturn(normalLast);
        when(settlementMapper.countByRecurringId(1L)).thenReturn(1);
        when(settlementMapper.countByRecurringId(2L)).thenReturn(1);

        when(cycleGenerator.calculateNthCycleDate(eq(heavilyDelayed.getStartDate()), any(), anyInt()))
                .thenReturn(LocalDate.of(2026, 1, 2));
        when(cycleGenerator.generateOneCycle(eq(heavilyDelayed), any(), any()))
                .thenReturn(CycleGenerationOutcome.created(settlement(99L, 1L, LocalDate.of(2026, 1, 2))));

        when(cycleGenerator.calculateNthCycleDate(eq(normal.getStartDate()), any(), eq(1)))
                .thenReturn(LocalDate.of(2026, 12, 31));
        when(cycleGenerator.calculateNthCycleDate(eq(normal.getStartDate()), any(), eq(2)))
                .thenReturn(LocalDate.of(2027, 1, 30));
        when(cycleGenerator.generateOneCycle(eq(normal), eq(normalLast), any()))
                .thenReturn(CycleGenerationOutcome.created(settlement(21L, 2L, LocalDate.of(2026, 12, 31))));
        RecurringSettlementBatchResult result = sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator, times(31)).generateOneCycle(eq(heavilyDelayed), any(), any());
        verify(cycleGenerator).generateOneCycle(eq(normal), eq(normalLast), any());
        assertThat(result.succeeded()).isEqualTo(2); // 둘 다 "실패"는 아니므로 성공 집계
    }
}