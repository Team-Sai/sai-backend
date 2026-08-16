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
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.OverdueCriteria;
import org.teamsai.saibackend.domain.settlement.service.OverdueSettlementUpdater;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueSettlementUpdaterTest {

    @Mock private SettlementParticipantMapper participantMapper;
    @Mock private PaymentObligationMapper paymentObligationMapper;
    @Mock private OverdueCriteria overdueCriteria;

    @InjectMocks
    private OverdueSettlementUpdater sut;

    private SettlementDTO settlement(Long id) {
        return SettlementDTO.builder().settlementId(id).build();
    }

    private SettlementParticipantDTO participant(Long participantId, SettlementParticipantStatus status) {
        return SettlementParticipantDTO.builder()
                .participantId(participantId)
                .participantStatus(status)
                .build();
    }

    @Test
    @DisplayName("ACTIVE 참여자의 미납 obligation에 overdueSince를 실제 기준일(dueDate)로 채운다")
    void updatesOverdueSinceUsingReferenceDate() {
        LocalDate baseDate = LocalDate.of(2026, 2, 5); // 배치가 며칠 밀려서 실행됨
        LocalDate actualDueDate = LocalDate.of(2026, 2, 1); // 실제 만기일
        SettlementDTO settlement = SettlementDTO.builder()
                .settlementId(1L)
                .settlementType(SettlementType.SHARED)
                .dueDate(actualDueDate)
                .build();

        when(overdueCriteria.resolveReferenceDate(settlement)).thenReturn(actualDueDate);
        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.ACTIVE)
        ));
        when(paymentObligationMapper.findUnpaidByParticipantIds(List.of(101L))).thenReturn(List.of(
                PaymentObligationDTO.builder().paymentObligationId(9001L).build()
        ));
        when(paymentObligationMapper.updateOverdueSince(9001L, actualDueDate.atStartOfDay())).thenReturn(1);

        sut.updateOverdueForSettlement(settlement, baseDate);

        // baseDate(2/5)가 아니라 actualDueDate(2/1)로 기록되는지 검증
        verify(paymentObligationMapper).updateOverdueSince(9001L, actualDueDate.atStartOfDay());
    }

    @Test
    @DisplayName("ACTIVE 참여자가 없으면 obligation 조회 자체를 하지 않는다")
    void skipsWhenNoActiveParticipants() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        SettlementDTO settlement = settlement(1L);

        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.LEFT)
        ));

        sut.updateOverdueForSettlement(settlement, baseDate);

        verify(paymentObligationMapper, never()).findUnpaidByParticipantIds(any());
    }

    @Test
    @DisplayName("미납 obligation이 없으면 갱신할 것도 없다")
    void doesNothingWhenNoUnpaidObligations() {
        LocalDate baseDate = LocalDate.of(2026, 2, 1);
        SettlementDTO settlement = settlement(1L);

        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.ACTIVE)
        ));
        when(paymentObligationMapper.findUnpaidByParticipantIds(List.of(101L))).thenReturn(List.of());

        sut.updateOverdueForSettlement(settlement, baseDate);

        verify(paymentObligationMapper, never()).updateOverdueSince(any(), any());
    }
}