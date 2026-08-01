package org.teamsai.saibackend.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.dto.PaymentRecordDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.mapper.PaymentRecordMapper;
import org.teamsai.saibackend.domain.payment.service.PaymentService;
import org.teamsai.saibackend.domain.payment.type.ObligationStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;
import org.teamsai.saibackend.domain.payment.type.RecordStatus;
import org.teamsai.saibackend.domain.payment.type.ReviewStatus;
import org.teamsai.saibackend.domain.payment.type.SourceType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    private static final Long PAYMENT_OBLIGATION_ID = 1L;
    private static final Long SETTLEMENT_BANK_TRANSACTION_ID = 101L;

    @Mock
    private PaymentObligationMapper paymentObligationMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @InjectMocks
    private PaymentService paymentService;

    @Nested
    @DisplayName("납부 반영")
    class ApplyPayment {

        @Test
        @DisplayName("남은 금액과 같은 금액을 납부하면 납부기록을 생성하고 완납 상태로 변경한다")
        void applyPaymentFullyPaid() {
            BigDecimal amount = new BigDecimal("70000");

            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.of(createActiveObligation()));

            given(paymentRecordMapper.sumConfirmedAmountByObligationId(
                    PAYMENT_OBLIGATION_ID
            )).willReturn(new BigDecimal("30000"));

            given(paymentRecordMapper.insert(any(PaymentRecordDTO.class)))
                    .willReturn(1);

            given(paymentObligationMapper.updatePaymentStatus(
                    PAYMENT_OBLIGATION_ID,
                    PaymentStatus.PAID
            )).willReturn(1);

            paymentService.applyPayment(
                    PAYMENT_OBLIGATION_ID,
                    SETTLEMENT_BANK_TRANSACTION_ID,
                    amount,
                    SourceType.AUTO_MATCH
            );

            ArgumentCaptor<PaymentRecordDTO> paymentRecordCaptor =
                    ArgumentCaptor.forClass(PaymentRecordDTO.class);

            verify(paymentRecordMapper)
                    .insert(paymentRecordCaptor.capture());

            PaymentRecordDTO paymentRecord =
                    paymentRecordCaptor.getValue();

            assertThat(paymentRecord.getObligationId())
                    .isEqualTo(PAYMENT_OBLIGATION_ID);
            assertThat(paymentRecord.getSettlementBankTransactionId())
                    .isEqualTo(SETTLEMENT_BANK_TRANSACTION_ID);
            assertThat(paymentRecord.getAmount())
                    .isEqualByComparingTo(amount);
            assertThat(paymentRecord.getSourceType())
                    .isEqualTo(SourceType.AUTO_MATCH);
            assertThat(paymentRecord.getRecordStatus())
                    .isEqualTo(RecordStatus.CONFIRMED);
            assertThat(paymentRecord.getRecordedAt())
                    .isNotNull();

            verify(paymentObligationMapper).updatePaymentStatus(
                    PAYMENT_OBLIGATION_ID,
                    PaymentStatus.PAID
            );
        }

        @Test
        @DisplayName("남은 금액보다 적은 금액을 납부하면 부분납 상태로 변경한다")
        void applyPaymentPartiallyPaid() {
            BigDecimal amount = new BigDecimal("50000");

            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.of(createActiveObligation()));

            given(paymentRecordMapper.sumConfirmedAmountByObligationId(
                    PAYMENT_OBLIGATION_ID
            )).willReturn(new BigDecimal("30000"));

            given(paymentRecordMapper.insert(any(PaymentRecordDTO.class)))
                    .willReturn(1);

            given(paymentObligationMapper.updatePaymentStatus(
                    PAYMENT_OBLIGATION_ID,
                    PaymentStatus.PARTIALLY_PAID
            )).willReturn(1);

            // sourceType과 관계없이 납부 상태 계산은 동일하게 동작해야 한다.
            paymentService.applyPayment(
                    PAYMENT_OBLIGATION_ID,
                    SETTLEMENT_BANK_TRANSACTION_ID,
                    amount,
                    SourceType.MANUAL
            );

            verify(paymentObligationMapper).updatePaymentStatus(
                    PAYMENT_OBLIGATION_ID,
                    PaymentStatus.PARTIALLY_PAID
            );
        }
    }

    @Nested
    @DisplayName("납부 반영 검증")
    class ValidateApplyPayment {

        @Test
        @DisplayName("납부의무가 없으면 예외가 발생한다")
        void applyPaymentFailsWhenObligationDoesNotExist() {
            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.empty());

            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            new BigDecimal("10000"),
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.PAYMENT_OBLIGATION_NOT_FOUND
            );

            verify(paymentRecordMapper, never())
                    .insert(any(PaymentRecordDTO.class));
        }

        @Test
        @DisplayName("활성 상태가 아닌 납부의무면 예외가 발생한다")
        void applyPaymentFailsWhenObligationIsNotActive() {
            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.of(
                            createObligation(ObligationStatus.CANCELLED)
                    ));

            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            new BigDecimal("10000"),
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.PAYMENT_OBLIGATION_NOT_ACTIVE
            );

            verify(paymentRecordMapper, never())
                    .insert(any(PaymentRecordDTO.class));
        }

        @Test
        @DisplayName("납부 금액이 0 이하이면 예외가 발생한다")
        void applyPaymentFailsWhenAmountIsInvalid() {
            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            BigDecimal.ZERO,
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.INVALID_PAYMENT_AMOUNT
            );

            verify(paymentRecordMapper, never())
                    .insert(any(PaymentRecordDTO.class));
            verify(paymentObligationMapper, never())
                    .findByIdForUpdate(PAYMENT_OBLIGATION_ID);
        }

        @Test
        @DisplayName("납부 출처가 없으면 예외가 발생한다")
        void applyPaymentFailsWhenSourceTypeIsNull() {
            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            new BigDecimal("10000"),
                            null
                    ),
                    PaymentErrorCode.INVALID_PAYMENT_SOURCE_TYPE
            );

            verify(paymentRecordMapper, never())
                    .insert(any(PaymentRecordDTO.class));
            verify(paymentObligationMapper, never())
                    .findByIdForUpdate(PAYMENT_OBLIGATION_ID);
        }

        @Test
        @DisplayName("은행 거래 ID가 없으면 예외가 발생한다")
        void applyPaymentFailsWhenSettlementBankTransactionIdIsNull() {
            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            null,
                            new BigDecimal("10000"),
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.INVALID_SETTLEMENT_BANK_TRANSACTION_ID
            );

            verify(paymentRecordMapper, never())
                    .insert(any(PaymentRecordDTO.class));
            verify(paymentObligationMapper, never())
                    .findByIdForUpdate(PAYMENT_OBLIGATION_ID);
        }

        @Test
        @DisplayName("같은 은행 거래 ID로 이미 반영된 납부기록이 있으면 예외가 발생한다")
        void applyPaymentFailsWhenSettlementBankTransactionIsAlreadyApplied() {
            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.of(createActiveObligation()));

            given(paymentRecordMapper.existsBySettlementBankTransactionId(
                    SETTLEMENT_BANK_TRANSACTION_ID
            )).willReturn(true);

            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            new BigDecimal("10000"),
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.DUPLICATE_PAYMENT_RECORD
            );

            verify(paymentRecordMapper, never())
                    .insert(any(PaymentRecordDTO.class));
        }

        @Test
        @DisplayName("납부 금액이 남은 금액을 초과하면 예외가 발생한다")
        void applyPaymentFailsWhenAmountExceedsRemainingAmount() {
            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.of(createActiveObligation()));

            given(paymentRecordMapper.sumConfirmedAmountByObligationId(
                    PAYMENT_OBLIGATION_ID
            )).willReturn(new BigDecimal("30000"));

            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            new BigDecimal("80000"),
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING_AMOUNT
            );

            verify(paymentRecordMapper, never())
                    .insert(any(PaymentRecordDTO.class));
        }

        @Test
        @DisplayName("납부기록 생성에 실패하면 예외가 발생한다")
        void applyPaymentFailsWhenPaymentRecordCreateFails() {
            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.of(createActiveObligation()));

            given(paymentRecordMapper.sumConfirmedAmountByObligationId(
                    PAYMENT_OBLIGATION_ID
            )).willReturn(BigDecimal.ZERO);

            given(paymentRecordMapper.insert(any(PaymentRecordDTO.class)))
                    .willReturn(0);

            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            new BigDecimal("10000"),
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.PAYMENT_RECORD_CREATE_FAILED
            );

            verify(paymentObligationMapper, never())
                    .updatePaymentStatus(
                            PAYMENT_OBLIGATION_ID,
                            PaymentStatus.PARTIALLY_PAID
                    );
        }

        @Test
        @DisplayName("납부기록 생성 중 중복 오류가 발생하면 중복 반영 예외로 변환한다")
        void applyPaymentFailsWhenDuplicateKeyExceptionOccursOnInsert() {
            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.of(createActiveObligation()));

            given(paymentRecordMapper.sumConfirmedAmountByObligationId(
                    PAYMENT_OBLIGATION_ID
            )).willReturn(BigDecimal.ZERO);

            given(paymentRecordMapper.insert(any(PaymentRecordDTO.class)))
                    .willThrow(new DuplicateKeyException("duplicate payment record"));

            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            new BigDecimal("10000"),
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.DUPLICATE_PAYMENT_RECORD
            );

            verify(paymentObligationMapper, never())
                    .updatePaymentStatus(any(), any());
        }

        @Test
        @DisplayName("납부 상태 변경에 실패하면 예외가 발생한다")
        void applyPaymentFailsWhenPaymentStatusUpdateFails() {
            given(paymentObligationMapper.findByIdForUpdate(PAYMENT_OBLIGATION_ID))
                    .willReturn(Optional.of(createActiveObligation()));

            given(paymentRecordMapper.sumConfirmedAmountByObligationId(
                    PAYMENT_OBLIGATION_ID
            )).willReturn(BigDecimal.ZERO);

            given(paymentRecordMapper.insert(any(PaymentRecordDTO.class)))
                    .willReturn(1);

            given(paymentObligationMapper.updatePaymentStatus(
                    PAYMENT_OBLIGATION_ID,
                    PaymentStatus.PARTIALLY_PAID
            )).willReturn(0);

            assertPaymentExceptionThrownBy(
                    () -> paymentService.applyPayment(
                            PAYMENT_OBLIGATION_ID,
                            SETTLEMENT_BANK_TRANSACTION_ID,
                            new BigDecimal("10000"),
                            SourceType.AUTO_MATCH
                    ),
                    PaymentErrorCode.PAYMENT_STATUS_UPDATE_FAILED
            );
        }
    }

    private PaymentObligationDTO createActiveObligation() {
        return createObligation(ObligationStatus.ACTIVE);
    }

    private PaymentObligationDTO createObligation(
            ObligationStatus obligationStatus
    ) {
        return PaymentObligationDTO.builder()
                .paymentObligationId(PAYMENT_OBLIGATION_ID)
                .participantId(1L)
                .expectedAmount(new BigDecimal("100000"))
                .paymentStatus(PaymentStatus.UNPAID)
                .reviewStatus(ReviewStatus.NORMAL)
                .obligationStatus(obligationStatus)
                .build();
    }

    private void assertPaymentExceptionThrownBy(
            Runnable operation,
            PaymentErrorCode errorCode
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(errorCode)
                );
    }
}
