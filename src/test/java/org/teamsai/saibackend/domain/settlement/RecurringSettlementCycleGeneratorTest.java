package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementCycleGenerator;
import org.teamsai.saibackend.domain.settlement.type.*;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringSettlementCycleGeneratorTest {

    @Mock private SettlementMapper settlementMapper;
    @Mock private SettlementParticipantMapper participantMapper;
    @Mock private PaymentObligationMapper paymentObligationMapper;
    @Mock private SettlementPaymentService settlementPaymentService;

    @InjectMocks
    private RecurringSettlementCycleGenerator sut; // system under test

    private RecurringSettlementDTO recurring(CycleRule cycleRule) {
        return RecurringSettlementDTO.builder()
                .recurringSettlementId(1L)
                .ownerId(100L)
                .settlementCategory("월세")
                .title("자취방 월세")
                .splitType(SplitType.EQUAL)
                .totalAmount(BigDecimal.valueOf(300000))
                .cycleRule(cycleRule)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(null)
                .build();
    }

    private SettlementDTO latestSettlement(LocalDate cycleDate) {
        return SettlementDTO.builder()
                .settlementId(10L)
                .recurringSettlementId(1L)
                .cycleDate(cycleDate)
                .build();
    }

    private SettlementParticipantDTO activeParticipant(Long participantId, Long userId) {
        return SettlementParticipantDTO.builder()
                .participantId(participantId)
                .userId(userId)
                .settlementId(10L)
                .participantRole(SettlementParticipantRole.MEMBER)
                .participantStatus(SettlementParticipantStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("직전 회차/참여자 존재 여부에 따른 스킵 처리")
    class SkipCases {

        @Test
        @DisplayName("직전 회차가 없으면 생성하지 않고 조용히 종료한다")
        void skipWhenNoLatestSettlement() {
            RecurringSettlementDTO recurring = recurring(CycleRule.MONTHLY);
            when(settlementMapper.findLatestByRecurringId(1L)).thenReturn(null);

            sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 1));

            verify(settlementMapper, never()).insertSettlement(any());
            verify(participantMapper, never()).insert(any());
        }

        @Test
        @DisplayName("오늘이 생성 주기가 아니면 생성하지 않는다")
        void skipWhenNotDueToday() {
            RecurringSettlementDTO recurring = recurring(CycleRule.MONTHLY);
            when(settlementMapper.findLatestByRecurringId(1L))
                    .thenReturn(latestSettlement(LocalDate.of(2026, 2, 1)));

            // 같은 달 15일 - 아직 다음 회차 도래 전
            sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 15));

            verify(settlementMapper, never()).insertSettlement(any());
        }

        @Test
        @DisplayName("ACTIVE 참여자가 없으면 생성하지 않는다")
        void skipWhenNoActiveParticipants() {
            RecurringSettlementDTO recurring = recurring(CycleRule.DAILY);
            when(settlementMapper.findLatestByRecurringId(1L))
                    .thenReturn(latestSettlement(LocalDate.of(2026, 2, 1)));
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(
                    SettlementParticipantDTO.builder()
                            .participantId(1L)
                            .participantStatus(SettlementParticipantStatus.LEFT)
                            .build()
            ));

            sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 2));

            verify(settlementMapper, never()).insertSettlement(any());
        }
    }

    @Nested
    @DisplayName("CycleRule별 생성일 도래 판정")
    class DueDateJudgement {

        @Test
        @DisplayName("DAILY - 직전 회차와 날짜가 다르면 도래한 것으로 본다")
        void dailyIsDueWhenDateDiffers() {
            RecurringSettlementDTO recurring = recurring(CycleRule.DAILY);
            when(settlementMapper.findLatestByRecurringId(1L))
                    .thenReturn(latestSettlement(LocalDate.of(2026, 2, 1)));
            when(participantMapper.findBySettlementId(10L))
                    .thenReturn(List.of(activeParticipant(1L, 100L)));
            when(paymentObligationMapper.findByParticipantId(1L))
                    .thenReturn(Optional.of(PaymentObligationDTO.builder()
                            .expectedAmount(BigDecimal.valueOf(150000)).build()));
            when(settlementMapper.insertSettlement(any())).thenAnswer(inv -> {
                SettlementDTO s = inv.getArgument(0);
                s.setSettlementId(20L); // setter 없으면 @Builder.Default 등으로 대체 필요
                return 1;
            });
            when(participantMapper.insert(any())).thenReturn(1);

            sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 2));

            verify(settlementMapper).insertSettlement(any());
        }

        @Test
        @DisplayName("MONTHLY - 같은 일자가 아니면 도래하지 않은 것으로 본다")
        void monthlyNotDueOnDifferentDay() {
            RecurringSettlementDTO recurring = recurring(CycleRule.MONTHLY);
            when(settlementMapper.findLatestByRecurringId(1L))
                    .thenReturn(latestSettlement(LocalDate.of(2026, 1, 1)));

            sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 2)); // 1일이 아니라 2일

            verify(settlementMapper, never()).insertSettlement(any());
        }

        @Test
        @DisplayName("MONTHLY - 같은 일자, 한 달 이상 지났으면 도래한 것으로 본다")
        void monthlyDueOnSameDayNextMonth() {
            RecurringSettlementDTO recurring = recurring(CycleRule.MONTHLY);
            when(settlementMapper.findLatestByRecurringId(1L))
                    .thenReturn(latestSettlement(LocalDate.of(2026, 1, 1)));
            when(participantMapper.findBySettlementId(10L))
                    .thenReturn(List.of(activeParticipant(1L, 100L)));
            when(paymentObligationMapper.findByParticipantId(1L))
                    .thenReturn(Optional.of(PaymentObligationDTO.builder()
                            .expectedAmount(BigDecimal.valueOf(150000)).build()));
            when(settlementMapper.insertSettlement(any())).thenReturn(1);
            when(participantMapper.insert(any())).thenReturn(1);

            sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 1));

            verify(settlementMapper).insertSettlement(any());
        }
    }

    @Nested
    @DisplayName("정상 생성 시 참여자/납부의무 복사")
    class HappyPath {

        @Test
        @DisplayName("ACTIVE 참여자만 복사하고, 각 참여자의 직전 회차 expectedAmount로 납부의무를 생성한다")
        void copiesOnlyActiveParticipantsWithObligation() {
            RecurringSettlementDTO recurring = recurring(CycleRule.DAILY);
            when(settlementMapper.findLatestByRecurringId(1L))
                    .thenReturn(latestSettlement(LocalDate.of(2026, 2, 1)));
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(
                    activeParticipant(1L, 100L),
                    activeParticipant(2L, 200L),
                    SettlementParticipantDTO.builder()
                            .participantId(3L).userId(300L)
                            .participantStatus(SettlementParticipantStatus.REMOVED)
                            .build()
            ));
            when(settlementMapper.insertSettlement(any())).thenReturn(1);
            when(participantMapper.insert(any())).thenReturn(1);
            when(paymentObligationMapper.findByParticipantId(1L))
                    .thenReturn(Optional.of(PaymentObligationDTO.builder()
                            .expectedAmount(BigDecimal.valueOf(150000)).build()));
            when(paymentObligationMapper.findByParticipantId(2L))
                    .thenReturn(Optional.of(PaymentObligationDTO.builder()
                            .expectedAmount(BigDecimal.valueOf(150000)).build()));

            sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 2));

            // REMOVED(participantId=3)는 복사 대상에서 제외되어 2번만 insert
            verify(participantMapper, times(2)).insert(any());
            verify(settlementPaymentService, times(2)).createObligation(any(), eq(BigDecimal.valueOf(150000)));
            verify(paymentObligationMapper, never()).findByParticipantId(3L);
        }
    }

    @Nested
    @DisplayName("예외 상황")
    class ExceptionCases {

        @Test
        @DisplayName("Settlement insert 실패 시 SETTLEMENT_CREATE_FAILED 예외를 던진다")
        void throwsWhenSettlementInsertFails() {
            RecurringSettlementDTO recurring = recurring(CycleRule.DAILY);
            when(settlementMapper.findLatestByRecurringId(1L))
                    .thenReturn(latestSettlement(LocalDate.of(2026, 2, 1)));
            when(participantMapper.findBySettlementId(10L))
                    .thenReturn(List.of(activeParticipant(1L, 100L)));
            when(settlementMapper.insertSettlement(any())).thenReturn(0); // 실패

            assertThatThrownBy(() -> sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 2)))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_CREATE_FAILED);
        }

        @Test
        @DisplayName("직전 회차 납부의무가 없으면 PAYMENT_OBLIGATION_NOT_FOUND 예외를 던진다")
        void throwsWhenPreviousObligationMissing() {
            RecurringSettlementDTO recurring = recurring(CycleRule.DAILY);
            when(settlementMapper.findLatestByRecurringId(1L))
                    .thenReturn(latestSettlement(LocalDate.of(2026, 2, 1)));
            when(participantMapper.findBySettlementId(10L))
                    .thenReturn(List.of(activeParticipant(1L, 100L)));
            when(settlementMapper.insertSettlement(any())).thenReturn(1);
            when(participantMapper.insert(any())).thenReturn(1);
            when(paymentObligationMapper.findByParticipantId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.generateNextCycle(recurring, LocalDate.of(2026, 2, 2)))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_FOUND);
        }
    }
}