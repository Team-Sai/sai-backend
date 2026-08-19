package org.teamsai.saibackend.domain.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.notification.type.ReminderStage;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.OverdueCriteria;
import org.teamsai.saibackend.domain.settlement.service.SettlementDueReminderService;
import org.teamsai.saibackend.domain.settlement.service.SettlementReminderResult;
import org.teamsai.saibackend.domain.settlement.service.SettlementReminderSender;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementDueReminderServiceTest {

    @Mock
    private OverdueCriteria overdueCriteria;
    @Mock
    private SettlementMapper settlementMapper;
    @Mock
    private SettlementParticipantMapper participantMapper;
    @Mock
    private PaymentObligationMapper paymentObligationMapper;
    @Mock
    private SettlementReminderSender reminderSender;

    @InjectMocks
    private SettlementDueReminderService service;

    private final LocalDate baseDate = LocalDate.of(2026, 8, 19);

    private SettlementDTO settlement(Long id) {
        SettlementDTO dto = mock(SettlementDTO.class);
        lenient().when(dto.getSettlementId()).thenReturn(id);
        return dto;
    }

    @Nested
    class NoTargets {

        @Test
        void 대상_정산이_없으면_0_0을_반환한다() {
            when(settlementMapper.countInProgressSettlements()).thenReturn(0);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(Collections.emptyList());

            SettlementReminderResult result = service.sendDueReminders(baseDate);

            assertThat(result.processedCount()).isZero();
            assertThat(result.failedCount()).isZero();
            verifyNoInteractions(reminderSender);
        }
    }

    @Nested
    class StageResolution {

        @Test
        void referenceDate가_null이면_리마인드를_보내지_않는다() {
            SettlementDTO s = settlement(1L);
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(null);

            SettlementReminderResult result = service.sendDueReminders(baseDate);

            assertThat(result.processedCount()).isZero();
            verifyNoInteractions(reminderSender);
        }

        @Test
        void D_3일때_D3_스테이지로_발송한다() {
            SettlementDTO s = settlement(1L);
            LocalDate referenceDate = baseDate.plusDays(3);
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(referenceDate);
            when(reminderSender.sendForSettlement(s, ReminderStage.D3)).thenReturn(2);

            SettlementReminderResult result = service.sendDueReminders(baseDate);

            assertThat(result.processedCount()).isEqualTo(2);
            verify(reminderSender).sendForSettlement(s, ReminderStage.D3);
        }

        @Test
        void D_1일때_D1_스테이지로_발송한다() {
            SettlementDTO s = settlement(1L);
            LocalDate referenceDate = baseDate.plusDays(1);
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(referenceDate);
            when(reminderSender.sendForSettlement(s, ReminderStage.D1)).thenReturn(1);

            SettlementReminderResult result = service.sendDueReminders(baseDate);

            assertThat(result.processedCount()).isEqualTo(1);
            verify(reminderSender).sendForSettlement(s, ReminderStage.D1);
        }

        @Test
        void 당일이면_DDAY_스테이지로_발송한다() {
            SettlementDTO s = settlement(1L);
            LocalDate referenceDate = baseDate;
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(referenceDate);
            when(reminderSender.sendForSettlement(s, ReminderStage.DDAY)).thenReturn(3);

            SettlementReminderResult result = service.sendDueReminders(baseDate);

            assertThat(result.processedCount()).isEqualTo(3);
            verify(reminderSender).sendForSettlement(s, ReminderStage.DDAY);
        }

        @Test
        void D3_D1_DDAY에_해당하지_않으면_발송하지_않는다() {
            SettlementDTO s = settlement(1L);
            LocalDate referenceDate = baseDate.plusDays(5); // D-5, 대상 아님
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(referenceDate);

            SettlementReminderResult result = service.sendDueReminders(baseDate);

            assertThat(result.processedCount()).isZero();
            verifyNoInteractions(reminderSender);
        }

        @Test
        void 이미_지난_기준일이면_발송하지_않는다() {
            SettlementDTO s = settlement(1L);
            LocalDate referenceDate = baseDate.minusDays(1); // 과거, default case
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(referenceDate);

            SettlementReminderResult result = service.sendDueReminders(baseDate);

            assertThat(result.processedCount()).isZero();
            verifyNoInteractions(reminderSender);
        }
    }

    @Nested
    class FailureHandling {

        @Test
        void 특정_정산_발송_실패해도_나머지는_계속_처리하고_failedCount에_반영된다() {
            SettlementDTO s1 = settlement(1L);
            SettlementDTO s2 = settlement(2L);
            LocalDate referenceDate = baseDate; // DDAY

            when(settlementMapper.countInProgressSettlements()).thenReturn(2);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s1, s2));
            when(overdueCriteria.resolveReferenceDate(s1)).thenReturn(referenceDate);
            when(overdueCriteria.resolveReferenceDate(s2)).thenReturn(referenceDate);

            when(reminderSender.sendForSettlement(s1, ReminderStage.DDAY))
                    .thenThrow(new RuntimeException("발송 실패"));
            when(reminderSender.sendForSettlement(s2, ReminderStage.DDAY)).thenReturn(1);

            SettlementReminderResult result = service.sendDueReminders(baseDate);

            assertThat(result.processedCount()).isEqualTo(1);
            assertThat(result.failedCount()).isEqualTo(1);
            verify(reminderSender).sendForSettlement(s1, ReminderStage.DDAY);
            verify(reminderSender).sendForSettlement(s2, ReminderStage.DDAY);
        }
    }

    @Nested
    class Pagination {

        @Test
        void 마지막_페이지가_PAGE_SIZE보다_작으면_추가조회하지_않는다() {
            SettlementDTO s = settlement(1L);
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(null);

            service.sendDueReminders(baseDate);

            verify(settlementMapper, times(1)).findInProgressSettlements(anyInt(), anyInt());
        }
    }
}
