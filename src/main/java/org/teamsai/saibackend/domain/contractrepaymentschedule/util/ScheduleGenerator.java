package org.teamsai.saibackend.domain.contractrepaymentschedule.util;

import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.exception.RepaymentScheduleErrorCode;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ScheduleGenerator {

    private static final int CALCULATION_SCALE = 20;
    private static final int WON_SCALE = 0;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    public static List<RepaymentScheduleDTO> generateEqualPrincipalAndInterest(
            Long contractId, BigDecimal principal, BigDecimal annualInterestRate,
            int months, LocalDate startDate
    ) {
        BigDecimal monthlyRate = calculateMonthlyRate(annualInterestRate);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return generateZeroInterestRows(contractId, principal, months, startDate);
        }

        BigDecimal compoundFactor = BigDecimal.ONE.add(monthlyRate).pow(months);
        BigDecimal theoreticalMonthlyPayment = principal.multiply(monthlyRate).multiply(compoundFactor)
                .divide(compoundFactor.subtract(BigDecimal.ONE), CALCULATION_SCALE, RoundingMode.HALF_UP);

        List<BigDecimal> theoreticalInterests = new ArrayList<>();
        BigDecimal theoreticalRemainingPrincipal = principal;
        BigDecimal theoreticalTotalInterest = BigDecimal.ZERO;

        for (int i = 1; i <= months; i++) {
            BigDecimal theoreticalInterest = theoreticalRemainingPrincipal.multiply(monthlyRate);
            BigDecimal theoreticalPrincipal = (i == months)
                    ? theoreticalRemainingPrincipal
                    : theoreticalMonthlyPayment.subtract(theoreticalInterest);

            theoreticalInterests.add(theoreticalInterest);
            theoreticalTotalInterest = theoreticalTotalInterest.add(theoreticalInterest);
            theoreticalRemainingPrincipal = theoreticalRemainingPrincipal.subtract(theoreticalPrincipal);
        }

        BigDecimal finalizedTotalInterest = floorToWon(theoreticalTotalInterest);
        BigDecimal finalizedTotalPayment = principal.add(finalizedTotalInterest);
        BigDecimal regularTotalPayment = finalizedTotalPayment
                .divide(BigDecimal.valueOf(months), WON_SCALE, RoundingMode.DOWN);

        List<RepaymentScheduleDTO> schedules = new ArrayList<>();
        BigDecimal remainingPrincipal = principal;
        BigDecimal allocatedPrincipal = BigDecimal.ZERO;
        BigDecimal allocatedInterest = BigDecimal.ZERO;

        for (int i = 1; i <= months; i++) {
            BigDecimal principalDue;
            BigDecimal interestDue;

            if (i == months) {
                principalDue = principal.subtract(allocatedPrincipal);
                interestDue = finalizedTotalInterest.subtract(allocatedInterest);
            } else {
                interestDue = floorToWon(theoreticalInterests.get(i - 1));
                principalDue = regularTotalPayment.subtract(interestDue);
            }

            remainingPrincipal = remainingPrincipal.subtract(principalDue);
            allocatedPrincipal = allocatedPrincipal.add(principalDue);
            allocatedInterest = allocatedInterest.add(interestDue);

            schedules.add(buildScheduleRow(
                    contractId, i, startDate.plusMonths(i),
                    principalDue, interestDue, remainingPrincipal
            ));
        }

        validateSchedule(schedules, principal, finalizedTotalInterest);
        return schedules;
    }

    public static List<RepaymentScheduleDTO> generateEqualPrincipal(
            Long contractId, BigDecimal principal, BigDecimal annualInterestRate,
            int months, LocalDate startDate
    ) {
        BigDecimal monthlyRate = calculateMonthlyRate(annualInterestRate);
        BigDecimal monthlyPrincipal = principal
                .divide(BigDecimal.valueOf(months), WON_SCALE, RoundingMode.DOWN);

        List<BigDecimal> theoreticalInterests = new ArrayList<>();
        BigDecimal calculationRemainingPrincipal = principal;
        BigDecimal theoreticalTotalInterest = BigDecimal.ZERO;

        for (int i = 1; i <= months; i++) {
            BigDecimal theoreticalInterest = calculationRemainingPrincipal.multiply(monthlyRate);
            BigDecimal principalDue = (i == months) ? calculationRemainingPrincipal : monthlyPrincipal;

            theoreticalInterests.add(theoreticalInterest);
            theoreticalTotalInterest = theoreticalTotalInterest.add(theoreticalInterest);
            calculationRemainingPrincipal = calculationRemainingPrincipal.subtract(principalDue);
        }

        BigDecimal finalizedTotalInterest = floorToWon(theoreticalTotalInterest);
        List<RepaymentScheduleDTO> schedules = new ArrayList<>();
        BigDecimal remainingPrincipal = principal;
        BigDecimal allocatedInterest = BigDecimal.ZERO;

        for (int i = 1; i <= months; i++) {
            BigDecimal principalDue = (i == months) ? remainingPrincipal : monthlyPrincipal;
            BigDecimal interestDue = (i == months)
                    ? finalizedTotalInterest.subtract(allocatedInterest)
                    : floorToWon(theoreticalInterests.get(i - 1));

            remainingPrincipal = remainingPrincipal.subtract(principalDue);
            allocatedInterest = allocatedInterest.add(interestDue);

            schedules.add(buildScheduleRow(
                    contractId, i, startDate.plusMonths(i),
                    principalDue, interestDue, remainingPrincipal
            ));
        }

        validateSchedule(schedules, principal, finalizedTotalInterest);
        return schedules;
    }

    public static List<RepaymentScheduleDTO> generateBulletRepayment(
            Long contractId, BigDecimal principal, BigDecimal annualInterestRate,
            int months, LocalDate startDate
    ) {
        BigDecimal monthlyRate = calculateMonthlyRate(annualInterestRate);
        BigDecimal theoreticalMonthlyInterest = principal.multiply(monthlyRate);
        BigDecimal finalizedTotalInterest = floorToWon(
                theoreticalMonthlyInterest.multiply(BigDecimal.valueOf(months))
        );
        BigDecimal regularInterest = floorToWon(theoreticalMonthlyInterest);

        List<RepaymentScheduleDTO> schedules = new ArrayList<>();
        BigDecimal allocatedInterest = BigDecimal.ZERO;

        for (int i = 1; i <= months; i++) {
            BigDecimal principalDue = (i == months) ? principal : BigDecimal.ZERO;
            BigDecimal remainingPrincipal = (i == months) ? BigDecimal.ZERO : principal;
            BigDecimal interestDue = (i == months)
                    ? finalizedTotalInterest.subtract(allocatedInterest)
                    : regularInterest;

            allocatedInterest = allocatedInterest.add(interestDue);

            schedules.add(buildScheduleRow(
                    contractId, i, startDate.plusMonths(i),
                    principalDue, interestDue, remainingPrincipal
            ));
        }

        validateSchedule(schedules, principal, finalizedTotalInterest);
        return schedules;
    }

    private static BigDecimal calculateMonthlyRate(BigDecimal annualInterestRate) {
        return annualInterestRate
                .divide(HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP)
                .divide(MONTHS_PER_YEAR, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal floorToWon(BigDecimal amount) {
        return amount.setScale(WON_SCALE, RoundingMode.DOWN);
    }

    private static RepaymentScheduleDTO buildScheduleRow(
            Long contractId, int sequence, LocalDate dueDate,
            BigDecimal principalDue, BigDecimal interestDue, BigDecimal remainingPrincipal
    ) {
        validateWholeWon(principalDue);
        validateWholeWon(interestDue);
        validateWholeWon(remainingPrincipal);

        BigDecimal totalPaymentDue = principalDue.add(interestDue);
        validateWholeWon(totalPaymentDue);

        if (principalDue.signum() < 0 || interestDue.signum() < 0 || remainingPrincipal.signum() < 0) {
            throw RepaymentScheduleErrorCode.SCHEDULE_GENERATION_FAILED.toException();
        }

        return RepaymentScheduleDTO.builder()
                .contractId(contractId)
                .sequence(sequence)
                .dueDate(dueDate)
                .principalDue(principalDue)
                .interestDue(interestDue)
                .totalPaymentDue(totalPaymentDue)
                .remainingPrincipal(remainingPrincipal)
                .status(RepaymentScheduleStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static List<RepaymentScheduleDTO> generateZeroInterestRows(
            Long contractId, BigDecimal principal, int months, LocalDate startDate
    ) {
        BigDecimal monthlyPrincipal = principal
                .divide(BigDecimal.valueOf(months), WON_SCALE, RoundingMode.DOWN);
        List<RepaymentScheduleDTO> schedules = new ArrayList<>();
        BigDecimal remainingPrincipal = principal;

        for (int i = 1; i <= months; i++) {
            BigDecimal principalDue = (i == months) ? remainingPrincipal : monthlyPrincipal;
            remainingPrincipal = remainingPrincipal.subtract(principalDue);
            schedules.add(buildScheduleRow(
                    contractId, i, startDate.plusMonths(i),
                    principalDue, BigDecimal.ZERO, remainingPrincipal
            ));
        }

        validateSchedule(schedules, principal, BigDecimal.ZERO);
        return schedules;
    }

    private static void validateWholeWon(BigDecimal amount) {
        if (amount == null || amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw RepaymentScheduleErrorCode.SCHEDULE_GENERATION_FAILED.toException();
        }
    }

    private static void validateSchedule(
            List<RepaymentScheduleDTO> schedules,
            BigDecimal expectedPrincipal,
            BigDecimal expectedInterest
    ) {
        BigDecimal principalSum = schedules.stream()
                .map(RepaymentScheduleDTO::getPrincipalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interestSum = schedules.stream()
                .map(RepaymentScheduleDTO::getInterestDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (principalSum.compareTo(expectedPrincipal) != 0) {
            throw RepaymentScheduleErrorCode.SCHEDULE_GENERATION_FAILED.toException();
        }
        if (interestSum.compareTo(expectedInterest) != 0) {
            throw RepaymentScheduleErrorCode.SCHEDULE_GENERATION_FAILED.toException();
        }
        if (schedules.isEmpty()
                || schedules.get(schedules.size() - 1).getRemainingPrincipal().compareTo(BigDecimal.ZERO) != 0) {
            throw RepaymentScheduleErrorCode.SCHEDULE_GENERATION_FAILED.toException();
        }
    }
}
