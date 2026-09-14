package org.teamsai.saibackend.domain.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.ReminderStage;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementReminderSender;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementReminderSenderTest {

    @Mock
    private SettlementParticipantMapper participantMapper;
    @Mock
    private PaymentObligationMapper paymentObligationMapper;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SettlementReminderSender sender;

    private SettlementDTO settlement(Long id) {
        SettlementDTO dto = mock(SettlementDTO.class);
        lenient().when(dto.getSettlementId()).thenReturn(id);
        return dto;
    }

    private SettlementParticipantDTO participant(Long participantId, Long userId) {
        SettlementParticipantDTO dto = mock(SettlementParticipantDTO.class);
        lenient().when(dto.getParticipantId()).thenReturn(participantId);
        lenient().when(dto.getUserId()).thenReturn(userId);
        return dto;
    }

    private PaymentObligationDTO obligation(Long obligationId, Long participantId, PaymentStatus status, boolean unresolved) {
        PaymentObligationDTO dto = mock(PaymentObligationDTO.class);
        lenient().when(dto.getPaymentObligationId()).thenReturn(obligationId);
        lenient().when(dto.getParticipantId()).thenReturn(participantId);
        PaymentStatus mockedStatus = status != null ? status : mock(PaymentStatus.class);
        lenient().when(dto.getPaymentStatus()).thenReturn(mockedStatus);
        return dto;
    }

    @Nested
    class NoActiveParticipants {

        @Test
        void 활성_참여자가_없으면_0을_반환하고_아무것도_조회하지_않는다() {
            SettlementDTO s = settlement(1L);
            when(participantMapper.findActiveBySettlementId(1L)).thenReturn(Collections.emptyList());

            int result = sender.sendForSettlement(s, ReminderStage.D3);

            assertThat(result).isZero();
            verifyNoInteractions(paymentObligationMapper, notificationService);
        }
    }

    @Nested
    class ObligationFiltering {

        @Test
        void 미해결_의무만_필터링해서_리마인드를_보낸다() {
            SettlementDTO s = settlement(1L);
            SettlementParticipantDTO p1 = participant(10L, 100L);
            when(participantMapper.findActiveBySettlementId(1L)).thenReturn(List.of(p1));

            PaymentObligationDTO unresolvedOb = mock(PaymentObligationDTO.class);
            when(unresolvedOb.getParticipantId()).thenReturn(10L);
            when(unresolvedOb.getPaymentObligationId()).thenReturn(500L);
            PaymentStatus unresolvedStatus = mock(PaymentStatus.class);
            when(unresolvedStatus.isUnresolved()).thenReturn(true);
            when(unresolvedOb.getPaymentStatus()).thenReturn(unresolvedStatus);

            PaymentObligationDTO resolvedOb = mock(PaymentObligationDTO.class);
            PaymentStatus resolvedStatus = mock(PaymentStatus.class);
            when(resolvedStatus.isUnresolved()).thenReturn(false);
            when(resolvedOb.getPaymentStatus()).thenReturn(resolvedStatus);

            when(paymentObligationMapper.findByParticipantIds(List.of(10L)))
                    .thenReturn(List.of(unresolvedOb, resolvedOb));

            int result = sender.sendForSettlement(s, ReminderStage.D3);

            assertThat(result).isEqualTo(1);
            verify(notificationService).createIfAbsent(
                    eq(100L), any(), any(), any(), eq(500L), eq(1L)
            );
        }
    }

    @Nested
    class UserMappingFailure {

        @Test
        void participantId에_매핑되는_userId가_없으면_스킵하고_카운트하지_않는다() {
            SettlementDTO s = settlement(1L);
            SettlementParticipantDTO p1 = participant(10L, 100L);
            when(participantMapper.findActiveBySettlementId(1L)).thenReturn(List.of(p1));

            // obligation의 participantId가 activeParticipants에 없는 20L (매핑 실패 상황)
            PaymentObligationDTO orphanOb = mock(PaymentObligationDTO.class);
            when(orphanOb.getParticipantId()).thenReturn(20L);
            PaymentStatus unresolvedStatus = mock(PaymentStatus.class);
            when(unresolvedStatus.isUnresolved()).thenReturn(true);
            when(orphanOb.getPaymentStatus()).thenReturn(unresolvedStatus);

            when(paymentObligationMapper.findByParticipantIds(List.of(10L)))
                    .thenReturn(List.of(orphanOb));

            int result = sender.sendForSettlement(s, ReminderStage.D3);

            assertThat(result).isZero();
            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    class PartialFailure {

        @Test
        void 특정_알림_발송_실패해도_나머지는_계속_처리한다() {
            SettlementDTO s = settlement(1L);
            SettlementParticipantDTO p1 = participant(10L, 100L);
            SettlementParticipantDTO p2 = participant(11L, 101L);
            when(participantMapper.findActiveBySettlementId(1L)).thenReturn(List.of(p1, p2));

            PaymentObligationDTO ob1 = mock(PaymentObligationDTO.class);
            when(ob1.getParticipantId()).thenReturn(10L);
            when(ob1.getPaymentObligationId()).thenReturn(500L);
            PaymentStatus status1 = mock(PaymentStatus.class);
            when(status1.isUnresolved()).thenReturn(true);
            when(ob1.getPaymentStatus()).thenReturn(status1);

            PaymentObligationDTO ob2 = mock(PaymentObligationDTO.class);
            when(ob2.getParticipantId()).thenReturn(11L);
            when(ob2.getPaymentObligationId()).thenReturn(501L);
            PaymentStatus status2 = mock(PaymentStatus.class);
            when(status2.isUnresolved()).thenReturn(true);
            when(ob2.getPaymentStatus()).thenReturn(status2);

            when(paymentObligationMapper.findByParticipantIds(List.of(10L, 11L)))
                    .thenReturn(List.of(ob1, ob2));

            doThrow(new RuntimeException("알림 발송 실패"))
                    .when(notificationService).createIfAbsent(
                            eq(100L), any(), any(), any(), eq(500L), eq(1L)
                    );

            int result = sender.sendForSettlement(s, ReminderStage.D3);

            // ob1은 실패, ob2는 성공 -> sent = 1
            assertThat(result).isEqualTo(1);
            verify(notificationService).createIfAbsent(eq(100L), any(), any(), any(), eq(500L), eq(1L));
            verify(notificationService).createIfAbsent(eq(101L), any(), any(), any(), eq(501L), eq(1L));
        }
    }
}