package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.OverdueCriteria;
import org.teamsai.saibackend.domain.settlement.service.OverdueSettlementService;
import org.teamsai.saibackend.domain.settlement.service.OverdueSettlementUpdater;
import org.teamsai.saibackend.domain.settlement.service.OverdueUpdateResult;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueSettlementServiceTest {

    @Mock private OverdueCriteria overdueCriteria;
    @Mock private SettlementMapper settlementMapper;
    @Mock private OverdueSettlementUpdater overdueSettlementUpdater;

    @InjectMocks
    private OverdueSettlementService sut;

    private SettlementDTO settlement(Long id) {
        return SettlementDTO.builder().settlementId(id).build();
    }

    @Test
    @DisplayName("연체로 판정된 정산만 updater에 위임하고, 판정 안 된 정산은 건드리지 않는다")
    void onlyDelegatesOverdueSettlementsToUpdater() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        SettlementDTO overdue = settlement(1L);
        SettlementDTO notOverdue = settlement(2L);
        LocalDate overdueRefDate = LocalDate.of(2026, 1, 15);
        LocalDate notOverdueRefDate = LocalDate.of(2026, 2, 15);

        when(settlementMapper.findInProgressSettlements(0, 200)).thenReturn(List.of(overdue, notOverdue));
        when(overdueCriteria.resolveReferenceDate(overdue)).thenReturn(overdueRefDate);
        when(overdueCriteria.resolveReferenceDate(notOverdue)).thenReturn(notOverdueRefDate);
        when(overdueCriteria.isOverdue(overdue, baseDate, overdueRefDate)).thenReturn(true);
        when(overdueCriteria.isOverdue(notOverdue, baseDate, notOverdueRefDate)).thenReturn(false);

        sut.updateOverdueStatus(baseDate);

        verify(overdueSettlementUpdater).updateOverdueForSettlement(overdue, overdueRefDate);
        verify(overdueSettlementUpdater, never()).updateOverdueForSettlement(eq(notOverdue), any());
    }

    @Test
    @DisplayName("첫 페이지가 PAGE_SIZE 미만이면 다음 페이지를 조회하지 않고 종료한다")
    void stopsWhenFirstPageIsPartial() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        List<SettlementDTO> partialPage = List.of(settlement(1L), settlement(2L));

        when(settlementMapper.findInProgressSettlements(0, 200)).thenReturn(partialPage);
        when(overdueCriteria.resolveReferenceDate(any())).thenReturn(LocalDate.of(2026, 3, 1));
        when(overdueCriteria.isOverdue(any(), eq(baseDate), any())).thenReturn(false);

        sut.updateOverdueStatus(baseDate);

        verify(settlementMapper, times(1)).findInProgressSettlements(anyInt(), anyInt());
    }

    @Test
    @DisplayName("페이지가 가득 차면 다음 offset으로 계속 조회하고, 빈 페이지가 나오면 종료한다")
    void continuesToNextPageWhenFull() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        List<SettlementDTO> fullFirstPage = IntStream.rangeClosed(1, 200)
                .mapToObj(i -> settlement((long) i))
                .toList();
        List<SettlementDTO> secondPage = List.of(settlement(201L));

        when(settlementMapper.findInProgressSettlements(0, 200)).thenReturn(fullFirstPage);
        when(settlementMapper.findInProgressSettlements(200, 200)).thenReturn(secondPage);
        when(overdueCriteria.resolveReferenceDate(any())).thenReturn(LocalDate.of(2026, 3, 1));
        when(overdueCriteria.isOverdue(any(), eq(baseDate), any())).thenReturn(false);

        sut.updateOverdueStatus(baseDate);

        verify(settlementMapper).findInProgressSettlements(0, 200);
        verify(settlementMapper).findInProgressSettlements(200, 200);
        verify(settlementMapper, times(2)).findInProgressSettlements(anyInt(), anyInt());
    }

    @Test
    @DisplayName("대상 정산이 없으면 updater를 전혀 호출하지 않는다")
    void doesNothingWhenNoSettlements() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        when(settlementMapper.findInProgressSettlements(0, 200)).thenReturn(List.of());

        sut.updateOverdueStatus(baseDate);

        verify(overdueSettlementUpdater, never()).updateOverdueForSettlement(any(), any());
    }

    @Test
    @DisplayName("한 정산 갱신이 실패해도 나머지 정산은 계속 처리되고, 결과에 성공/실패 건수가 반영된다")
    void continuesProcessingWhenOneUpdateFails() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        SettlementDTO s1 = settlement(1L);
        SettlementDTO s2 = settlement(2L);
        LocalDate refDate1 = LocalDate.of(2026, 1, 15);
        LocalDate refDate2 = LocalDate.of(2026, 1, 16);

        when(settlementMapper.findInProgressSettlements(0, 200)).thenReturn(List.of(s1, s2));
        when(overdueCriteria.resolveReferenceDate(s1)).thenReturn(refDate1);
        when(overdueCriteria.resolveReferenceDate(s2)).thenReturn(refDate2);
        when(overdueCriteria.isOverdue(s1, baseDate, refDate1)).thenReturn(true);
        when(overdueCriteria.isOverdue(s2, baseDate, refDate2)).thenReturn(true);

        doThrow(new IllegalStateException("갱신 실패"))
                .when(overdueSettlementUpdater).updateOverdueForSettlement(s1, refDate1);

        OverdueUpdateResult result = sut.updateOverdueStatus(baseDate);

        verify(overdueSettlementUpdater).updateOverdueForSettlement(s1, refDate1);
        verify(overdueSettlementUpdater).updateOverdueForSettlement(s2, refDate2);
        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
    }
}