package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;
import org.teamsai.saibackend.domain.settlement.type.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@MybatisTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("dev")
@Sql(scripts = {
        "/db/user.sql",
        "/db/settlement.sql",
        "/db/settlement_participant.sql"
})
@DisplayName("SettlementMapper 통합 테스트")
class SettlementMapperTest {

    private static final Long USER_ID = 9701L;
    private static final Long OTHER_USER_ID = 9702L;

    @Autowired
    private SettlementMapper settlementMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("본인이 생성한 정산은 OWNER로 조회한다")
    void findAllByUserIdReturnsOwnerSettlement() {
        insertUser(USER_ID, "조회 사용자");
        insertSettlement(9711L, USER_ID, "내가 만든 정산");

        List<SettlementListResponse> result = settlementMapper.findAllByUserId(USER_ID);

        assertThat(result)
                .extracting(SettlementListResponse::settlementId, SettlementListResponse::role)
                .contains(tuple(9711L, "OWNER"));
    }

    @Test
    @Transactional
    @DisplayName("ACTIVE 참여자는 정산을 MEMBER로 조회한다")
    void findAllByUserIdReturnsActiveMemberSettlement() {
        insertUser(USER_ID, "참여자");
        insertUser(OTHER_USER_ID, "정산 생성자");
        insertSettlement(9721L, OTHER_USER_ID, "참여 중인 정산");
        insertParticipant(9741L, 9721L, USER_ID, "ACTIVE");

        List<SettlementListResponse> result = settlementMapper.findAllByUserId(USER_ID);

        assertThat(result)
                .extracting(SettlementListResponse::settlementId, SettlementListResponse::role)
                .contains(tuple(9721L, "MEMBER"));
    }

    @Test
    @Transactional
    @DisplayName("ACTIVE가 아닌 참여자는 MEMBER 목록에서 제외한다")
    void findAllByUserIdExcludesInactiveParticipant() {
        insertUser(USER_ID, "참여자");
        insertUser(OTHER_USER_ID, "정산 생성자");
        insertSettlement(9781L, OTHER_USER_ID, "비활성 참여 정산");
        insertParticipant(9801L, 9781L, USER_ID, "REMOVED");

        List<SettlementListResponse> result = settlementMapper.findAllByUserId(USER_ID);

        assertThat(result)
                .extracting(SettlementListResponse::settlementId)
                .doesNotContain(9781L);
    }

    @Test
    @Transactional
    @DisplayName("정산과 관계없는 사용자의 목록에는 정산이 포함되지 않는다")
    void findAllByUserIdExcludesUnrelatedSettlement() {
        insertUser(USER_ID, "조회 사용자");
        insertUser(OTHER_USER_ID, "정산 생성자");
        insertSettlement(9811L, OTHER_USER_ID, "관계없는 정산");

        List<SettlementListResponse> result = settlementMapper.findAllByUserId(USER_ID);

        assertThat(result)
                .extracting(SettlementListResponse::settlementId)
                .doesNotContain(9811L);
    }

    @Test
    @Transactional
    @DisplayName("recurringSettlementId로 가장 최근 회차(cycleDate 기준)를 조회한다")
    void findLatestByRecurringId_returnsMostRecentCycle() {
        insertUser(9901L, "정기정산 소유자");
        insertRecurringSettlement(9911L, 9901L, LocalDate.of(2026, 1, 31));
        insertRecurringSettlementInstance(9921L, 9911L, 9901L, LocalDate.of(2026, 1, 31));
        insertRecurringSettlementInstance(9922L, 9911L, 9901L, LocalDate.of(2026, 2, 28));
        insertRecurringSettlementInstance(9923L, 9911L, 9901L, LocalDate.of(2025, 12, 31)); // 더 과거 회차

        SettlementDTO result = settlementMapper.findLatestByRecurringId(9911L);

        assertThat(result).isNotNull();
        assertThat(result.getSettlementId()).isEqualTo(9922L);
        assertThat(result.getCycleDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @Transactional
    @DisplayName("해당 recurringSettlementId로 생성된 회차가 없으면 null을 반환한다")
    void findLatestByRecurringId_returnsNullWhenNoSettlement() {
        SettlementDTO result = settlementMapper.findLatestByRecurringId(999999L);

        assertThat(result).isNull();
    }

    @Test
    @Transactional
    @DisplayName("Settlement를 저장하면 DTO에 생성된 settlementId가 채워진다")
    void insertSettlement_fillsGeneratedId() {
        insertUser(9901L, "정기정산 소유자");
        insertRecurringSettlement(9911L, 9901L, LocalDate.of(2026, 1, 31));

        SettlementDTO newSettlement = SettlementDTO.builder()
                .recurringSettlementId(9911L)
                .ownerId(9901L)
                .settlementType(SettlementType.RECURRING)
                .settlementStatus(SettlementStatus.IN_PROGRESS)
                .settlementCategory("월세")
                .title("자취방 월세")
                .splitType(SplitType.EQUAL)
                .totalAmount(new BigDecimal("300000"))
                .cycleDate(LocalDate.of(2026, 3, 31))
                .dueDate(null)
                .createdAt(LocalDateTime.now())
                .build();

        int inserted = settlementMapper.insertSettlement(newSettlement);

        assertThat(inserted).isEqualTo(1);
        assertThat(newSettlement.getSettlementId()).isNotNull();
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

    private void insertSettlement(Long settlementId, Long ownerId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement (
                    settlement_id, owner_id, settlement_type, settlement_status,
                    settlement_category, title, split_type, due_date, total_amount, created_at
                )
                VALUES (?, ?, 'SHARED', 'IN_PROGRESS', 'FOOD', ?, 'EQUAL', '2099-12-31', 100000.00, NOW())
                """,
                settlementId, ownerId, title
        );
    }

    private void insertParticipant(Long participantId, Long settlementId, Long userId, String participantStatus) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement_participant (
                    participant_id, settlement_id, user_id, participant_role, participant_status, joined_at
                )
                VALUES (?, ?, ?, 'MEMBER', ?, NOW())
                """,
                participantId, settlementId, userId, participantStatus
        );
    }

    private void insertRecurringSettlement(Long recurringSettlementId, Long ownerId, LocalDate startDate) {
        jdbcTemplate.update(
                """
                INSERT INTO recurring_settlement (
                    recurring_settlement_id, owner_id, settlement_category, title,
                    split_type, total_amount, cycle_rule, start_date, end_date, created_at
                )
                VALUES (?, ?, '월세', '자취방 월세', 'EQUAL', 300000, 'MONTHLY', ?, NULL, NOW())
                """,
                recurringSettlementId, ownerId, startDate
        );
    }

    private void insertRecurringSettlementInstance(
            Long settlementId, Long recurringSettlementId, Long ownerId, LocalDate cycleDate
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement (
                    settlement_id, recurring_settlement_id, owner_id, settlement_type,
                    settlement_status, settlement_category, title, split_type,
                    total_amount, due_date, cycle_date, created_at
                )
                VALUES (?, ?, ?, 'RECURRING', 'IN_PROGRESS', '월세', '자취방 월세', 'EQUAL', 300000, NULL, ?, NOW())
                """,
                settlementId, recurringSettlementId, ownerId, cycleDate
        );
    }
}