package org.teamsai.saibackend.domain.contractchangedetail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.contractchangedetail.util.RepaymentCalculator;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RepaymentCalculator 단위 테스트")
class RepaymentCalculatorTest {

    private static final BigDecimal PRINCIPAL = BigDecimal.valueOf(100_000_000);
    private static final BigDecimal INTEREST_RATE = BigDecimal.valueOf(4.5);
    private static final LocalDate START_DATE = LocalDate.of(2025, 1, 1);
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("만기일시상환 — 원금 × 이율/12")
    void calculateBulletRepayment() {
        BigDecimal result = RepaymentCalculator.calculate(
                PRINCIPAL, INTEREST_RATE, "BULLET_REPAYMENT", START_DATE, MATURITY_DATE
        );

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(375000));
    }

    @Test
    @DisplayName("원금균등상환 — 매달 원금 + 1회차 이자")
    void calculateEqualPrincipal() {
        BigDecimal result = RepaymentCalculator.calculate(
                PRINCIPAL, INTEREST_RATE, "EQUAL_PRINCIPAL", START_DATE, MATURITY_DATE
        );

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(8708333.33));
    }

    @Test
    @DisplayName("원리금균등상환 — 매달 동일 금액")
    void calculateEqualPrincipalAndInterest() {
        BigDecimal result = RepaymentCalculator.calculate(
                PRINCIPAL, INTEREST_RATE, "EQUAL_PRINCIPAL_AND_INTEREST", START_DATE, MATURITY_DATE
        );

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(8537852.16));

    }

    @Test
    @DisplayName("알 수 없는 상환방식이면 예외가 발생한다")
    void calculateThrowsWhenUnknownType() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                RepaymentCalculator.calculate(
                        PRINCIPAL, INTEREST_RATE, "이상한방식", START_DATE, MATURITY_DATE
                )
        ).isInstanceOf(DomainException.class);
    }
}
