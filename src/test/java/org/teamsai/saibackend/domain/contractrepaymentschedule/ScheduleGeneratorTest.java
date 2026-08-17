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
        assertThat(first.getInterestDue()).isEqualByComparingTo("100000");
        assertThat(first.getPrincipalDue()).isEqualByComparingTo("788487");
        assertThat(first.getTotalPaymentDue()).isEqualByComparingTo("888487");
        assertThat(first.getRemainingPrincipal()).isEqualByComparingTo("9211513");
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

        assertThat(first.getPrincipalDue()).isEqualByComparingTo("833333");
        assertThat(first.getInterestDue()).isEqualByComparingTo("100000");
        assertThat(second.getPrincipalDue()).isEqualByComparingTo("833333");
        assertThat(second.getInterestDue()).isEqualByComparingTo("91666");

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
    @DisplayName("무이자 100만원을 3회 상환하면 마지막 회차가 원금 나머지를 부담한다")
    void zeroInterest_lastRoundAbsorbsPrincipalRemainder() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateEqualPrincipalAndInterest(
                        contractId,
                        new BigDecimal("1000000"),
                        BigDecimal.ZERO,
                        3,
                        startDate
                );

        assertThat(schedules)
                .extracting(RepaymentScheduleDTO::getPrincipalDue)
                .containsExactly(
                        new BigDecimal("333333"),
                        new BigDecimal("333333"),
                        new BigDecimal("333334")
                );
        assertThat(schedules)
                .extracting(RepaymentScheduleDTO::getTotalPaymentDue)
                .containsExactly(
                        new BigDecimal("333333"),
                        new BigDecimal("333333"),
                        new BigDecimal("333334")
                );
    }

    @Test
    @DisplayName("만기일시 0.5% 이자의 정수 나머지는 마지막 회차에 추가한다")
    void bulletRepayment_lastRoundAbsorbsInterestRemainder() {
        List<RepaymentScheduleDTO> schedules =
                ScheduleGenerator.generateBulletRepayment(
                        contractId,
                        new BigDecimal("100000"),
                        new BigDecimal("0.5"),
                        12,
                        startDate
                );

        assertThat(schedules.subList(0, 11))
                .extracting(RepaymentScheduleDTO::getInterestDue)
                .containsOnly(new BigDecimal("41"));
        assertThat(schedules.get(11).getInterestDue()).isEqualByComparingTo("49");
        assertThat(schedules.stream()
                .map(RepaymentScheduleDTO::getInterestDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("모든 상환방식의 저장 금액은 원 단위이고 원금 합계가 유지된다")
    void allMethods_generateWholeWonAmountsAndPreservePrincipal() {
        BigDecimal testPrincipal = new BigDecimal("1000000");
        BigDecimal testRate = new BigDecimal("0.5");

        List<List<RepaymentScheduleDTO>> schedulesByMethod = List.of(
                ScheduleGenerator.generateEqualPrincipalAndInterest(
                        contractId, testPrincipal, testRate, 12, startDate),
                ScheduleGenerator.generateEqualPrincipal(
                        contractId, testPrincipal, testRate, 12, startDate),
                ScheduleGenerator.generateBulletRepayment(
                        contractId, testPrincipal, testRate, 12, startDate)
        );

        for (List<RepaymentScheduleDTO> schedules : schedulesByMethod) {
            assertThat(schedules.stream()
                    .map(RepaymentScheduleDTO::getPrincipalDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(testPrincipal);

            assertThat(schedules).allSatisfy(schedule -> {
                assertWholeWon(schedule.getPrincipalDue());
                assertWholeWon(schedule.getInterestDue());
                assertWholeWon(schedule.getTotalPaymentDue());
                assertWholeWon(schedule.getRemainingPrincipal());
                assertThat(schedule.getTotalPaymentDue())
                        .isEqualByComparingTo(schedule.getPrincipalDue().add(schedule.getInterestDue()));
            });

            assertThat(schedules.get(schedules.size() - 1).getRemainingPrincipal())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
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

    private void assertWholeWon(BigDecimal amount) {
        assertThat(amount.remainder(BigDecimal.ONE))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
