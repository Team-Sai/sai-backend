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
import org.teamsai.saibackend.domain.settlement.service.SettlementAmountCalculator;
import org.teamsai.saibackend.domain.settlement.type.*;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringSettlementCycleGeneratorTest {

    @Mock private SettlementMapper settlementMapper;
    @Mock private SettlementParticipantMapper participantMapper;
    @Mock private PaymentObligationMapper paymentObligationMapper;
    @Mock private SettlementPaymentService settlementPaymentService;
    @Mock private SettlementAmountCalculator settlementAmountCalculator;

    @InjectMocks
    private RecurringSettlementCycleGenerator sut;

    private RecurringSettlementDTO recurring(SplitType splitType) {
        return RecurringSettlementDTO.builder()
                .recurringSettlementId(1L)
                .ownerId(100L)
                .settlementCategory("월세")
                .title("자취방 월세")
                .splitType(splitType)
                .totalAmount(BigDecimal.valueOf(300000))
                .cycleRule(CycleRule.MONTHLY)
                .startDate(LocalDate.of(2026, 1, 31))
                .endDate(null)
                .build();
    }

    private SettlementDTO previousSettlement() {
        return SettlementDTO.builder()
                .settlementId(10L)
                .recurringSettlementId(1L)
                .cycleDate(LocalDate.of(2026, 1, 31))
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

    private PaymentObligationDTO obligation(Long obligationId, Long participantId, BigDecimal expectedAmount) {
        return PaymentObligationDTO.builder()
                .paymentObligationId(obligationId)
                .participantId(participantId)
                .expectedAmount(expectedAmount)
                .build();
    }

    @Nested
    @DisplayName("동시성 락 검증")
    class LockValidation {

        @Test
        @DisplayName("락 획득 시점의 직전 회차가 넘겨받은 previousSettlement와 다르면 null을 반환하고 아무것도 생성하지 않는다")
        void returnsNullWhenConcurrentGenerationDetected() {
            RecurringSettlementDTO recurring = recurring(SplitType.EQUAL);
            SettlementDTO previous = previousSettlement();

            SettlementDTO alreadyCreatedByOther = SettlementDTO.builder()
                    .settlementId(11L)
                    .recurringSettlementId(1L)
                    .cycleDate(LocalDate.of(2026, 2, 28))
                    .build();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L))
                    .thenReturn(alreadyCreatedByOther);

            SettlementDTO result = sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28));

            assertThat(result).isNull();
            verify(settlementMapper, never()).insertSettlement(any());
            verify(participantMapper, never()).findBySettlementId(any());
        }

        @Test
        @DisplayName("락 획득 시점의 직전 회차가 null이면 null을 반환한다")
        void returnsNullWhenLockedLatestIsNull() {
            RecurringSettlementDTO recurring = recurring(SplitType.EQUAL);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(null);

            SettlementDTO result = sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28));

            assertThat(result).isNull();
            verify(settlementMapper, never()).insertSettlement(any());
        }
    }

    @Nested
    @DisplayName("ACTIVE 참여자 존재 여부")
    class ActiveParticipantCheck {

        @Test
        @DisplayName("ACTIVE 참여자가 없으면 null을 반환하고 생성하지 않는다")
        void returnsNullWhenNoActiveParticipants() {
            RecurringSettlementDTO recurring = recurring(SplitType.EQUAL);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(previous);
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(
                    SettlementParticipantDTO.builder()
                            .participantId(1L)
                            .participantStatus(SettlementParticipantStatus.LEFT)
                            .build()
            ));

            SettlementDTO result = sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28));

            assertThat(result).isNull();
            verify(settlementMapper, never()).insertSettlement(any());
        }
    }

    @Nested
    @DisplayName("EQUAL 분할 재계산")
    class EqualSplitRecalculation {

        @Test
        @DisplayName("EQUAL이면 직전 회차 금액이 아니라 현재 ACTIVE 참여자 수(전체 인원) 기준으로 재계산한다")
        void recalculatesEqualAmountByCurrentActiveCount() {
            RecurringSettlementDTO recurring = recurring(SplitType.EQUAL);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(previous);
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(
                    activeParticipant(1L, 100L)
            ));
            when(settlementMapper.insertSettlement(any())).thenReturn(1);
            when(participantMapper.insert(any())).thenReturn(1);
            when(settlementAmountCalculator.calculateEqualAmountForTotalCount(BigDecimal.valueOf(300000), 1))
                    .thenReturn(BigDecimal.valueOf(300000));

            SettlementDTO result = sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28));

            assertThat(result).isNotNull();
            verify(settlementAmountCalculator).calculateEqualAmountForTotalCount(BigDecimal.valueOf(300000), 1);
            verify(settlementPaymentService).createObligation(any(), eq(BigDecimal.valueOf(300000)));
            // EQUAL이면 배치 조회 자체를 안 해야 함 (N+1 제거 후에도 EQUAL 경로는 조회 불필요)
            verify(paymentObligationMapper, never()).findByParticipantIds(any());
        }

        @Test
        @DisplayName("EQUAL이 아니면 참여자ID 목록으로 한 번에 조회한 뒤 직전 회차의 expectedAmount를 그대로 유지한다")
        void keepsPreviousAmountWhenNotEqual() {
            RecurringSettlementDTO recurring = recurring(SplitType.CUSTOM);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(previous);
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(
                    activeParticipant(1L, 100L)
            ));
            when(settlementMapper.insertSettlement(any())).thenReturn(1);
            when(participantMapper.insert(any())).thenReturn(1);
            when(paymentObligationMapper.findByParticipantIds(List.of(1L)))
                    .thenReturn(List.of(obligation(500L, 1L, BigDecimal.valueOf(150000))));

            SettlementDTO result = sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28));

            assertThat(result).isNotNull();
            verify(settlementPaymentService).createObligation(any(), eq(BigDecimal.valueOf(150000)));
            verify(settlementAmountCalculator, never()).calculateEqualAmountForTotalCount(any(), anyInt());
            verify(paymentObligationMapper, times(1)).findByParticipantIds(any());
        }

        @Test
        @DisplayName("같은 참여자에게 obligation이 여러 건 있어도, 쿼리는 이미 최신 것 하나만 반환한다고 가정하고 그 값을 사용한다")
        void usesTheSingleObligationReturnedByQuery() {
            RecurringSettlementDTO recurring = recurring(SplitType.CUSTOM);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(previous);
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(
                    activeParticipant(1L, 100L)
            ));
            when(settlementMapper.insertSettlement(any())).thenReturn(1);
            when(participantMapper.insert(any())).thenReturn(1);
            when(paymentObligationMapper.findByParticipantIds(List.of(1L)))
                    .thenReturn(List.of(obligation(600L, 1L, BigDecimal.valueOf(150000))));

            sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28));

            verify(settlementPaymentService).createObligation(any(), eq(BigDecimal.valueOf(150000)));
        }

        @Test
        @DisplayName("CUSTOM인데 직전 회차 납부의무가 없으면 PAYMENT_OBLIGATION_NOT_FOUND 예외를 던진다")
        void throwsWhenCustomAndPreviousObligationMissing() {
            RecurringSettlementDTO recurring = recurring(SplitType.CUSTOM);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(previous);
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(
                    activeParticipant(1L, 100L)
            ));
            when(settlementMapper.insertSettlement(any())).thenReturn(1);
            when(participantMapper.insert(any())).thenReturn(1);
            when(paymentObligationMapper.findByParticipantIds(List.of(1L))).thenReturn(List.of());

            assertThatThrownBy(() -> sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28)))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("정상 생성 시 참여자 복사")
    class HappyPath {

        @Test
        @DisplayName("ACTIVE 참여자만 복사하고 생성된 SettlementDTO를 반환한다")
        void copiesOnlyActiveParticipantsAndReturnsCreatedSettlement() {
            RecurringSettlementDTO recurring = recurring(SplitType.EQUAL);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(previous);
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
            when(settlementAmountCalculator.calculateEqualAmountForTotalCount(BigDecimal.valueOf(300000), 2))
                    .thenReturn(BigDecimal.valueOf(150000));

            SettlementDTO result = sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28));

            assertThat(result).isNotNull();
            assertThat(result.getCycleDate()).isEqualTo(LocalDate.of(2026, 2, 28));
            verify(participantMapper, times(2)).insert(any()); // REMOVED는 제외되어 2명만
            verify(settlementPaymentService, times(2)).createObligation(any(), eq(BigDecimal.valueOf(150000)));
        }

        @Test
        @DisplayName("CUSTOM 참여자 복수 명에 대해 배치 조회가 정확히 1번만 호출된다 (N+1 검증)")
        void batchQueriesObligationsOnceForMultipleParticipants() {
            RecurringSettlementDTO recurring = recurring(SplitType.CUSTOM);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(previous);
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(
                    activeParticipant(1L, 100L),
                    activeParticipant(2L, 200L),
                    activeParticipant(3L, 300L)
            ));
            when(settlementMapper.insertSettlement(any())).thenReturn(1);
            when(participantMapper.insert(any())).thenReturn(1);
            when(paymentObligationMapper.findByParticipantIds(List.of(1L, 2L, 3L)))
                    .thenReturn(List.of(
                            obligation(500L, 1L, BigDecimal.valueOf(100000)),
                            obligation(501L, 2L, BigDecimal.valueOf(120000)),
                            obligation(502L, 3L, BigDecimal.valueOf(80000))
                    ));

            SettlementDTO result = sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28));

            assertThat(result).isNotNull();
            verify(participantMapper, times(3)).insert(any());
            verify(paymentObligationMapper, times(1)).findByParticipantIds(any());
            verify(settlementPaymentService).createObligation(any(), eq(BigDecimal.valueOf(100000)));
            verify(settlementPaymentService).createObligation(any(), eq(BigDecimal.valueOf(120000)));
            verify(settlementPaymentService).createObligation(any(), eq(BigDecimal.valueOf(80000)));
        }

        @Test
        @DisplayName("Settlement insert 실패 시 SETTLEMENT_CREATE_FAILED 예외를 던진다")
        void throwsWhenSettlementInsertFails() {
            RecurringSettlementDTO recurring = recurring(SplitType.EQUAL);
            SettlementDTO previous = previousSettlement();
            when(settlementMapper.findLatestByRecurringIdForUpdate(1L)).thenReturn(previous);
            when(participantMapper.findBySettlementId(10L)).thenReturn(List.of(activeParticipant(1L, 100L)));
            when(settlementMapper.insertSettlement(any())).thenReturn(0);

            assertThatThrownBy(() -> sut.generateOneCycle(recurring, previous, LocalDate.of(2026, 2, 28)))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_CREATE_FAILED);
        }
    }

    @Nested
    @DisplayName("회차 목표일 계산 (월말/윤년 앵커링)")
    class NthCycleDateCalculation {

        @Test
        @DisplayName("MONTHLY - 1/31 시작, 1번째 회차는 2월 말일(28일)로 클램프된다")
        void monthlyClampsToLastDayOfShortMonth() {
            LocalDate result = sut.calculateNthCycleDate(LocalDate.of(2026, 1, 31), CycleRule.MONTHLY, 1);
            assertThat(result).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("MONTHLY - 2번째 회차는 anchor인 31일로 복귀한다 (2월 클램프에 영향받지 않음)")
        void monthlyAnchorRecoversOnNextMonth() {
            LocalDate result = sut.calculateNthCycleDate(LocalDate.of(2026, 1, 31), CycleRule.MONTHLY, 2);
            assertThat(result).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        @DisplayName("YEARLY - 2/29 시작, 평년(1번째)엔 2/28로 클램프된다")
        void yearlyClampsToFeb28InNonLeapYear() {
            LocalDate result = sut.calculateNthCycleDate(LocalDate.of(2024, 2, 29), CycleRule.YEARLY, 1);
            assertThat(result).isEqualTo(LocalDate.of(2025, 2, 28));
        }

        @Test
        @DisplayName("YEARLY - 다음 윤년(4번째)엔 anchor인 2/29로 복귀한다")
        void yearlyAnchorRecoversOnNextLeapYear() {
            LocalDate result = sut.calculateNthCycleDate(LocalDate.of(2024, 2, 29), CycleRule.YEARLY, 4);
            assertThat(result).isEqualTo(LocalDate.of(2028, 2, 29));
        }

        @Test
        @DisplayName("WEEKLY - n주 뒤 날짜를 정확히 계산한다 (드리프트 없음)")
        void weeklyAddsExactWeeksFromStartDate() {
            LocalDate result = sut.calculateNthCycleDate(LocalDate.of(2026, 1, 5), CycleRule.WEEKLY, 3);
            assertThat(result).isEqualTo(LocalDate.of(2026, 1, 26));
        }

        @Test
        @DisplayName("DAILY - n일 뒤 날짜를 정확히 계산한다")
        void dailyAddsExactDaysFromStartDate() {
            LocalDate result = sut.calculateNthCycleDate(LocalDate.of(2026, 1, 1), CycleRule.DAILY, 5);
            assertThat(result).isEqualTo(LocalDate.of(2026, 1, 6));
        }
    }
}