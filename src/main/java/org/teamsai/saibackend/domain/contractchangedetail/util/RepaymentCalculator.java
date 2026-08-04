package org.teamsai.saibackend.domain.contractchangedetail.util;



import org.teamsai.saibackend.domain.contractchangedetail.exception.ChangeRequestDetailErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

public class RepaymentCalculator {
    public static BigDecimal calculate(
                BigDecimal principal,
                BigDecimal annualInterestRate,
                String repaymentType,
                LocalDate startDate,
                LocalDate maturityDate
    ) {
        int months = calculateMonths(startDate, maturityDate);

        return switch (repaymentType) {
            case "BULLET_REPAYMENT" -> calculateBulletRepayment(principal, annualInterestRate);
            case "EQUAL_PRINCIPAL" -> calculateEqualPrincipal(principal, annualInterestRate, months);
            case "EQUAL_PRINCIPAL_AND_INTEREST" -> calculateEqualPrincipalAndInterest(principal, annualInterestRate, months);
            default -> throw ChangeRequestDetailErrorCode.UNKNOWN_REPAYMENT_TYPE.toException();        };
    }

    private static int calculateMonths(LocalDate startDate, LocalDate maturityDate) {
        Period period = Period.between(startDate, maturityDate);
        return period.getYears() * 12 + period.getMonths();
    }

    private static BigDecimal calculateBulletRepayment(BigDecimal principal, BigDecimal annualInterestRate) {
        return principal
        .multiply(annualInterestRate)
        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateEqualPrincipal(BigDecimal principal, BigDecimal annualInterestRate, int months) {
        BigDecimal monthlyPrincipal = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        BigDecimal firstMonthInterest = calculateBulletRepayment(principal, annualInterestRate);

        return monthlyPrincipal.add(firstMonthInterest);

    }

    private static BigDecimal calculateEqualPrincipalAndInterest(BigDecimal principal, BigDecimal annualInterestRate, int months) {
        BigDecimal monthlyRate = annualInterestRate
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal compoundFactor = BigDecimal.ONE.add(monthlyRate).pow(months);
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(compoundFactor);
        BigDecimal denominator = compoundFactor.subtract(BigDecimal.ONE);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
