package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.service.SettlementAmountCalculator;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SettlementAmountCalculator 단위 테스트")
class SettlementAmountCalculatorTest {

    private final SettlementAmountCalculator settlementAmountCalculator =
            new SettlementAmountCalculator();


    @Test
    @DisplayName(
            "총금액을 생성자 포함 전체 인원으로 균등 분배한다"
    )
    void calculateEqualAmountSuccess() {

        BigDecimal totalAmount =
                new BigDecimal("450000");

        int participantCount = 2;


        BigDecimal result =
                settlementAmountCalculator.calculateEqualAmount(
                        totalAmount,
                        participantCount
                );


        assertThat(result)
                .isEqualByComparingTo("150000");
    }


    @Test
    @DisplayName(
            "총금액이 인원수로 나누어떨어지지 않으면 원 단위로 내림한다"
    )
    void calculateEqualAmountRoundsDown() {

        BigDecimal totalAmount =
                new BigDecimal("10000");

        int participantCount = 2;


        BigDecimal result =
                settlementAmountCalculator.calculateEqualAmount(
                        totalAmount,
                        participantCount
                );


        assertThat(result)
                .isEqualByComparingTo("3333");
    }


    @Test
    @DisplayName(
            "총금액이 0이면 예외가 발생한다"
    )
    void calculateEqualAmountFailsWhenTotalAmountIsZero() {

        BigDecimal totalAmount =
                BigDecimal.ZERO;


        assertSettlementExceptionThrownBy(
                () ->
                        settlementAmountCalculator
                                .calculateEqualAmount(
                                        totalAmount,
                                        2
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_AMOUNT
        );
    }


    @Test
    @DisplayName(
            "총금액이 음수이면 예외가 발생한다"
    )
    void calculateEqualAmountFailsWhenTotalAmountIsNegative() {

        BigDecimal totalAmount =
                new BigDecimal("-10000");


        assertSettlementExceptionThrownBy(
                () ->
                        settlementAmountCalculator
                                .calculateEqualAmount(
                                        totalAmount,
                                        2
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_AMOUNT
        );
    }


    @Test
    @DisplayName(
            "총금액이 없으면 예외가 발생한다"
    )
    void calculateEqualAmountFailsWhenTotalAmountIsNull() {

        assertSettlementExceptionThrownBy(
                () ->
                        settlementAmountCalculator
                                .calculateEqualAmount(
                                        null,
                                        2
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_AMOUNT
        );
    }


    @Test
    @DisplayName(
            "인원수에 비해 총금액이 너무 작아 1인당 금액이 0원이 되면 예외가 발생한다"
    )
    void calculateEqualAmountFailsWhenPerPersonAmountIsZero() {

        BigDecimal totalAmount =
                BigDecimal.ONE;

        int participantCount = 2;


        assertSettlementExceptionThrownBy(
                () ->
                        settlementAmountCalculator
                                .calculateEqualAmount(
                                        totalAmount,
                                        participantCount
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_AMOUNT
        );
    }


    private void assertSettlementExceptionThrownBy(
            Runnable operation,
            SettlementErrorCode errorCode
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