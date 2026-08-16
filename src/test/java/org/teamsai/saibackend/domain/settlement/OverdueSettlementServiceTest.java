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

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.eq;
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

        when(settlementMapper.findInProgressSettlements(0, 200)).thenReturn(List.of(overdue, notOverdue));
        when(overdueCriteria.isOverdue(overdue, baseDate)).thenReturn(true);
        when(overdueCriteria.isOverdue(notOverdue, baseDate)).thenReturn(false);

        sut.updateOverdueStatus(baseDate);

        verify(overdueSettlementUpdater).updateOverdueForSettlement(overdue, baseDate);
        verify(overdueSettlementUpdater, never()).updateOverdueForSettlement(eq(notOverdue), eq(baseDate));
    }

    @Test
    @DisplayName("한 정산 갱신이 실패해도 나머지 정산은 계속 처리된다")
    void continuesProcessingWhenOneUpdateFails() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        SettlementDTO s1 = settlement(1L);
        SettlementDTO s2 = settlement(2L);

        when(settlementMapper.findInProgressSettlements(0, 200)).thenReturn(List.of(s1, s2));
        when(overdueCriteria.isOverdue(s1, baseDate)).thenReturn(true);
        when(overdueCriteria.isOverdue(s2, baseDate)).thenReturn(true);
        doThrow(new IllegalStateException("갱신 실패"))
                .when(overdueSettlementUpdater).updateOverdueForSettlement(s1, baseDate);

        sut.updateOverdueStatus(baseDate);

        verify(overdueSettlementUpdater).updateOverdueForSettlement(s1, baseDate);
        verify(overdueSettlementUpdater).updateOverdueForSettlement(s2, baseDate); // s1 실패와 무관하게 호출됨
    }

    @Test
    @DisplayName("첫 페이지가 PAGE_SIZE 미만이면 다음 페이지를 조회하지 않고 종료한다")
    void stopsWhenFirstPageIsPartial() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        List<SettlementDTO> partialPage = List.of(settlement(1L), settlement(2L)); // 200개 미만

        when(settlementMapper.findInProgressSettlements(0, 200)).thenReturn(partialPage);
        when(overdueCriteria.isOverdue(any(), eq(baseDate))).thenReturn(false);

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
        when(overdueCriteria.isOverdue(any(), eq(baseDate))).thenReturn(false);

        sut.updateOverdueStatus(baseDate);

        verify(settlementMapper).findInProgressSettlements(0, 200);
        verify(settlementMapper).findInProgressSettlements(200, 200);
        // 두 번째 페이지(1건, PAGE_SIZE 미만)에서 종료되므로 세 번째 조회는 없어야 함
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
}