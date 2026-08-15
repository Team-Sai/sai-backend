package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.RecurringSettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementCycleGenerator;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementGenerationService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringSettlementGenerationServiceTest {

    @Mock private RecurringSettlementMapper recurringSettlementMapper;
    @Mock private RecurringSettlementCycleGenerator cycleGenerator;

    @InjectMocks
    private RecurringSettlementGenerationService sut;

    @Test
    @DisplayName("조회된 대상 각각에 대해 회차 생성을 시도한다")
    void invokesCycleGeneratorForEachCandidate() {
        LocalDate baseDate = LocalDate.of(2026, 2, 2);
        RecurringSettlementDTO r1 = RecurringSettlementDTO.builder().recurringSettlementId(1L).build();
        RecurringSettlementDTO r2 = RecurringSettlementDTO.builder().recurringSettlementId(2L).build();
        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1, r2));

        sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator).generateNextCycle(r1, baseDate);
        verify(cycleGenerator).generateNextCycle(r2, baseDate);
    }

    @Test
    @DisplayName("한 건이 예외를 던져도 나머지 대상은 계속 처리된다")
    void continuesProcessingWhenOneFails() {
        LocalDate baseDate = LocalDate.of(2026, 2, 2);
        RecurringSettlementDTO r1 = RecurringSettlementDTO.builder().recurringSettlementId(1L).build();
        RecurringSettlementDTO r2 = RecurringSettlementDTO.builder().recurringSettlementId(2L).build();
        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of(r1, r2));
        doThrow(new IllegalStateException("boom"))
                .when(cycleGenerator).generateNextCycle(eq(r1), eq(baseDate));

        sut.generateTodaySettlements(baseDate);

        verify(cycleGenerator).generateNextCycle(r2, baseDate);
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 호출하지 않는다")
    void doesNothingWhenNoCandidates() {
        LocalDate baseDate = LocalDate.of(2026, 2, 2);
        when(recurringSettlementMapper.findActiveInRange(baseDate)).thenReturn(List.of());

        sut.generateTodaySettlements(baseDate);

        verifyNoInteractions(cycleGenerator);
    }
}