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
        jdbcTemplate.update("DELETE FROM payment_record WHERE payment_record_id >= 90000");
        jdbcTemplate.update("DELETE FROM bank_transaction WHERE bank_transaction_id >= 90000");
        jdbcTemplate.update("DELETE FROM linked_bank_account WHERE linked_account_id >= 90000");
        jdbcTemplate.update("DELETE FROM payment_obligation WHERE payment_obligation_id >= 90000");
        jdbcTemplate.update("DELETE FROM settlement_participant WHERE participant_id >= 90000");
        jdbcTemplate.update("DELETE FROM settlement WHERE settlement_id >= 90000");
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

            assertThat(fetchOverdueSince(92301L)).isEqualTo(LocalDate.of(2026, 2, 1).atStartOfDay());
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
        jdbcTemplate.update(
                """
                INSERT INTO settlement (
                    settlement_id, owner_id, settlement_type, settlement_status,
                    settlement_category, title, split_type, total_amount, due_date, cycle_date, created_at
                )
                VALUES (?, ?, 'RECURRING', 'IN_PROGRESS', '월세', '테스트 정기정산', 'EQUAL', 150000, NULL, ?, NOW())
                """,
                settlementId, ownerId, cycleDate
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