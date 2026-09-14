package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.settlement.service.OverdueSettlementService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@Sql(scripts = {
        "/db/user.sql",
        "/db/settlement.sql",
        "/db/settlement_participant.sql",
        "/db/payment.sql"
})
@DisplayName("OverdueSettlementService 엔드투엔드 통합 테스트")
class OverdueSettlementServiceIntegrationTest {

    @Autowired
    private OverdueSettlementService overdueSettlementService;

    @Autowired
    private SettlementPaymentService settlementPaymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM bank_transaction_match_candidate WHERE bank_transaction_id >= 90000"
        );
        jdbcTemplate.update("DELETE FROM payment_record WHERE bank_transaction_id >= 90000");
        jdbcTemplate.update("DELETE FROM bank_transaction WHERE bank_transaction_id >= 90000");
        jdbcTemplate.update("DELETE FROM settlement_account WHERE settlement_id >= 90000");
        jdbcTemplate.update("DELETE FROM linked_bank_account WHERE linked_account_id >= 90000");
        jdbcTemplate.update("DELETE FROM payment_obligation WHERE payment_obligation_id >= 90000");
        jdbcTemplate.update("DELETE FROM settlement_participant WHERE participant_id >= 90000");
        jdbcTemplate.update("DELETE FROM settlement WHERE settlement_id >= 90000");
        jdbcTemplate.update("DELETE FROM recurring_settlement WHERE recurring_settlement_id >= 90000"); // 추가
        jdbcTemplate.update("DELETE FROM users WHERE user_id >= 90000");
    }

    @Nested
    @DisplayName("SHARED 정산 연체 판정")
    class SharedSettlementOverdue {

        @Test
        @DisplayName("dueDate가 지난 IN_PROGRESS 정산의 미납 obligation에 overdueSince가 채워진다")
        void marksOverdueWhenDueDatePassed() {
            insertUser(91001L, "채권자");
            insertUser(91002L, "채무자");
            insertSharedSettlement(91101L, 91001L, LocalDate.of(2026, 1, 10));
            insertParticipant(91201L, 91101L, 91002L, "ACTIVE");
            insertObligation(91301L, 91201L, "UNPAID");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));

            LocalDateTime overdueSince = fetchOverdueSince(91301L);
            assertThat(overdueSince).isEqualTo(LocalDate.of(2026, 1, 11).atStartOfDay());
        }

        @Test
        @DisplayName("dueDate가 아직 안 지났으면 overdueSince가 채워지지 않는다")
        void doesNotMarkWhenNotYetDue() {
            insertUser(91001L, "채권자");
            insertUser(91002L, "채무자");
            insertSharedSettlement(91101L, 91001L, LocalDate.of(2026, 2, 28));
            insertParticipant(91201L, 91101L, 91002L, "ACTIVE");
            insertObligation(91301L, 91201L, "UNPAID");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));

            assertThat(fetchOverdueSince(91301L)).isNull();
        }

        @Test
        @DisplayName("이미 overdueSince가 채워진 obligation은 재실행해도 시점이 갱신되지 않는다 (멱등성)")
        void doesNotOverwriteExistingOverdueSince() {
            insertUser(91001L, "채권자");
            insertUser(91002L, "채무자");
            insertSharedSettlement(91101L, 91001L, LocalDate.of(2026, 1, 10));
            insertParticipant(91201L, 91101L, 91002L, "ACTIVE");
            insertObligation(91301L, 91201L, "UNPAID");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));
            LocalDateTime firstOverdueSince = fetchOverdueSince(91301L);

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 15)); // 며칠 뒤 재실행
            LocalDateTime secondOverdueSince = fetchOverdueSince(91301L);

            assertThat(secondOverdueSince).isEqualTo(firstOverdueSince); // 최초 연체일 그대로 유지
        }

        @Test
        @DisplayName("배치가 며칠 밀려 실행돼도 overdueSince는 baseDate가 아닌 실제 dueDate로 기록된다")
        void recordsActualDueDateNotBatchExecutionDate() {
            insertUser(99001L, "채권자");
            insertUser(99002L, "채무자");
            insertSharedSettlement(99101L, 99001L, LocalDate.of(2026, 1, 10));
            insertParticipant(99201L, 99101L, 99002L, "ACTIVE");
            insertObligation(99301L, 99201L, "UNPAID");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 15));

            assertThat(fetchOverdueSince(99301L)).isEqualTo(LocalDate.of(2026, 1, 11).atStartOfDay());
        }
    }

    @Nested
    @DisplayName("RECURRING 정산 연체 판정")
    class RecurringSettlementOverdue {

        @Test
        @DisplayName("cycleDate가 지난 RECURRING 정산도 연체로 판정된다 (dueDate는 null)")
        void marksOverdueForRecurringUsingCycleDate() {
            insertUser(92001L, "관리자");
            insertUser(92002L, "참여자");
            insertRecurringSettlement(92101L, 92001L, LocalDate.of(2026, 1, 31));
            insertParticipant(92201L, 92101L, 92002L, "ACTIVE");
            insertObligation(92301L, 92201L, "UNPAID");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 2, 1));

            assertThat(fetchOverdueSince(92301L)).isEqualTo(LocalDate.of(2026, 2, 1).atStartOfDay()); // baseDate(2/1)가 아니라 cycleDate(1/31)
        }
    }

    @Nested
    @DisplayName("완납 시 연체 해제")
    class ClearOverdueOnFullPayment {

        @Test
        @DisplayName("연체 상태에서 완납하면 overdueSince가 해제된다")
        void clearsOverdueSinceWhenFullyPaid() {
            insertUser(93001L, "채권자");
            insertUser(93002L, "채무자");
            insertLinkedAccount(93401L, 93001L);
            insertSharedSettlement(93101L, 93001L, LocalDate.of(2026, 1, 10));
            insertParticipant(93201L, 93101L, 93002L, "ACTIVE");
            insertObligation(93301L, 93201L, "UNPAID", "150000.00");
            insertBankTransaction(93501L, 93401L, "150000.00");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));
            assertThat(fetchOverdueSince(93301L)).isNotNull();

            settlementPaymentService.applyAutoMatchedPayment(93301L, 93501L, new BigDecimal("150000.00"));

            assertThat(fetchOverdueSince(93301L)).isNull();
        }

        @Test
        @DisplayName("부분납이면 overdueSince가 해제되지 않는다")
        void doesNotClearOverdueSinceOnPartialPayment() {
            insertUser(94001L, "채권자");
            insertUser(94002L, "채무자");
            insertLinkedAccount(94401L, 94001L);
            insertSharedSettlement(94101L, 94001L, LocalDate.of(2026, 1, 10));
            insertParticipant(94201L, 94101L, 94002L, "ACTIVE");
            insertObligation(94301L, 94201L, "UNPAID", "150000.00");
            insertBankTransaction(94501L, 94401L, "50000.00");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));
            assertThat(fetchOverdueSince(94301L)).isNotNull();

            settlementPaymentService.applyAutoMatchedPayment(94301L, 94501L, new BigDecimal("50000.00"));

            assertThat(fetchOverdueSince(94301L)).isNotNull();
        }
    }

    @Nested
    @DisplayName("정산 단위 독립 처리 (부분 실패 격리)")
    class PartialFailureIsolation {

        @Test
        @DisplayName("한 정산에 문제가 있어도, 다른 정산의 연체 갱신은 정상적으로 처리된다")
        void continuesOtherSettlementsWhenOneFails() {
            insertUser(95001L, "채권자");
            insertUser(95002L, "채무자A");
            insertUser(95003L, "채무자B");

            // 정상 처리될 정산
            insertSharedSettlement(95101L, 95001L, LocalDate.of(2026, 1, 10));
            insertParticipant(95201L, 95101L, 95002L, "ACTIVE");
            insertObligation(95301L, 95201L, "UNPAID");

            // 참여자가 아예 없어서 스킵되지만, 예외 없이 넘어가는지 확인용 정산
            insertSharedSettlement(95102L, 95001L, LocalDate.of(2026, 1, 10));
            // 의도적으로 참여자를 넣지 않음

            // 정상 처리될 또 다른 정산
            insertSharedSettlement(95103L, 95001L, LocalDate.of(2026, 1, 10));
            insertParticipant(95202L, 95103L, 95003L, "ACTIVE");
            insertObligation(95302L, 95202L, "UNPAID");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));

            assertThat(fetchOverdueSince(95301L)).isNotNull();
            assertThat(fetchOverdueSince(95302L)).isNotNull();
        }
    }

    @Nested
    @DisplayName("페이징 - 다수 정산 처리")
    class PagingMultipleSettlements {

        @Test
        @DisplayName("PAGE_SIZE(200)를 넘는 정산이 있어도 모두 처리된다")
        void processesAllSettlementsAcrossMultiplePages() {
            insertUser(96001L, "채권자");

            int settlementCount = 210; // PAGE_SIZE(200)를 넘기도록
            for (int i = 0; i < settlementCount; i++) {
                long userId = 96100L + i;
                long settlementId = 96200L + i;
                long participantId = 96500L + i;
                long obligationId = 96800L + i;

                insertUser(userId, "참여자" + i);
                insertSharedSettlement(settlementId, 96001L, LocalDate.of(2026, 1, 10));
                insertParticipant(participantId, settlementId, userId, "ACTIVE");
                insertObligation(obligationId, participantId, "UNPAID");
            }

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));

            Integer overdueCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_obligation WHERE payment_obligation_id BETWEEN 96800 AND 97009 AND overdue_since IS NOT NULL",
                    Integer.class
            );
            assertThat(overdueCount).isEqualTo(settlementCount); // 210건 전부 처리됨
        }
    }

    @Nested
    @DisplayName("동시성 - 배치와 완납이 겹치는 경우")
    class ConcurrentPaymentDuringOverdueUpdate {

        @Test
        @DisplayName("연체 대상 조회 이후 완납 처리된 obligation은 overdueSince로 갱신되지 않는다")
        void doesNotMarkOverdueWhenAlreadyPaidBeforeUpdate() {
            insertUser(97001L, "채권자");
            insertUser(97002L, "채무자");
            insertLinkedAccount(97401L, 97001L);
            insertSharedSettlement(97101L, 97001L, LocalDate.of(2026, 1, 10));
            insertParticipant(97201L, 97101L, 97002L, "ACTIVE");
            insertObligation(97301L, 97201L, "UNPAID", "150000.00");
            insertBankTransaction(97501L, 97401L, "150000.00");

            settlementPaymentService.applyAutoMatchedPayment(97301L, 97501L, new BigDecimal("150000.00"));

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));

            assertThat(fetchOverdueSince(97301L)).isNull();
        }

        @Test
        @DisplayName("조건부 UPDATE는 이미 overdueSince가 채워진 건을 다시 덮어쓰지 않는다 (재실행 시 완납된 건 보호)")
        void conditionalUpdateProtectsAlreadyPaidObligation() {
            insertUser(98001L, "채권자");
            insertUser(98002L, "채무자");
            insertLinkedAccount(98401L, 98001L);
            insertSharedSettlement(98101L, 98001L, LocalDate.of(2026, 1, 10));
            insertParticipant(98201L, 98101L, 98002L, "ACTIVE");
            insertObligation(98301L, 98201L, "UNPAID", "150000.00");
            insertBankTransaction(98501L, 98401L, "150000.00");

            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 11));
            assertThat(fetchOverdueSince(98301L)).isNotNull();

            settlementPaymentService.applyAutoMatchedPayment(98301L, 98501L, new BigDecimal("150000.00"));
            assertThat(fetchOverdueSince(98301L)).isNull(); // 완납으로 해제됨

            // 배치가 다시 돌아도 이미 완납된 건이 연체로 잘못 마킹되면 안 됨
            overdueSettlementService.updateOverdueStatus(LocalDate.of(2026, 1, 15));
            assertThat(fetchOverdueSince(98301L)).isNull(); // 여전히 null 유지
        }
    }

    private LocalDateTime fetchOverdueSince(Long paymentObligationId) {
        return jdbcTemplate.queryForObject(
                "SELECT overdue_since FROM payment_obligation WHERE payment_obligation_id = ?",
                LocalDateTime.class,
                paymentObligationId
        );
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

    private void insertSharedSettlement(Long settlementId, Long ownerId, LocalDate dueDate) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement (
                    settlement_id, owner_id, settlement_type, settlement_status,
                    settlement_category, title, split_type, total_amount, due_date, created_at
                )
                VALUES (?, ?, 'SHARED', 'IN_PROGRESS', '식비', '테스트 정산', 'EQUAL', 150000, ?, NOW())
                """,
                settlementId, ownerId, dueDate
        );
    }

    private void insertRecurringSettlement(Long settlementId, Long ownerId, LocalDate cycleDate) {
        Long recurringSettlementId = settlementId; // 편의상 동일 값 사용, 별도 시퀀스 관리 불필요

        jdbcTemplate.update(
                """
                INSERT INTO recurring_settlement (
                    recurring_settlement_id, owner_id, settlement_category, title,
                    split_type, total_amount, cycle_rule, start_date, end_date, created_at
                )
                VALUES (?, ?, '월세', '테스트 정기정산', 'EQUAL', 150000, 'MONTHLY', ?, NULL, NOW())
                """,
                recurringSettlementId, ownerId, cycleDate
        );

        jdbcTemplate.update(
                """
                INSERT INTO settlement (
                    settlement_id, recurring_settlement_id, owner_id, settlement_type,
                    settlement_status, settlement_category, title, split_type,
                    total_amount, due_date, cycle_date, created_at
                )
                VALUES (?, ?, ?, 'RECURRING', 'IN_PROGRESS', '월세', '테스트 정기정산', 'EQUAL', 150000, NULL, ?, NOW())
                """,
                settlementId, recurringSettlementId, ownerId, cycleDate
        );
    }

    private void insertParticipant(Long participantId, Long settlementId, Long userId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement_participant (
                    participant_id, settlement_id, user_id, participant_role, participant_status, joined_at
                )
                VALUES (?, ?, ?, 'MEMBER', ?, NOW())
                """,
                participantId, settlementId, userId, status
        );
    }

    private void insertObligation(Long obligationId, Long participantId, String paymentStatus) {
        insertObligation(obligationId, participantId, paymentStatus, "150000.00");
    }

    private void insertObligation(Long obligationId, Long participantId, String paymentStatus, String expectedAmount) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_obligation (
                    payment_obligation_id, participant_id, expected_amount,
                    payment_status, review_status, obligation_status
                )
                VALUES (?, ?, ?, ?, 'NORMAL', 'ACTIVE')
                """,
                obligationId, participantId, new BigDecimal(expectedAmount), paymentStatus
        );
    }

    private void insertLinkedAccount(Long linkedAccountId, Long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO linked_bank_account (
                    linked_account_id, user_id, bank_code, account_number,
                    account_holder_name, connection_status, account_id
                )
                VALUES (?, ?, '001', ?, 'Owner', 'AVAILABLE', ?)
                """,
                linkedAccountId, userId, "account-" + linkedAccountId, linkedAccountId
        );
    }

    private void insertBankTransaction(Long bankTransactionId, Long linkedAccountId, String amount) {
        String externalTransactionId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO bank_transaction (
                    bank_transaction_id, linked_account_id, external_transaction_id,
                    amount, transaction_type, processing_status, transaction_at, synced_at
                )
                VALUES (?, ?, ?, ?, 'DEPOSIT', 'PENDING', NOW(), NOW())
                """,
                bankTransactionId, linkedAccountId, externalTransactionId, new BigDecimal(amount)
        );
    }
}