package org.teamsai.saibackend.domain.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;
import org.teamsai.saibackend.domain.contractrepaymentschedule.exception.RepaymentScheduleErrorCode;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.payment.type.SourceType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanPaymentService 단위 테스트")
class LoanPaymentServiceTest {

    private static final Long SCHEDULE_ID = 1L;
    private static final Long BANK_TRANSACTION_ID = 101L;

    @Mock
    private RepaymentScheduleService repaymentScheduleService;

    @Mock
    private PaymentRecordService paymentRecordService;

    @InjectMocks
    private LoanPaymentService loanPaymentService;

    @Nested
    @DisplayName("자동매칭 납부 반영")
    class ApplyAutoMatchedPayment {

        @Test
        @DisplayName("남은 금액과 같은 금액을 납부하면 납부기록을 생성하고 상환완료 처리한다")
        void appliesPaymentAndMarksScheduleAsPaidWhenFullyPaid() {
            BigDecimal amount = new BigDecimal("30000");

            given(repaymentScheduleService.getScheduleByScheduleId(SCHEDULE_ID))
                    .willReturn(schedule(RepaymentScheduleStatus.PENDING, new BigDecimal("100000")));

            given(paymentRecordService.sumConfirmedAmountByTarget(PaymentTargetType.LOAN, SCHEDULE_ID))
                    .willReturn(new BigDecimal("70000"));

            loanPaymentService.applyAutoMatchedPayment(SCHEDULE_ID, BANK_TRANSACTION_ID, amount);

            verify(paymentRecordService).createConfirmedRecord(
                    BANK_TRANSACTION_ID,
                    PaymentTargetType.LOAN,
                    SCHEDULE_ID,
                    amount,
                    SourceType.AUTO_MATCH
            );

            ArgumentCaptor<LocalDateTime> paidAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(repaymentScheduleService).markAsPaid(eq(SCHEDULE_ID), paidAtCaptor.capture());
            assertThat(Duration.between(paidAtCaptor.getValue(), LocalDateTime.now()).abs())
                    .isLessThan(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("남은 금액보다 적게 납부하면 납부기록만 생성하고 상환완료 처리하지 않는다")
        void appliesPaymentWithoutMarkingAsPaidWhenPartiallyPaid() {
            BigDecimal amount = new BigDecimal("10000");

            given(repaymentScheduleService.getScheduleByScheduleId(SCHEDULE_ID))
                    .willReturn(schedule(RepaymentScheduleStatus.PENDING, new BigDecimal("100000")));

            given(paymentRecordService.sumConfirmedAmountByTarget(PaymentTargetType.LOAN, SCHEDULE_ID))
                    .willReturn(new BigDecimal("70000"));

            loanPaymentService.applyAutoMatchedPayment(SCHEDULE_ID, BANK_TRANSACTION_ID, amount);

            verify(paymentRecordService).createConfirmedRecord(
                    BANK_TRANSACTION_ID,
                    PaymentTargetType.LOAN,
                    SCHEDULE_ID,
                    amount,
                    SourceType.AUTO_MATCH
            );
            verify(repaymentScheduleService, never()).markAsPaid(any(), any());
        }

        @Test
        @DisplayName("기존 확정 납부금액이 없으면(null) 0원으로 간주하여 계산한다")
        void treatsNullExistingConfirmedAmountAsZero() {
            BigDecimal amount = new BigDecimal("100000");

            given(repaymentScheduleService.getScheduleByScheduleId(SCHEDULE_ID))
                    .willReturn(schedule(RepaymentScheduleStatus.PENDING, new BigDecimal("100000")));

            given(paymentRecordService.sumConfirmedAmountByTarget(PaymentTargetType.LOAN, SCHEDULE_ID))
                    .willReturn(null);

            loanPaymentService.applyAutoMatchedPayment(SCHEDULE_ID, BANK_TRANSACTION_ID, amount);

            verify(paymentRecordService).createConfirmedRecord(
                    BANK_TRANSACTION_ID,
                    PaymentTargetType.LOAN,
                    SCHEDULE_ID,
                    amount,
                    SourceType.AUTO_MATCH
            );
            verify(repaymentScheduleService).markAsPaid(eq(SCHEDULE_ID), any());
        }
    }

    @Nested
    @DisplayName("자동매칭 납부 반영 검증")
    class ValidateApplyAutoMatchedPayment {

        @Test
        @DisplayName("PENDING 상태가 아닌 스케줄이면 예외가 발생하고 납부기록을 생성하지 않는다")
        void failsWhenScheduleIsNotPending() {
            given(repaymentScheduleService.getScheduleByScheduleId(SCHEDULE_ID))
                    .willReturn(schedule(RepaymentScheduleStatus.PAID, new BigDecimal("100000")));

            assertThatThrownBy(() -> loanPaymentService.applyAutoMatchedPayment(
                    SCHEDULE_ID, BANK_TRANSACTION_ID, new BigDecimal("10000")
            )).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(RepaymentScheduleErrorCode.SCHEDULE_NOT_PENDING)
            );

            verify(paymentRecordService, never()).createConfirmedRecord(any(), any(), any(), any(), any());
            verify(repaymentScheduleService, never()).markAsPaid(any(), any());
        }

        @Test
        @DisplayName("납부 예정 금액을 초과하여 납부하면 예외가 발생하고 납부기록을 생성하지 않는다")
        void failsWhenAmountExceedsExpectedTotal() {
            given(repaymentScheduleService.getScheduleByScheduleId(SCHEDULE_ID))
                    .willReturn(schedule(RepaymentScheduleStatus.PENDING, new BigDecimal("100000")));

            given(paymentRecordService.sumConfirmedAmountByTarget(PaymentTargetType.LOAN, SCHEDULE_ID))
                    .willReturn(new BigDecimal("70000"));

            assertThatThrownBy(() -> loanPaymentService.applyAutoMatchedPayment(
                    SCHEDULE_ID, BANK_TRANSACTION_ID, new BigDecimal("40000")
            )).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING_AMOUNT)
            );

            verify(paymentRecordService, never()).createConfirmedRecord(any(), any(), any(), any(), any());
            verify(repaymentScheduleService, never()).markAsPaid(any(), any());
        }
    }

    @Nested
    @DisplayName("사용자 선택 납부 반영")
    class ApplyManuallyMatchedPayment {

        @Test
        @DisplayName("초과입금은 현재 잔여금액까지만 수동 납부로 반영한다")
        void limitsExcessAmountToCurrentRemainingAmount() {
            given(repaymentScheduleService
                    .getScheduleByScheduleId(SCHEDULE_ID))
                    .willReturn(schedule(
                            RepaymentScheduleStatus.PENDING,
                            new BigDecimal("100000")
                    ));
            given(paymentRecordService.sumConfirmedAmountByTarget(
                    PaymentTargetType.LOAN,
                    SCHEDULE_ID
            )).willReturn(new BigDecimal("70000"));

            loanPaymentService.applyManuallyMatchedPayment(
                    SCHEDULE_ID,
                    BANK_TRANSACTION_ID,
                    new BigDecimal("32000")
            );

            verify(paymentRecordService).createConfirmedRecord(
                    BANK_TRANSACTION_ID,
                    PaymentTargetType.LOAN,
                    SCHEDULE_ID,
                    new BigDecimal("30000"),
                    SourceType.MANUAL
            );
            verify(repaymentScheduleService).markAsPaid(
                    eq(SCHEDULE_ID),
                    any()
            );
        }

        @Test
        @DisplayName("확정 납부금액상 잔여액이 없으면 수동 납부를 반영하지 않는다")
        void rejectsPaymentWhenConfirmedAmountAlreadyCoversSchedule() {
            given(repaymentScheduleService
                    .getScheduleByScheduleId(SCHEDULE_ID))
                    .willReturn(schedule(
                            RepaymentScheduleStatus.PENDING,
                            new BigDecimal("100000")
                    ));
            given(paymentRecordService.sumConfirmedAmountByTarget(
                    PaymentTargetType.LOAN,
                    SCHEDULE_ID
            )).willReturn(new BigDecimal("100000"));

            assertThatThrownBy(() ->
                    loanPaymentService.applyManuallyMatchedPayment(
                            SCHEDULE_ID,
                            BANK_TRANSACTION_ID,
                            new BigDecimal("10000")
                    )
            ).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    RepaymentScheduleErrorCode
                                            .SCHEDULE_NOT_PENDING
                            )
            );

            verify(paymentRecordService, never()).createConfirmedRecord(
                    any(), any(), any(), any(), any()
            );
        }
    }

    private RepaymentScheduleDTO schedule(RepaymentScheduleStatus status, BigDecimal totalPaymentDue) {
        return RepaymentScheduleDTO.builder()
                .scheduleId(SCHEDULE_ID)
                .status(status)
                .totalPaymentDue(totalPaymentDue)
                .build();
    }
}
