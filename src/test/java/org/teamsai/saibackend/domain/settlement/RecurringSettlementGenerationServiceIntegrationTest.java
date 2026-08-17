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
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementGenerationService;
import org.teamsai.saibackend.domain.settlement.type.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@Sql(scripts = {
        "/db/user.sql",
        "/db/settlement.sql",
        "/db/settlement_participant.sql",
        "/db/payment.sql"
})
@DisplayName("RecurringSettlementGenerationService 엔드투엔드 통합 테스트")
class RecurringSettlementGenerationServiceIntegrationTest {

    @Autowired
    private RecurringSettlementGenerationService generationService;

    @Autowired
    private SettlementMapper settlementMapper;

    @Autowired
    private SettlementParticipantMapper participantMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_obligation WHERE participant_id >= 70000");
        jdbcTemplate.update("DELETE FROM settlement_participant WHERE participant_id >= 70000");
        jdbcTemplate.update("DELETE FROM settlement WHERE settlement_id >= 70000");
        jdbcTemplate.update("DELETE FROM recurring_settlement WHERE recurring_settlement_id >= 70000");
        jdbcTemplate.update("DELETE FROM users WHERE user_id >= 70000");
    }

    @Nested
    @DisplayName("정상 시나리오 - 2회차 생성")
    class NormalGeneration {

        @Test
        @DisplayName("참여자 2명이 있는 1회차에서 다음 회차를 생성하면, 동일한 참여자 2명이 복사되고 동일 금액이 유지된다")
        void generatesNextCycleWithSameParticipantsAndAmounts() {
            Long ownerId = 71001L;
            Long userA = 71002L;
            Long userB = 71003L;
            Long recurringId = 71101L;
            Long settlement1Id = 71201L;
            Long participant1Id = 71301L;
            Long participant2Id = 71302L;

            insertUser(ownerId, "월세 관리자");
            insertUser(userA, "참여자 A");
            insertUser(userB, "참여자 B");
            insertRecurringSettlement(recurringId, ownerId, LocalDate.of(2026, 1, 31));
            insertSettlementInstance(settlement1Id, recurringId, ownerId, LocalDate.of(2026, 1, 31));
            insertParticipant(participant1Id, settlement1Id, userA, "ACTIVE");
            insertParticipant(participant2Id, settlement1Id, userB, "ACTIVE");
            insertObligation(81001L, participant1Id, "150000.00");
            insertObligation(81002L, participant2Id, "150000.00");

            generationService.generateTodaySettlements(LocalDate.of(2026, 2, 28));

            var latest = settlementMapper.findLatestByRecurringId(recurringId);
            assertThat(latest).isNotNull();
            assertThat(latest.getSettlementId()).isNotEqualTo(settlement1Id);
            assertThat(latest.getCycleDate()).isEqualTo(LocalDate.of(2026, 2, 28));

            var newParticipants = participantMapper.findBySettlementId(latest.getSettlementId());
            assertThat(newParticipants).hasSize(2);

            Map<Long, BigDecimal> obligationByUserId = fetchObligationAmountsByUser(latest.getSettlementId());
            assertThat(obligationByUserId.get(userA)).isEqualByComparingTo(new BigDecimal("150000"));
            assertThat(obligationByUserId.get(userB)).isEqualByComparingTo(new BigDecimal("150000"));
        }
    }

    @Nested
    @DisplayName("참여자 탈퇴 시 EQUAL 재계산")
    class EqualRecalculationOnParticipantLeft {

        @Test
        @DisplayName("2명 중 1명이 REMOVED되면, 새 회차엔 남은 1명만 복사되고 전액(30만원)으로 재계산된다")
        void recalculatesFullAmountWhenOneParticipantRemoved() {
            Long ownerId = 72001L;
            Long userA = 72002L;
            Long userB = 72003L;
            Long recurringId = 72101L;
            Long settlement1Id = 72201L;
            Long participant1Id = 72301L;
            Long participant2Id = 72302L;

            insertUser(ownerId, "월세 관리자");
            insertUser(userA, "남는 참여자");
            insertUser(userB, "탈퇴한 참여자");
            insertRecurringSettlement(recurringId, ownerId, LocalDate.of(2026, 1, 31));
            insertSettlementInstance(settlement1Id, recurringId, ownerId, LocalDate.of(2026, 1, 31));
            insertParticipant(participant1Id, settlement1Id, userA, "ACTIVE");
            insertParticipant(participant2Id, settlement1Id, userB, "REMOVED"); // 이미 탈퇴 상태
            insertObligation(82001L, participant1Id, "150000.00");
            insertObligation(82002L, participant2Id, "150000.00");

            generationService.generateTodaySettlements(LocalDate.of(2026, 2, 28));

            var latest = settlementMapper.findLatestByRecurringId(recurringId);
            var newParticipants = participantMapper.findBySettlementId(latest.getSettlementId());

            assertThat(newParticipants).hasSize(1);

            Map<Long, BigDecimal> obligationByUserId = fetchObligationAmountsByUser(latest.getSettlementId());
            assertThat(obligationByUserId.get(userA)).isEqualByComparingTo(new BigDecimal("300000"));
            assertThat(obligationByUserId).doesNotContainKey(userB);
        }
    }

    @Nested
    @DisplayName("CUSTOM 분할 시 재청구 이력 중 최신 obligation 사용")
    class LatestObligationAmongMultipleRecords {

        @Test
        @DisplayName("한 참여자에게 obligation이 여러 건(재청구 이력) 있으면, ID가 가장 큰 최신 것의 금액으로 다음 회차가 생성된다")
        void usesLatestObligationAmountWhenMultipleExistForSameParticipant() {
            Long ownerId = 75001L;
            Long userA = 75002L;
            Long recurringId = 75101L;
            Long settlement1Id = 75201L;
            Long participant1Id = 75301L;

            insertUser(ownerId, "채무자 관리자");
            insertUser(userA, "참여자 A");
            insertRecurringSettlement(recurringId, ownerId, LocalDate.of(2026, 1, 31), SplitType.CUSTOM);
            insertSettlementInstance(settlement1Id, recurringId, ownerId, LocalDate.of(2026, 1, 31), SplitType.CUSTOM);
            insertParticipant(participant1Id, settlement1Id, userA, "ACTIVE");

            // 재청구 이력: 오래된 obligation(취소/제외 처리됐다고 가정)과 최신 obligation이 공존
            insertObligation(85001L, participant1Id, "100000.00", "EXCLUDED");
            insertObligation(85002L, participant1Id, "150000.00", "ACTIVE"); // 더 최근에 생성된, ID가 더 큰 것

            generationService.generateTodaySettlements(LocalDate.of(2026, 2, 28));

            var latest = settlementMapper.findLatestByRecurringId(recurringId);
            assertThat(latest).isNotNull();
            assertThat(latest.getSettlementId()).isNotEqualTo(settlement1Id);

            Map<Long, BigDecimal> obligationByUserId = fetchObligationAmountsByUser(latest.getSettlementId());
            assertThat(obligationByUserId.get(userA)).isEqualByComparingTo(new BigDecimal("150000"));
        }

        @Test
        @DisplayName("ACTIVE 상태인 obligation이 여러 건이어도, 그중 ID가 가장 큰(가장 최근) 것을 사용한다")
        void usesHighestIdAmongMultipleActiveObligations() {
            Long ownerId = 76001L;
            Long userA = 76002L;
            Long recurringId = 76101L;
            Long settlement1Id = 76201L;
            Long participant1Id = 76301L;

            insertUser(ownerId, "채무자 관리자");
            insertUser(userA, "참여자 A");
            insertRecurringSettlement(recurringId, ownerId, LocalDate.of(2026, 1, 31), SplitType.CUSTOM);
            insertSettlementInstance(settlement1Id, recurringId, ownerId, LocalDate.of(2026, 1, 31), SplitType.CUSTOM);
            insertParticipant(participant1Id, settlement1Id, userA, "ACTIVE");

            insertObligation(86001L, participant1Id, "80000.00", "ACTIVE");
            insertObligation(86002L, participant1Id, "200000.00", "ACTIVE"); // ID가 더 큼

            generationService.generateTodaySettlements(LocalDate.of(2026, 2, 28));

            var latest = settlementMapper.findLatestByRecurringId(recurringId);
            Map<Long, BigDecimal> obligationByUserId = fetchObligationAmountsByUser(latest.getSettlementId());
            assertThat(obligationByUserId.get(userA)).isEqualByComparingTo(new BigDecimal("200000"));
        }
    }

    @Nested
    @DisplayName("밀린 회차 캐치업")
    class CatchUpMultipleCycles {

        @Test
        @DisplayName("배치가 두 달 밀린 상태에서 한 번 호출하면, 2회차와 3회차가 순차적으로 모두 생성된다")
        void catchesUpMultipleMissedCycles() {
            Long ownerId = 73001L;
            Long userA = 73002L;
            Long recurringId = 73101L;
            Long settlement1Id = 73201L;
            Long participant1Id = 73301L;

            insertUser(ownerId, "월세 관리자");
            insertUser(userA, "참여자 A");
            insertRecurringSettlement(recurringId, ownerId, LocalDate.of(2026, 1, 31));
            insertSettlementInstance(settlement1Id, recurringId, ownerId, LocalDate.of(2026, 1, 31));
            insertParticipant(participant1Id, settlement1Id, userA, "ACTIVE");
            insertObligation(83001L, participant1Id, "300000.00");

            generationService.generateTodaySettlements(LocalDate.of(2026, 3, 31));

            List<LocalDate> cycleDates = jdbcTemplate.queryForList(
                    "SELECT cycle_date FROM settlement WHERE recurring_settlement_id = ? ORDER BY cycle_date",
                    LocalDate.class,
                    recurringId
            );

            assertThat(cycleDates).containsExactly(
                    LocalDate.of(2026, 1, 31),
                    LocalDate.of(2026, 2, 28), // 말일 클램프
                    LocalDate.of(2026, 3, 31)  // anchor 복귀
            );
        }
    }

    @Nested
    @DisplayName("멱등성 - 중복 실행 방지")
    class Idempotency {

        @Test
        @DisplayName("이미 오늘자 회차가 존재하는 상태에서 같은 baseDate로 다시 호출해도 중복 생성되지 않는다")
        void doesNotDuplicateWhenAlreadyGeneratedForBaseDate() {
            Long ownerId = 74001L;
            Long userA = 74002L;
            Long recurringId = 74101L;
            Long settlement1Id = 74201L;
            Long participant1Id = 74301L;

            insertUser(ownerId, "월세 관리자");
            insertUser(userA, "참여자 A");
            insertRecurringSettlement(recurringId, ownerId, LocalDate.of(2026, 1, 31));
            insertSettlementInstance(settlement1Id, recurringId, ownerId, LocalDate.of(2026, 1, 31));
            insertParticipant(participant1Id, settlement1Id, userA, "ACTIVE");
            insertObligation(84001L, participant1Id, "300000.00");

            generationService.generateTodaySettlements(LocalDate.of(2026, 2, 28));
            generationService.generateTodaySettlements(LocalDate.of(2026, 2, 28)); // 같은 날짜로 재호출

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM settlement WHERE recurring_settlement_id = ? AND cycle_date = ?",
                    Integer.class,
                    recurringId, LocalDate.of(2026, 2, 28)
            );

            assertThat(count).isEqualTo(1); // 두 번 호출해도 1건만 존재
        }

        @Test
        @DisplayName("RECURRING 타입인데 recurring_settlement_id 또는 cycle_date가 NULL이면 CHECK 제약 위반으로 insert가 거부된다")
        void rejectsRecurringSettlementWithMissingRequiredFields() {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    """
                    INSERT INTO settlement (
                        settlement_id, owner_id, settlement_type, settlement_status,
                        settlement_category, title, split_type, total_amount, cycle_date, created_at
                    )
                    VALUES (?, ?, 'RECURRING', 'IN_PROGRESS', '월세', '위반 테스트', 'EQUAL', 150000, NULL, NOW())
                    """,
                    99901L, 99001L
            )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    private Map<Long, BigDecimal> fetchObligationAmountsByUser(Long settlementId) {
        return jdbcTemplate.query(
                """
                SELECT sp.user_id AS user_id, po.expected_amount AS expected_amount
                FROM settlement_participant sp
                JOIN payment_obligation po ON po.participant_id = sp.participant_id
                WHERE sp.settlement_id = ?
                """,
                (rs, rowNum) -> Map.entry(rs.getLong("user_id"), rs.getBigDecimal("expected_amount")),
                settlementId
        ).stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

    private void insertRecurringSettlement(Long recurringSettlementId, Long ownerId, LocalDate startDate) {
        insertRecurringSettlement(recurringSettlementId, ownerId, startDate, SplitType.EQUAL);
    }

    private void insertSettlementInstance(Long settlementId, Long recurringSettlementId, Long ownerId, LocalDate cycleDate) {
        insertSettlementInstance(settlementId, recurringSettlementId, ownerId, cycleDate, SplitType.EQUAL);
    }

    private void insertObligation(Long obligationId, Long participantId, String expectedAmount) {
        insertObligation(obligationId, participantId, expectedAmount, "ACTIVE");
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

    private void insertRecurringSettlement(Long recurringSettlementId, Long ownerId, LocalDate startDate, SplitType splitType) {
        jdbcTemplate.update(
                """
                INSERT INTO recurring_settlement (
                    recurring_settlement_id, owner_id, settlement_category, title,
                    split_type, total_amount, cycle_rule, start_date, end_date, created_at
                )
                VALUES (?, ?, '차용금', '월 상환액', ?, 300000, 'MONTHLY', ?, NULL, NOW())
                """,
                recurringSettlementId, ownerId, splitType.name(), startDate
        );
    }

    private void insertSettlementInstance(
            Long settlementId, Long recurringSettlementId, Long ownerId, LocalDate cycleDate, SplitType splitType
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement (
                    settlement_id, recurring_settlement_id, owner_id, settlement_type,
                    settlement_status, settlement_category, title, split_type,
                    total_amount, due_date, cycle_date, created_at
                )
                VALUES (?, ?, ?, 'RECURRING', 'IN_PROGRESS', '차용금', '월 상환액', ?, 300000, NULL, ?, NOW())
                """,
                settlementId, recurringSettlementId, ownerId, splitType.name(), cycleDate
        );
    }

    private void insertObligation(Long obligationId, Long participantId, String expectedAmount, String obligationStatus) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_obligation (
                    payment_obligation_id, participant_id, expected_amount,
                    payment_status, review_status, obligation_status
                )
                VALUES (?, ?, ?, 'UNPAID', 'NORMAL', ?)
                """,
                obligationId, participantId, new BigDecimal(expectedAmount), obligationStatus
        );
    }
}