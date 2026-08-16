package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.OverdueCriteria;
import org.teamsai.saibackend.domain.settlement.service.OverdueSettlementService;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueSettlementServiceTest {

    @Mock private OverdueCriteria overdueCriteria;
    @Mock private SettlementMapper settlementMapper;
    @Mock private SettlementParticipantMapper participantMapper;
    @Mock private PaymentObligationMapper paymentObligationMapper;

    @InjectMocks
    private OverdueSettlementService sut;

    private SettlementDTO settlement(Long id, SettlementType type) {
        return SettlementDTO.builder()
                .settlementId(id)
                .settlementType(type)
                .settlementStatus(SettlementStatus.IN_PROGRESS)
                .build();
    }

    private SettlementParticipantDTO participant(Long participantId, SettlementParticipantStatus status) {
        return SettlementParticipantDTO.builder()
                .participantId(participantId)
                .participantStatus(status)
                .build();
    }

    @Test
    @DisplayName("연체로 판정된 정산만 참여자를 조회하고, 판정 안 된 정산은 건드리지 않는다")
    void onlyProcessesOverdueSettlements() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        SettlementDTO overdue = settlement(1L, SettlementType.SHARED);
        SettlementDTO notOverdue = settlement(2L, SettlementType.SHARED);

        when(settlementMapper.findInProgressSettlements()).thenReturn(List.of(overdue, notOverdue));
        when(overdueCriteria.isOverdue(overdue, baseDate)).thenReturn(true);
        when(overdueCriteria.isOverdue(notOverdue, baseDate)).thenReturn(false);
        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.ACTIVE)
        ));
        when(paymentObligationMapper.findUnpaidByParticipantIds(List.of(101L))).thenReturn(List.of());

        sut.updateOverdueStatus(baseDate);

        verify(participantMapper).findBySettlementId(1L);
        verify(participantMapper, never()).findBySettlementId(2L);
    }

    @Test
    @DisplayName("ACTIVE 참여자의 미납 obligation에 overdueSince를 baseDate 자정으로 채운다")
    void updatesOverdueSinceForUnpaidObligations() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        SettlementDTO overdue = settlement(1L, SettlementType.SHARED);

        when(settlementMapper.findInProgressSettlements()).thenReturn(List.of(overdue));
        when(overdueCriteria.isOverdue(overdue, baseDate)).thenReturn(true);
        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.ACTIVE),
                participant(102L, SettlementParticipantStatus.LEFT) // 제외되어야 함
        ));
        when(paymentObligationMapper.findUnpaidByParticipantIds(List.of(101L))).thenReturn(List.of(
                PaymentObligationDTO.builder().paymentObligationId(9001L).build(),
                PaymentObligationDTO.builder().paymentObligationId(9002L).build()
        ));

        sut.updateOverdueStatus(baseDate);

        LocalDateTime expectedOverdueSince = baseDate.atStartOfDay();
        verify(paymentObligationMapper).updateOverdueSince(9001L, expectedOverdueSince);
        verify(paymentObligationMapper).updateOverdueSince(9002L, expectedOverdueSince);
        // LEFT 참여자는 findUnpaidByParticipantIds 호출 시 애초에 포함 안 됨
        verify(paymentObligationMapper).findUnpaidByParticipantIds(List.of(101L));
    }

    @Test
    @DisplayName("ACTIVE 참여자가 없으면 obligation 조회 자체를 하지 않는다")
    void skipsWhenNoActiveParticipants() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        SettlementDTO overdue = settlement(1L, SettlementType.SHARED);

        when(settlementMapper.findInProgressSettlements()).thenReturn(List.of(overdue));
        when(overdueCriteria.isOverdue(overdue, baseDate)).thenReturn(true);
        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.LEFT)
        ));

        sut.updateOverdueStatus(baseDate);

        verify(paymentObligationMapper, never()).findUnpaidByParticipantIds(any());
    }

    @Test
    @DisplayName("연체 대상 정산이 없으면 아무것도 조회하지 않는다")
    void doesNothingWhenNoSettlements() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        when(settlementMapper.findInProgressSettlements()).thenReturn(List.of());

        sut.updateOverdueStatus(baseDate);

        verify(participantMapper, never()).findBySettlementId(any());
        verify(paymentObligationMapper, never()).updateOverdueSince(any(), any());
    }
}