package org.teamsai.saibackend.domain.contractrepaymentschedule.util;

import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ScheduleGenerator {

    public static List<RepaymentScheduleDTO> generateEqualPrincipalAndInterest(
            Long contractId, BigDecimal principal, BigDecimal annualInterestRate,
            int months, LocalDate startDate
    ) {
        BigDecimal monthlyRate = calculateMonthlyRate(annualInterestRate);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return generateZeroInterestRows(contractId, principal, months, startDate);
        }

        BigDecimal compoundFactor = BigDecimal.ONE.add(monthlyRate).pow(months);
        BigDecimal monthlyPayment = principal.multiply(monthlyRate).multiply(compoundFactor)
                .divide(compoundFactor.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);

        List<RepaymentScheduleDTO> schedules = new ArrayList<>();
        BigDecimal remainingPrincipal = principal;
        for (int i = 1; i <= months; i++) {
            BigDecimal interestDue = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalDue = (i == months) ? remainingPrincipal : monthlyPayment.subtract(interestDue);
            remainingPrincipal = remainingPrincipal.subtract(principalDue);
            schedules.add(buildScheduleRow(contractId, i, startDate.plusMonths(i), principalDue, interestDue, remainingPrincipal));
        }
        return schedules;
    }

    public static List<RepaymentScheduleDTO> generateEqualPrincipal(
            Long contractId, BigDecimal principal, BigDecimal annualInterestRate,
            int months, LocalDate startDate
    ) {
        BigDecimal monthlyRate = calculateMonthlyRate(annualInterestRate);
        BigDecimal monthlyPrincipal = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        List<RepaymentScheduleDTO> schedules = new ArrayList<>();
        BigDecimal remainingPrincipal = principal;

        for (int i = 1; i <= months; i++) {
            BigDecimal interestDue = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalDue = (i == months) ? remainingPrincipal : monthlyPrincipal;
            remainingPrincipal = remainingPrincipal.subtract(principalDue);

            schedules.add(buildScheduleRow(contractId, i, startDate.plusMonths(i), principalDue, interestDue, remainingPrincipal));
        }
        return schedules;
    }

    public static List<RepaymentScheduleDTO> generateBulletRepayment(
            Long contractId, BigDecimal principal, BigDecimal annualInterestRate,
            int months, LocalDate startDate
    ) {
        BigDecimal monthlyRate = calculateMonthlyRate(annualInterestRate);
        BigDecimal interestDue = principal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);

        List<RepaymentScheduleDTO> schedules = new ArrayList<>();

        for (int i = 1; i <= months; i++) {
            BigDecimal principalDue = (i == months) ? principal : BigDecimal.ZERO;
            BigDecimal remainingPrincipal = (i == months) ? BigDecimal.ZERO : principal;

            schedules.add(buildScheduleRow(contractId, i, startDate.plusMonths(i), principalDue, interestDue, remainingPrincipal));
        }
        return schedules;
    }

    private static BigDecimal calculateMonthlyRate(BigDecimal annualInterestRate) {
        return annualInterestRate
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
    }

    private static RepaymentScheduleDTO buildScheduleRow(
            Long contractId, int sequence, LocalDate dueDate,
            BigDecimal principalDue, BigDecimal interestDue, BigDecimal remainingPrincipal
    ) {
        return RepaymentScheduleDTO.builder()
                .contractId(contractId)
                .sequence(sequence)
                .dueDate(dueDate)
                .principalDue(principalDue)
                .interestDue(interestDue)
                .totalPaymentDue(principalDue.add(interestDue))
                .remainingPrincipal(remainingPrincipal)
                .status(RepaymentScheduleStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static List<RepaymentScheduleDTO> generateZeroInterestRows(
            Long contractId, BigDecimal principal, int months, LocalDate startDate
    ) {
        BigDecimal monthlyPrincipal = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        List<RepaymentScheduleDTO> schedules = new ArrayList<>();
        BigDecimal remainingPrincipal = principal;
        for (int i = 1; i <= months; i++) {
            BigDecimal principalDue = (i == months) ? remainingPrincipal : monthlyPrincipal;
            remainingPrincipal = remainingPrincipal.subtract(principalDue);
            schedules.add(buildScheduleRow(contractId, i, startDate.plusMonths(i), principalDue, BigDecimal.ZERO, remainingPrincipal));
        }
        return schedules;
    }
}