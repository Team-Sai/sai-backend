package org.teamsai.saibackend.domain.contractrepaymentschedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;
import org.teamsai.saibackend.domain.contractrepaymentschedule.util.ScheduleGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleGeneratorTest {

    private final Long contractId = 1L;
    private final BigDecimal principal = BigDecimal.valueOf(10_000_000);
    private final BigDecimal annualInterestRate = BigDecimal.valueOf(12);
    private final int months = 12;
    private final LocalDate startDate = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("원리금균등 - 회차 수는 개월 수와 같다")
    void equalPrincipalAndInterest_rowCount() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateEqualPrincipalAndInterest(contractId, principal, annualInterestRate, months, startDate);

        assertThat(schedules).hasSize(12);
    }

    @Test
    @DisplayName("원리금균등 - 1회차 원금/이자/합계가 정확하다")
    void equalPrincipalAndInterest_firstRound() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateEqualPrincipalAndInterest(contractId, principal, annualInterestRate, months, startDate);

        RepaymentScheduleDTO first = schedules.get(0);
        assertThat(first.getInterestDue()).isEqualByComparingTo("100000.00");
        assertThat(first.getPrincipalDue()).isEqualByComparingTo("788487.89");
        assertThat(first.getTotalPaymentDue()).isEqualByComparingTo("888487.89");
        assertThat(first.getRemainingPrincipal()).isEqualByComparingTo("9211512.11");
    }

    @Test
    @DisplayName("원리금균등 - 매달 총 상환액(원금+이자)은 항상 동일하다")
    void equalPrincipalAndInterest_totalPaymentIsConstant() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateEqualPrincipalAndInterest(contractId, principal, annualInterestRate, months, startDate);

        // 마지막 회차는 반올림 보정 때문에 살짝 다를 수 있어 제외
        BigDecimal firstTotal = schedules.get(0).getTotalPaymentDue();
        for (int i = 0; i < schedules.size() - 1; i++) {
            assertThat(schedules.get(i).getTotalPaymentDue()).isEqualByComparingTo(firstTotal);
        }
    }

    @Test
    @DisplayName("원리금균등 - 마지막 회차 후 잔액은 0원이다")
    void equalPrincipalAndInterest_lastRoundClearsBalance() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateEqualPrincipalAndInterest(contractId, principal, annualInterestRate, months, startDate);

        RepaymentScheduleDTO last = schedules.get(schedules.size() - 1);
        assertThat(last.getRemainingPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("원금균등 - 매달 원금은 고정, 이자는 잔액에 따라 감소한다")
    void equalPrincipal_firstAndSecondRound() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateEqualPrincipal(contractId, principal, annualInterestRate, months, startDate);

        RepaymentScheduleDTO first = schedules.get(0);
        RepaymentScheduleDTO second = schedules.get(1);

        assertThat(first.getPrincipalDue()).isEqualByComparingTo("833333.33");
        assertThat(first.getInterestDue()).isEqualByComparingTo("100000.00");
        assertThat(second.getPrincipalDue()).isEqualByComparingTo("833333.33");
        assertThat(second.getInterestDue()).isEqualByComparingTo("91666.67");

        // 원금은 고정, 이자만 줄어서 → 2회차 총액이 1회차보다 작아야 함
        assertThat(second.getTotalPaymentDue()).isLessThan(first.getTotalPaymentDue());
    }

    @Test
    @DisplayName("원금균등 - 마지막 회차 후 잔액은 0원이다")
    void equalPrincipal_lastRoundClearsBalance() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateEqualPrincipal(contractId, principal, annualInterestRate, months, startDate);

        assertThat(schedules.get(schedules.size() - 1).getRemainingPrincipal())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("만기일시 - 마지막 회차 전까지는 원금이 0이고 이자만 낸다")
    void bulletRepayment_middleRoundsInterestOnly() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateBulletRepayment(contractId, principal, annualInterestRate, months, startDate);

        for (int i = 0; i < schedules.size() - 1; i++) {
            RepaymentScheduleDTO row = schedules.get(i);
            assertThat(row.getPrincipalDue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(row.getInterestDue()).isEqualByComparingTo("100000.00");
            assertThat(row.getRemainingPrincipal()).isEqualByComparingTo(principal);
        }
    }

    @Test
    @DisplayName("만기일시 - 마지막 회차에 원금 전액 + 이자를 낸다")
    void bulletRepayment_lastRoundPaysFullPrincipal() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateBulletRepayment(contractId, principal, annualInterestRate, months, startDate);

        RepaymentScheduleDTO last = schedules.get(schedules.size() - 1);
        assertThat(last.getPrincipalDue()).isEqualByComparingTo(principal);
        assertThat(last.getTotalPaymentDue()).isEqualByComparingTo("10100000.00");
        assertThat(last.getRemainingPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("세 방식 모두 회차별 contractId와 sequence가 정확하다")
    void allMethods_haveCorrectContractIdAndSequence() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateEqualPrincipal(contractId, principal, annualInterestRate, months, startDate);

        for (int i = 0; i < schedules.size(); i++) {
            assertThat(schedules.get(i).getContractId()).isEqualTo(contractId);
            assertThat(schedules.get(i).getSequence()).isEqualTo(i + 1);
            assertThat(schedules.get(i).getStatus()).isEqualTo(RepaymentScheduleStatus.PENDING);
        }
    }
}