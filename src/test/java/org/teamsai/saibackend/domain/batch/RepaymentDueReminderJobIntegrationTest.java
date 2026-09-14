package org.teamsai.saibackend.domain.batch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.teamsai.saibackend.domain.batch.common.listener.LoggingJobExecutionListener;
import org.teamsai.saibackend.domain.batch.common.notification.SlackNotifier;
import org.teamsai.saibackend.domain.batch.repaymentschedule.config.RepaymentDueReminderJobConfig;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        RepaymentDueReminderJobConfig.class,
        org.teamsai.saibackend.domain.batch.common.config.BatchInfraConfig.class,
        LoggingJobExecutionListener.class,
        org.teamsai.saibackend.domain.batch.common.listener.BaseSkipListener.class,  // ← 추가
        RepaymentDueReminderJobIntegrationTest.TestSliceConfig.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@DisplayName("repaymentDueReminderJob 통합 테스트")
class RepaymentDueReminderJobIntegrationTest {

    @TestConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackages = "org.teamsai.saibackend.domain")
    @ComponentScan(basePackages = {
            "org.teamsai.saibackend.domain.notification",
    })
    static class TestSliceConfig {

        @Bean
        SlackNotifier slackNotifier() {
            return org.mockito.Mockito.mock(SlackNotifier.class);
        }
    }

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job repaymentDueReminderJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM repayment_schedule WHERE contract_id >= 90000");
        jdbcTemplate.update("DELETE FROM contract_account WHERE contract_id >= 90000");
        jdbcTemplate.update("DELETE FROM loan_contract_change_request WHERE contract_id >= 90000");
        jdbcTemplate.update("UPDATE loan_contract SET previous_contract_id = NULL WHERE contract_id >= 90000"); // 자기참조 끊기
        jdbcTemplate.update("DELETE FROM loan_contract WHERE contract_id >= 90000");
        jdbcTemplate.update("DELETE FROM notification WHERE user_id >= 90000");
        jdbcTemplate.update("DELETE FROM users WHERE user_id >= 90000");
    }

    private JobExecution launch(LocalDate baseDate) throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("baseDate", baseDate.toString())
                .addLong("uniqueness", System.nanoTime())
                .toJobParameters();
        return jobOperator.start(repaymentDueReminderJob, jobParameters);
    }

    @Nested
    @DisplayName("D-3 / D-1 / D-day 대상 판정")
    class StageMatching {

        @Test
        @DisplayName("dueDate가 baseDate+3이면 D3 알림이 생성된다")
        void createsD3Notification() throws Exception {
            insertUser(91001L, "채권자");
            insertUser(91002L, "채무자");
            insertLoanContract(91101L, 91001L, 91002L);
            insertSchedule(91201L, 91101L, LocalDate.of(2026, 1, 14));

            JobExecution execution = launch(LocalDate.of(2026, 1, 11));

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countNotifications(91002L, "REPAYMENT_DUE_REMINDER_D3")).isEqualTo(1);
        }

        @Test
        @DisplayName("dueDate가 baseDate+1이면 D1 알림이 생성된다")
        void createsD1Notification() throws Exception {
            insertUser(92001L, "채권자");
            insertUser(92002L, "채무자");
            insertLoanContract(92101L, 92001L, 92002L);
            insertSchedule(92201L, 92101L, LocalDate.of(2026, 1, 12));

            JobExecution execution = launch(LocalDate.of(2026, 1, 11));

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countNotifications(92002L, "REPAYMENT_DUE_REMINDER_D1")).isEqualTo(1);
        }

        @Test
        @DisplayName("dueDate가 baseDate와 같으면 DDAY 알림이 생성된다")
        void createsDDayNotification() throws Exception {
            insertUser(93001L, "채권자");
            insertUser(93002L, "채무자");
            insertLoanContract(93101L, 93001L, 93002L);
            insertSchedule(93201L, 93101L, LocalDate.of(2026, 1, 11));

            JobExecution execution = launch(LocalDate.of(2026, 1, 11));

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countNotifications(93002L, "REPAYMENT_DUE_REMINDER_DDAY")).isEqualTo(1);
        }

        @Test
        @DisplayName("D-3/D-1/D-day에 해당하지 않으면 알림이 생성되지 않는다")
        void createsNoNotificationOutsideWindow() throws Exception {
            insertUser(94001L, "채권자");
            insertUser(94002L, "채무자");
            insertLoanContract(94101L, 94001L, 94002L);
            insertSchedule(94201L, 94101L, LocalDate.of(2026, 1, 20));

            JobExecution execution = launch(LocalDate.of(2026, 1, 11));

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notification WHERE user_id = ?", Integer.class, 94002L);
            assertThat(total).isZero();
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("같은 baseDate로 재실행해도 알림이 중복 생성되지 않는다")
        void doesNotDuplicateNotificationOnRerun() throws Exception {
            insertUser(95001L, "채권자");
            insertUser(95002L, "채무자");
            insertLoanContract(95101L, 95001L, 95002L);
            insertSchedule(95201L, 95101L, LocalDate.of(2026, 1, 11));

            launch(LocalDate.of(2026, 1, 11));
            launch(LocalDate.of(2026, 1, 11));

            assertThat(countNotifications(95002L, "REPAYMENT_DUE_REMINDER_DDAY")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("부분 실패 격리")
    class PartialFailureIsolation {

        @Test
        @DisplayName("한 스케줄의 채무자 조회가 실패해도 나머지 스케줄은 정상 처리된다")
        void continuesOtherSchedulesWhenDebtorLookupFails() throws Exception {
            insertUser(96001L, "채권자");
            insertUser(96002L, "채무자B");

            // debtor_id를 NULL로 둔 계약 -> findDebtorUserIdByContractId가 null 반환
            insertLoanContractWithoutDebtor(96101L, 96001L);
            insertSchedule(96201L, 96101L, LocalDate.of(2026, 1, 11));

            insertLoanContract(96102L, 96001L, 96002L);
            insertSchedule(96202L, 96102L, LocalDate.of(2026, 1, 11));

            JobExecution execution = launch(LocalDate.of(2026, 1, 11));

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(countNotifications(96002L, "REPAYMENT_DUE_REMINDER_DDAY")).isEqualTo(1);
        }
    }

    private int countNotifications(Long userId, String type) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND notification_type = ?",
                Integer.class, userId, type);
        return count == null ? 0 : count;
    }

    private void insertUser(Long userId, String name) {
        String unique = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO users (user_id, user_token, email, password, name, birth_date)
                VALUES (?, ?, ?, ?, ?, '2000-01-01')
                """,
                userId, unique, unique + "@example.com", "password", name
        );
    }

    private void insertLoanContract(Long contractId, Long creditorId, Long debtorId) {
        jdbcTemplate.update(
                """
                INSERT INTO loan_contract (
                    contract_id, creditor_id, debtor_id,
                    principal_amount, interest_rate, repayment_type,
                    start_date, maturity_date, repayment_day,
                    creditor_address, contract_alias,
                    created_at, updated_at
                )
                VALUES (
                    ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?,
                    ?, ?,
                    NOW(), NOW()
                )
                """,
                contractId, creditorId, debtorId,
                new java.math.BigDecimal("100000000"), new java.math.BigDecimal("5.0"), "EQUAL_PRINCIPAL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 1,
                "서울시 테스트구", "테스트 계약"
        );
    }

    private void insertLoanContractWithoutDebtor(Long contractId, Long creditorId) {
        jdbcTemplate.update(
                """
                INSERT INTO loan_contract (
                    contract_id, creditor_id, debtor_id,
                    principal_amount, interest_rate, repayment_type,
                    start_date, maturity_date, repayment_day,
                    creditor_address, contract_alias,
                    created_at, updated_at
                )
                VALUES (
                    ?, ?, NULL,
                    ?, ?, ?,
                    ?, ?, ?,
                    ?, ?,
                    NOW(), NOW()
                )
                """,
                contractId, creditorId,
                new java.math.BigDecimal("100000000"), new java.math.BigDecimal("5.0"), "EQUAL_PRINCIPAL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 1,
                "서울시 테스트구", "테스트 계약"
        );
    }

    private void insertSchedule(Long scheduleId, Long contractId, LocalDate dueDate) {
        jdbcTemplate.update(
                """
                INSERT INTO repayment_schedule (
                    schedule_id, contract_id, sequence, due_date,
                    principal_due, interest_due, total_payment_due, remaining_principal,
                    status, created_at
                )
                VALUES (?, ?, 1, ?, 100000, 5000, 105000, 100000, 'PENDING', NOW())
                """,
                scheduleId, contractId, dueDate
        );
    }
}