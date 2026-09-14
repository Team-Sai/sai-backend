package org.teamsai.saibackend.domain.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.type.ObligationStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.payment.type.SourceType;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementPaymentService 단위 테스트")
class SettlementPaymentServiceTest {

    @Mock
    private PaymentObligationMapper paymentObligationMapper;

    @Mock
    private PaymentRecordService paymentRecordService;

    @Test
    @DisplayName("초과입금은 현재 잔여금액까지만 수동 납부로 반영한다")
    void limitsExcessAmountToCurrentRemainingAmount() {
        SettlementPaymentService settlementPaymentService =
                new SettlementPaymentService(
                        paymentObligationMapper,
                        paymentRecordService
                );
        PaymentObligationDTO obligation = PaymentObligationDTO.builder()
                .paymentObligationId(10L)
                .expectedAmount(new BigDecimal("100000"))
                .paymentStatus(PaymentStatus.PARTIALLY_PAID)
                .obligationStatus(ObligationStatus.ACTIVE)
                .build();

        given(paymentRecordService.existsByBankTransactionId(101L))
                .willReturn(false);
        given(paymentObligationMapper.findByIdForUpdate(10L))
                .willReturn(Optional.of(obligation));
        given(paymentRecordService.sumConfirmedAmountByTarget(
                PaymentTargetType.SETTLEMENT,
                10L
        )).willReturn(new BigDecimal("70000"));
        given(paymentObligationMapper.updatePaymentStatus(
                10L,
                PaymentStatus.PAID
        )).willReturn(1);

        settlementPaymentService.applyManuallyMatchedPayment(
                10L,
                101L,
                new BigDecimal("32000")
        );

        verify(paymentRecordService).createConfirmedRecord(
                101L,
                PaymentTargetType.SETTLEMENT,
                10L,
                new BigDecimal("30000"),
                SourceType.MANUAL
        );
        verify(paymentObligationMapper).updatePaymentStatus(
                10L,
                PaymentStatus.PAID
        );
    }

    @Test
    @DisplayName("이미 완납된 정산 대상에는 수동 납부를 반영하지 않는다")
    void rejectsManuallyMatchedPaymentForPaidObligation() {
        SettlementPaymentService settlementPaymentService =
                new SettlementPaymentService(
                        paymentObligationMapper,
                        paymentRecordService
                );
        PaymentObligationDTO obligation = PaymentObligationDTO.builder()
                .paymentObligationId(10L)
                .expectedAmount(new BigDecimal("100000"))
                .paymentStatus(PaymentStatus.PAID)
                .obligationStatus(ObligationStatus.ACTIVE)
                .build();

        given(paymentRecordService.existsByBankTransactionId(101L))
                .willReturn(false);
        given(paymentObligationMapper.findByIdForUpdate(10L))
                .willReturn(Optional.of(obligation));

        assertThatThrownBy(() ->
                settlementPaymentService.applyManuallyMatchedPayment(
                        10L,
                        101L,
                        new BigDecimal("10000")
                )
        ).isInstanceOfSatisfying(
                org.teamsai.saibackend.global.exception.DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_ACTIVE)
        );
    }
}
