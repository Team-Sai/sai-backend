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

    @Test
    @DisplayName(
            "인원수가 0이면 ArithmeticException이 아니라 도메인 예외가 발생한다"
    )
    void calculateEqualAmountFailsWhenParticipantCountIsZero() {
        // calculateEqualAmount(totalAmount, 0)은 내부적으로 calculateEqualAmountForTotalCount(totalAmount, 1)이 되어
        // 정상 계산되므로, 0 이하 검증은 calculateEqualAmountForTotalCount를 직접 호출해서 확인한다
        assertSettlementExceptionThrownBy(
                () ->
                        settlementAmountCalculator
                                .calculateEqualAmountForTotalCount(
                                        new BigDecimal("300000"),
                                        0
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_AMOUNT
        );
    }

    @Test
    @DisplayName(
            "인원수가 음수이면 도메인 예외가 발생한다"
    )
    void calculateEqualAmountForTotalCountFailsWhenParticipantCountIsNegative() {
        assertSettlementExceptionThrownBy(
                () ->
                        settlementAmountCalculator
                                .calculateEqualAmountForTotalCount(
                                        new BigDecimal("300000"),
                                        -1
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_AMOUNT
        );
    }

    @Test
    @DisplayName(
            "총금액을 전체 인원으로 균등 분배하되, 나머지 없이 정확히 나누어떨어지면 모두 동일한 금액이다"
    )
    void distributeEqualAmountsSuccessWhenDivisible() {
        BigDecimal totalAmount = new BigDecimal("300000");
        int totalParticipantCount = 3;

        java.util.List<BigDecimal> result =
                settlementAmountCalculator.distributeEqualAmounts(
                        totalAmount,
                        totalParticipantCount
                );

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(amount ->
                assertThat(amount).isEqualByComparingTo("100000"));
    }

    @Test
    @DisplayName(
            "총금액이 인원수로 나누어떨어지지 않으면 나머지를 마지막 참여자에게 배정하여 합계를 총금액과 일치시킨다"
    )
    void distributeEqualAmountsAssignsRemainderToLastParticipant() {
        BigDecimal totalAmount = new BigDecimal("10000");
        int totalParticipantCount = 3;

        java.util.List<BigDecimal> result =
                settlementAmountCalculator.distributeEqualAmounts(
                        totalAmount,
                        totalParticipantCount
                );

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualByComparingTo("3333");
        assertThat(result.get(1)).isEqualByComparingTo("3333");
        assertThat(result.get(2)).isEqualByComparingTo("3334");

        BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(totalAmount);
    }

    @Test
    @DisplayName(
            "1명에게 분배하면 전액이 그대로 배정된다"
    )
    void distributeEqualAmountsAssignsFullAmountToSingleParticipant() {
        BigDecimal totalAmount = new BigDecimal("150000");

        java.util.List<BigDecimal> result =
                settlementAmountCalculator.distributeEqualAmounts(
                        totalAmount,
                        1
                );

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualByComparingTo(totalAmount);
    }

    @Test
    @DisplayName(
            "distributeEqualAmounts도 총금액이 0이면 예외가 발생한다"
    )
    void distributeEqualAmountsFailsWhenTotalAmountIsZero() {
        assertSettlementExceptionThrownBy(
                () ->
                        settlementAmountCalculator
                                .distributeEqualAmounts(
                                        BigDecimal.ZERO,
                                        3
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_AMOUNT
        );
    }

    @Test
    @DisplayName(
            "distributeEqualAmounts도 인원수가 0 이하이면 도메인 예외가 발생한다"
    )
    void distributeEqualAmountsFailsWhenParticipantCountIsZeroOrLess() {
        assertSettlementExceptionThrownBy(
                () ->
                        settlementAmountCalculator
                                .distributeEqualAmounts(
                                        new BigDecimal("300000"),
                                        0
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