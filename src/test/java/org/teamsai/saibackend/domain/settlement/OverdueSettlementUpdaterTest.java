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
import org.teamsai.saibackend.domain.settlement.service.OverdueSettlementUpdater;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueSettlementUpdaterTest {

    @Mock private SettlementParticipantMapper participantMapper;
    @Mock private PaymentObligationMapper paymentObligationMapper;

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
    @DisplayName("ACTIVE 참여자의 미납 obligation에 overdueSince를 주어진 referenceDate로 채운다")
    void updatesOverdueSinceUsingGivenReferenceDate() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 1);
        SettlementDTO settlement = settlement(1L);

        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.ACTIVE),
                participant(102L, SettlementParticipantStatus.LEFT)
        ));
        when(paymentObligationMapper.findUnpaidByParticipantIds(List.of(101L))).thenReturn(List.of(
                PaymentObligationDTO.builder().paymentObligationId(9001L).build(),
                PaymentObligationDTO.builder().paymentObligationId(9002L).build()
        ));

        sut.updateOverdueForSettlement(settlement, referenceDate);

        verify(paymentObligationMapper).updateOverdueSince(9001L, referenceDate.atStartOfDay());
        verify(paymentObligationMapper).updateOverdueSince(9002L, referenceDate.atStartOfDay());
        verify(paymentObligationMapper).findUnpaidByParticipantIds(List.of(101L));
    }

    @Test
    @DisplayName("ACTIVE 참여자가 없으면 obligation 조회 자체를 하지 않는다")
    void skipsWhenNoActiveParticipants() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 1);
        SettlementDTO settlement = settlement(1L);

        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.LEFT)
        ));

        sut.updateOverdueForSettlement(settlement, referenceDate);

        verify(paymentObligationMapper, never()).findUnpaidByParticipantIds(any());
    }

    @Test
    @DisplayName("미납 obligation이 없으면 갱신할 것도 없다")
    void doesNothingWhenNoUnpaidObligations() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 1);
        SettlementDTO settlement = settlement(1L);

        when(participantMapper.findBySettlementId(1L)).thenReturn(List.of(
                participant(101L, SettlementParticipantStatus.ACTIVE)
        ));
        when(paymentObligationMapper.findUnpaidByParticipantIds(List.of(101L))).thenReturn(List.of());

        sut.updateOverdueForSettlement(settlement, referenceDate);

        verify(paymentObligationMapper, never()).updateOverdueSince(any(), any());
    }
}