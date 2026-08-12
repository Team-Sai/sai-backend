package org.teamsai.saibackend.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.service.PaymentRecordService;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.payment.type.ObligationStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.payment.type.ReviewStatus;
import org.teamsai.saibackend.domain.payment.type.SourceType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementPaymentService 단위 테스트")
class PaymentServiceTest {

    private static final Long PAYMENT_OBLIGATION_ID = 1L;
    private static final Long BANK_TRANSACTION_ID = 101L;
    private static final Long PARTICIPANT_ID = 11L;

    private static final BigDecimal EXPECTED_AMOUNT =
            new BigDecimal("150000");

    @Mock
    private PaymentObligationMapper paymentObligationMapper;

    @Mock
    private PaymentRecordService paymentRecordService;

    @InjectMocks
    private SettlementPaymentService paymentService;


    @Nested
    @DisplayName("납부 반영")
    class ApplyPayment {

        @Test
        @DisplayName(
                "남은 금액과 같은 금액을 납부하면 납부기록을 생성하고 완납 상태로 변경한다"
        )
        void applyPaymentFullyPaid() {

            BigDecimal amount =
                    new BigDecimal("70000");

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.of(
                            createActiveObligation()
                    )
            );

            given(
                    paymentRecordService
                            .sumConfirmedAmountByTarget(
                                    PaymentTargetType.SETTLEMENT,
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    new BigDecimal("30000")
            );

            given(
                    paymentObligationMapper
                            .updatePaymentStatus(
                                    PAYMENT_OBLIGATION_ID,
                                    PaymentStatus.PAID
                            )
            ).willReturn(1);


            paymentService.applyAutoMatchedPayment(
                    PAYMENT_OBLIGATION_ID,
                    BANK_TRANSACTION_ID,
                    amount
            );


            verify(paymentRecordService)
                    .createConfirmedRecord(
                            BANK_TRANSACTION_ID,
                            PaymentTargetType.SETTLEMENT,
                            PAYMENT_OBLIGATION_ID,
                            amount,
                            SourceType.AUTO_MATCH
                    );

            verify(paymentObligationMapper)
                    .updatePaymentStatus(
                            PAYMENT_OBLIGATION_ID,
                            PaymentStatus.PAID
                    );
        }


        @Test
        @DisplayName(
                "남은 금액보다 적은 금액을 납부하면 부분납 상태로 변경한다"
        )
        void applyPaymentPartiallyPaid() {

            BigDecimal amount =
                    new BigDecimal("50000");

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.of(
                            createActiveObligation()
                    )
            );

            given(
                    paymentRecordService
                            .sumConfirmedAmountByTarget(
                                    PaymentTargetType.SETTLEMENT,
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    new BigDecimal("30000")
            );

            given(
                    paymentObligationMapper
                            .updatePaymentStatus(
                                    PAYMENT_OBLIGATION_ID,
                                    PaymentStatus.PARTIALLY_PAID
                            )
            ).willReturn(1);


            paymentService.applyAutoMatchedPayment(
                    PAYMENT_OBLIGATION_ID,
                    BANK_TRANSACTION_ID,
                    amount
            );


            verify(paymentObligationMapper)
                    .updatePaymentStatus(
                            PAYMENT_OBLIGATION_ID,
                            PaymentStatus.PARTIALLY_PAID
                    );
        }
    }


    @Nested
    @DisplayName("납부 반영 검증")
    class ValidateApplyPayment {

        @Test
        @DisplayName(
                "납부의무가 없으면 예외가 발생한다"
        )
        void applyPaymentFailsWhenObligationDoesNotExist() {

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.empty()
            );


            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            BANK_TRANSACTION_ID,
                                            new BigDecimal("10000")
                                    ),
                    PaymentErrorCode
                            .PAYMENT_OBLIGATION_NOT_FOUND
            );


            verify(
                    paymentRecordService,
                    never()
            ).createConfirmedRecord(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );
        }


        @Test
        @DisplayName(
                "활성 상태가 아닌 납부의무면 예외가 발생한다"
        )
        void applyPaymentFailsWhenObligationIsNotActive() {

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.of(
                            createObligation(
                                    ObligationStatus.CANCELLED
                            )
                    )
            );


            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            BANK_TRANSACTION_ID,
                                            new BigDecimal("10000")
                                    ),
                    PaymentErrorCode
                            .PAYMENT_OBLIGATION_NOT_ACTIVE
            );


            verify(
                    paymentRecordService,
                    never()
            ).createConfirmedRecord(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );
        }


        @Test
        @DisplayName(
                "납부 금액이 0 이하이면 예외가 발생한다"
        )
        void applyPaymentFailsWhenAmountIsInvalid() {

            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            BANK_TRANSACTION_ID,
                                            BigDecimal.ZERO
                                    ),
                    PaymentErrorCode
                            .INVALID_PAYMENT_AMOUNT
            );


            verify(
                    paymentRecordService,
                    never()
            ).createConfirmedRecord(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );

            verify(
                    paymentObligationMapper,
                    never()
            ).findByIdForUpdate(
                    PAYMENT_OBLIGATION_ID
            );
        }


        @Test
        @DisplayName(
                "은행 거래 ID가 없으면 예외가 발생한다"
        )
        void applyPaymentFailsWhenBankTransactionIdIsNull() {

            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            null,
                                            new BigDecimal("10000")
                                    ),
                    PaymentErrorCode
                            .INVALID_BANK_TRANSACTION_ID
            );


            verify(
                    paymentRecordService,
                    never()
            ).createConfirmedRecord(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );

            verify(
                    paymentObligationMapper,
                    never()
            ).findByIdForUpdate(
                    PAYMENT_OBLIGATION_ID
            );
        }


        @Test
        @DisplayName(
                "같은 은행 거래 ID로 이미 반영된 납부기록이 있으면 예외가 발생한다"
        )
        void applyPaymentFailsWhenBankTransactionIsAlreadyApplied() {

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.of(
                            createActiveObligation()
                    )
            );

            given(
                    paymentRecordService
                            .sumConfirmedAmountByTarget(
                                    PaymentTargetType.SETTLEMENT,
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    BigDecimal.ZERO
            );

            willThrow(
                    PaymentErrorCode
                            .DUPLICATE_PAYMENT_RECORD
                            .toException()
            ).given(
                    paymentRecordService
            ).createConfirmedRecord(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );


            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            BANK_TRANSACTION_ID,
                                            new BigDecimal("10000")
                                    ),
                    PaymentErrorCode
                            .DUPLICATE_PAYMENT_RECORD
            );


            verify(paymentRecordService)
                    .createConfirmedRecord(
                            eq(BANK_TRANSACTION_ID),
                            eq(PaymentTargetType.SETTLEMENT),
                            eq(PAYMENT_OBLIGATION_ID),
                            eq(new BigDecimal("10000")),
                            eq(SourceType.AUTO_MATCH)
                    );
        }


        @Test
        @DisplayName(
                "납부 금액이 남은 금액을 초과하면 예외가 발생한다"
        )
        void applyPaymentFailsWhenAmountExceedsRemainingAmount() {

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.of(
                            createActiveObligation()
                    )
            );

            given(
                    paymentRecordService
                            .sumConfirmedAmountByTarget(
                                    PaymentTargetType.SETTLEMENT,
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    new BigDecimal("30000")
            );


            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            BANK_TRANSACTION_ID,
                                            new BigDecimal("80000")
                                    ),
                    PaymentErrorCode
                            .PAYMENT_AMOUNT_EXCEEDS_REMAINING_AMOUNT
            );


            verify(
                    paymentRecordService,
                    never()
            ).createConfirmedRecord(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );
        }


        @Test
        @DisplayName(
                "납부기록 생성에 실패하면 예외가 발생한다"
        )
        void applyPaymentFailsWhenPaymentRecordCreateFails() {

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.of(
                            createActiveObligation()
                    )
            );

            given(
                    paymentRecordService
                            .sumConfirmedAmountByTarget(
                                    PaymentTargetType.SETTLEMENT,
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    BigDecimal.ZERO
            );

            willThrow(
                    PaymentErrorCode
                            .PAYMENT_RECORD_CREATE_FAILED
                            .toException()
            ).given(
                    paymentRecordService
            ).createConfirmedRecord(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );


            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            BANK_TRANSACTION_ID,
                                            new BigDecimal("10000")
                                    ),
                    PaymentErrorCode
                            .PAYMENT_RECORD_CREATE_FAILED
            );


            verify(
                    paymentObligationMapper,
                    never()
            ).updatePaymentStatus(
                    PAYMENT_OBLIGATION_ID,
                    PaymentStatus.PARTIALLY_PAID
            );
        }


        @Test
        @DisplayName(
                "납부기록 생성 중 중복 오류가 발생하면 중복 반영 예외로 변환한다"
        )
        void applyPaymentFailsWhenDuplicateKeyExceptionOccursOnInsert() {

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.of(
                            createActiveObligation()
                    )
            );

            given(
                    paymentRecordService
                            .sumConfirmedAmountByTarget(
                                    PaymentTargetType.SETTLEMENT,
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    BigDecimal.ZERO
            );

            willThrow(
                    PaymentErrorCode
                            .DUPLICATE_PAYMENT_RECORD
                            .toException()
            ).given(
                    paymentRecordService
            ).createConfirmedRecord(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );


            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            BANK_TRANSACTION_ID,
                                            new BigDecimal("10000")
                                    ),
                    PaymentErrorCode
                            .DUPLICATE_PAYMENT_RECORD
            );


            verify(
                    paymentObligationMapper,
                    never()
            ).updatePaymentStatus(
                    any(),
                    any()
            );
        }


        @Test
        @DisplayName(
                "납부 상태 변경에 실패하면 예외가 발생한다"
        )
        void applyPaymentFailsWhenPaymentStatusUpdateFails() {

            given(
                    paymentObligationMapper
                            .findByIdForUpdate(
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    Optional.of(
                            createActiveObligation()
                    )
            );

            given(
                    paymentRecordService
                            .sumConfirmedAmountByTarget(
                                    PaymentTargetType.SETTLEMENT,
                                    PAYMENT_OBLIGATION_ID
                            )
            ).willReturn(
                    BigDecimal.ZERO
            );

            given(
                    paymentObligationMapper
                            .updatePaymentStatus(
                                    PAYMENT_OBLIGATION_ID,
                                    PaymentStatus.PARTIALLY_PAID
                            )
            ).willReturn(0);


            assertPaymentExceptionThrownBy(
                    () ->
                            paymentService
                                    .applyAutoMatchedPayment(
                                            PAYMENT_OBLIGATION_ID,
                                            BANK_TRANSACTION_ID,
                                            new BigDecimal("10000")
                                    ),
                    PaymentErrorCode
                            .PAYMENT_STATUS_UPDATE_FAILED
            );
        }


        @Nested
        @DisplayName("납부 의무 생성")
        class CreatePaymentObligation {

            @Test
            @DisplayName(
                    "참여자별 예정 원금과 초기 상태로 납부 의무를 생성한다"
            )
            void createObligationSucceeds() {

                given(
                        paymentObligationMapper.insert(
                                any(
                                        PaymentObligationDTO.class
                                )
                        )
                ).willReturn(1);


                paymentService.createObligation(
                        PARTICIPANT_ID,
                        EXPECTED_AMOUNT
                );


                ArgumentCaptor<PaymentObligationDTO> captor =
                        ArgumentCaptor.forClass(
                                PaymentObligationDTO.class
                        );

                verify(paymentObligationMapper)
                        .insert(
                                captor.capture()
                        );


                PaymentObligationDTO savedObligation =
                        captor.getValue();


                assertThat(
                        savedObligation.getParticipantId()
                ).isEqualTo(
                        PARTICIPANT_ID
                );

                assertThat(
                        savedObligation.getExpectedAmount()
                ).isEqualByComparingTo(
                        EXPECTED_AMOUNT
                );

                assertThat(
                        savedObligation.getPaymentStatus()
                ).isEqualTo(
                        PaymentStatus.UNPAID
                );

                assertThat(
                        savedObligation.getReviewStatus()
                ).isEqualTo(
                        ReviewStatus.NORMAL
                );

                assertThat(
                        savedObligation.getObligationStatus()
                ).isEqualTo(
                        ObligationStatus.ACTIVE
                );
            }


            @Test
            @DisplayName(
                    "참여자 ID가 없으면 납부 의무를 생성하지 않는다"
            )
            void createObligationFailsWhenParticipantIdIsNull() {

                assertPaymentExceptionThrownBy(
                        () ->
                                paymentService
                                        .createObligation(
                                                null,
                                                EXPECTED_AMOUNT
                                        ),
                        PaymentErrorCode
                                .INVALID_PAYMENT_OBLIGATION_REQUEST
                );


                verify(
                        paymentObligationMapper,
                        never()
                ).insert(
                        any(
                                PaymentObligationDTO.class
                        )
                );
            }


            @Test
            @DisplayName(
                    "예정 원금이 없으면 납부 의무를 생성하지 않는다"
            )
            void createObligationFailsWhenExpectedAmountIsNull() {

                assertPaymentExceptionThrownBy(
                        () ->
                                paymentService
                                        .createObligation(
                                                PARTICIPANT_ID,
                                                null
                                        ),
                        PaymentErrorCode
                                .INVALID_PAYMENT_OBLIGATION_REQUEST
                );


                verify(
                        paymentObligationMapper,
                        never()
                ).insert(
                        any(
                                PaymentObligationDTO.class
                        )
                );
            }


            @Test
            @DisplayName(
                    "예정 원금이 0 이하이면 납부 의무를 생성하지 않는다"
            )
            void createObligationFailsWhenExpectedAmountIsNotPositive() {

                assertPaymentExceptionThrownBy(
                        () ->
                                paymentService
                                        .createObligation(
                                                PARTICIPANT_ID,
                                                BigDecimal.ZERO
                                        ),
                        PaymentErrorCode
                                .INVALID_PAYMENT_OBLIGATION_REQUEST
                );


                verify(
                        paymentObligationMapper,
                        never()
                ).insert(
                        any(
                                PaymentObligationDTO.class
                        )
                );
            }


            @Test
            @DisplayName(
                    "납부 의무 INSERT 결과가 1건이 아니면 예외가 발생한다"
            )
            void createObligationFailsWhenInsertCountIsInvalid() {

                given(
                        paymentObligationMapper.insert(
                                any(
                                        PaymentObligationDTO.class
                                )
                        )
                ).willReturn(0);


                assertPaymentExceptionThrownBy(
                        () ->
                                paymentService
                                        .createObligation(
                                                PARTICIPANT_ID,
                                                EXPECTED_AMOUNT
                                        ),
                        PaymentErrorCode
                                .PAYMENT_OBLIGATION_CREATE_FAILED
                );
            }
        }
    }


    private PaymentObligationDTO createActiveObligation() {
        return createObligation(
                ObligationStatus.ACTIVE
        );
    }


    private PaymentObligationDTO createObligation(
            ObligationStatus obligationStatus
    ) {

        return PaymentObligationDTO.builder()
                .paymentObligationId(
                        PAYMENT_OBLIGATION_ID
                )
                .participantId(1L)
                .expectedAmount(
                        new BigDecimal("100000")
                )
                .paymentStatus(
                        PaymentStatus.UNPAID
                )
                .reviewStatus(
                        ReviewStatus.NORMAL
                )
                .obligationStatus(
                        obligationStatus
                )
                .build();
    }


    private void assertPaymentExceptionThrownBy(
            Runnable operation,
            PaymentErrorCode errorCode
    ) {

        assertThatThrownBy(
                operation::run
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception ->
                        assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                errorCode
                        )
        );
    }
}