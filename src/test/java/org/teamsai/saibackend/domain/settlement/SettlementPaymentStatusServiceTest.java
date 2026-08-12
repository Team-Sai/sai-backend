package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementPaymentStatusMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentStatusService;
import org.teamsai.saibackend.domain.settlement.service.SettlementValidator;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementPaymentStatusService 단위 테스트")
class SettlementPaymentStatusServiceTest {

    private static final Long SETTLEMENT_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementPaymentStatusMapper paymentStatusMapper;

    @Mock
    private SettlementValidator settlementValidator;

    @InjectMocks
    private SettlementPaymentStatusService paymentStatusService;


    @Test
    @DisplayName("납부의무별 금액을 합산하고 진행률을 계산한다")
    void getPaymentStatusCalculatesTotalsAndProgressRate() {

        SettlementDTO settlement =
                createSettlement();

        given(
                settlementMapper.findById(
                        SETTLEMENT_ID
                )
        ).willReturn(
                Optional.of(settlement)
        );

        given(
                paymentStatusMapper
                        .findPaymentObligationsBySettlementId(
                                SETTLEMENT_ID
                        )
        ).willReturn(
                List.of(
                        obligation(
                                10000,
                                5000,
                                5000,
                                PaymentStatus.PARTIALLY_PAID
                        ),
                        obligation(
                                20000,
                                20000,
                                0,
                                PaymentStatus.PAID
                        )
                )
        );


        SettlementPaymentStatusResponse response =
                paymentStatusService.getPaymentStatus(
                        SETTLEMENT_ID,
                        OWNER_ID
                );


        assertThat(response.getTotalExpectedAmount())
                .isEqualByComparingTo("30000");

        assertThat(response.getTotalPaidAmount())
                .isEqualByComparingTo("25000");

        assertThat(response.getTotalRemainingAmount())
                .isEqualByComparingTo("5000");

        assertThat(response.getProgressRate())
                .isEqualByComparingTo("83.33");

        assertThat(response.isClosable())
                .isFalse();


        verify(settlementValidator)
                .validateOwner(
                        settlement,
                        OWNER_ID
                );
    }


    @Test
    @DisplayName("모든 납부의무가 PAID이면 마감 가능 상태가 된다")
    void getPaymentStatusIsClosableWhenAllObligationsArePaid() {

        SettlementDTO settlement =
                createSettlement();

        given(
                settlementMapper.findById(
                        SETTLEMENT_ID
                )
        ).willReturn(
                Optional.of(settlement)
        );

        given(
                paymentStatusMapper
                        .findPaymentObligationsBySettlementId(
                                SETTLEMENT_ID
                        )
        ).willReturn(
                List.of(
                        obligation(
                                10000,
                                10000,
                                0,
                                PaymentStatus.PAID
                        )
                )
        );


        SettlementPaymentStatusResponse response =
                paymentStatusService.getPaymentStatus(
                        SETTLEMENT_ID,
                        OWNER_ID
                );


        assertThat(response.isClosable())
                .isTrue();

        assertThat(response.getProgressRate())
                .isEqualByComparingTo("100.00");
    }


    @Test
    @DisplayName("초과 납부가 있어도 진행률은 100을 넘지 않고 마감할 수 없다")
    void getPaymentStatusIsNotClosableWhenPaymentExceedsExpectedAmount() {

        SettlementDTO settlement =
                createSettlement();

        given(
                settlementMapper.findById(
                        SETTLEMENT_ID
                )
        ).willReturn(
                Optional.of(settlement)
        );

        given(
                paymentStatusMapper
                        .findPaymentObligationsBySettlementId(
                                SETTLEMENT_ID
                        )
        ).willReturn(
                List.of(
                        obligation(
                                10000,
                                11000,
                                0,
                                PaymentStatus.PAID
                        )
                )
        );


        SettlementPaymentStatusResponse response =
                paymentStatusService.getPaymentStatus(
                        SETTLEMENT_ID,
                        OWNER_ID
                );


        assertThat(response.getProgressRate())
                .isEqualByComparingTo("100.00");

        assertThat(response.getTotalRemainingAmount())
                .isEqualByComparingTo("0");

        assertThat(response.isClosable())
                .isFalse();
    }


    @Test
    @DisplayName("정산이 없으면 현황 조회에 실패한다")
    void getPaymentStatusFailsWhenSettlementDoesNotExist() {

        given(
                settlementMapper.findById(
                        SETTLEMENT_ID
                )
        ).willReturn(
                Optional.empty()
        );


        assertThatThrownBy(
                () ->
                        paymentStatusService.getPaymentStatus(
                                SETTLEMENT_ID,
                                OWNER_ID
                        )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception ->
                        assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                SettlementErrorCode
                                        .SETTLEMENT_NOT_FOUND
                        )
        );


        verify(
                settlementValidator,
                never()
        ).validateOwner(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }


    @Test
    @DisplayName("정산 owner가 아니면 납부 현황을 조회할 수 없다")
    void getPaymentStatusFailsWhenUserIsNotOwner() {

        SettlementDTO settlement =
                createSettlement();

        given(
                settlementMapper.findById(
                        SETTLEMENT_ID
                )
        ).willReturn(
                Optional.of(settlement)
        );

        doThrow(
                SettlementErrorCode
                        .SETTLEMENT_ACCESS_DENIED
                        .toException()
        ).when(settlementValidator)
                .validateOwner(
                        settlement,
                        OTHER_USER_ID
                );


        assertThatThrownBy(
                () ->
                        paymentStatusService.getPaymentStatus(
                                SETTLEMENT_ID,
                                OTHER_USER_ID
                        )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception ->
                        assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                SettlementErrorCode
                                        .SETTLEMENT_ACCESS_DENIED
                        )
        );


        verify(
                paymentStatusMapper,
                never()
        ).findPaymentObligationsBySettlementId(
                SETTLEMENT_ID
        );
    }


    @Test
    @DisplayName("모든 납부의무의 납부가 완료되면 true를 반환한다")
    void areAllObligationsPaidReturnsTrueWhenAllPaid() {

        given(
                paymentStatusMapper
                        .findPaymentObligationsBySettlementId(
                                SETTLEMENT_ID
                        )
        ).willReturn(
                List.of(
                        obligation(
                                10000,
                                10000,
                                0,
                                PaymentStatus.PAID
                        ),
                        obligation(
                                20000,
                                20000,
                                0,
                                PaymentStatus.PAID
                        )
                )
        );


        boolean result =
                paymentStatusService
                        .areAllObligationsPaid(
                                SETTLEMENT_ID
                        );


        assertThat(result)
                .isTrue();
    }


    @Test
    @DisplayName("완료되지 않은 납부의무가 하나라도 있으면 false를 반환한다")
    void areAllObligationsPaidReturnsFalseWhenNotAllPaid() {

        given(
                paymentStatusMapper
                        .findPaymentObligationsBySettlementId(
                                SETTLEMENT_ID
                        )
        ).willReturn(
                List.of(
                        obligation(
                                10000,
                                10000,
                                0,
                                PaymentStatus.PAID
                        ),
                        obligation(
                                20000,
                                10000,
                                10000,
                                PaymentStatus.PARTIALLY_PAID
                        )
                )
        );


        boolean result =
                paymentStatusService
                        .areAllObligationsPaid(
                                SETTLEMENT_ID
                        );


        assertThat(result)
                .isFalse();
    }


    @Test
    @DisplayName("납부의무가 하나도 없으면 완료 상태가 아니다")
    void areAllObligationsPaidReturnsFalseWhenObligationsAreEmpty() {

        given(
                paymentStatusMapper
                        .findPaymentObligationsBySettlementId(
                                SETTLEMENT_ID
                        )
        ).willReturn(
                List.of()
        );


        boolean result =
                paymentStatusService
                        .areAllObligationsPaid(
                                SETTLEMENT_ID
                        );


        assertThat(result)
                .isFalse();
    }


    private SettlementDTO createSettlement() {

        return SettlementDTO.builder()
                .settlementId(SETTLEMENT_ID)
                .ownerId(OWNER_ID)
                .build();
    }


    private SettlementPaymentObligationResponse obligation(
            long expectedAmount,
            long paidAmount,
            long remainingAmount,
            PaymentStatus paymentStatus
    ) {

        return SettlementPaymentObligationResponse.builder()
                .paymentObligationId(1L)
                .participantId(1L)
                .expectedAmount(
                        BigDecimal.valueOf(
                                expectedAmount
                        )
                )
                .paidAmount(
                        BigDecimal.valueOf(
                                paidAmount
                        )
                )
                .remainingAmount(
                        BigDecimal.valueOf(
                                remainingAmount
                        )
                )
                .paymentStatus(
                        paymentStatus
                )
                .build();
    }
}