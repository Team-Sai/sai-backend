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
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;

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
        "/db/settlement_invitation.sql",
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

        insertUser(
                USER_ID,
                "조회 사용자"
        );

        insertSettlement(
                9711L,
                USER_ID,
                "내가 만든 정산"
        );

        List<SettlementListResponse> result =
                settlementMapper.findAllByUserId(USER_ID);

        assertThat(result)
                .extracting(
                        SettlementListResponse::settlementId,
                        SettlementListResponse::role
                )
                .contains(
                        tuple(
                                9711L,
                                "OWNER"
                        )
                );
    }


    @Test
    @Transactional
    @DisplayName("수락된 초대의 ACTIVE 참여자는 MEMBER로 조회한다")
    void findAllByUserIdReturnsActiveMemberSettlement() {

        insertUser(
                USER_ID,
                "참여자"
        );

        insertUser(
                OTHER_USER_ID,
                "정산 생성자"
        );

        insertSettlement(
                9721L,
                OTHER_USER_ID,
                "참여 중인 정산"
        );

        insertInvitation(
                9731L,
                9721L,
                USER_ID,
                "ACCEPTED"
        );

        insertParticipant(
                9741L,
                9731L,
                "ACTIVE"
        );

        List<SettlementListResponse> result =
                settlementMapper.findAllByUserId(USER_ID);

        assertThat(result)
                .extracting(
                        SettlementListResponse::settlementId,
                        SettlementListResponse::role
                )
                .contains(
                        tuple(
                                9721L,
                                "MEMBER"
                        )
                );
    }


    @Test
    @Transactional
    @DisplayName("초대 대기 또는 거절 상태의 정산은 MEMBER 목록에서 제외한다")
    void findAllByUserIdExcludesInvitedAndRejectedInvitations() {

        insertUser(
                USER_ID,
                "참여자"
        );

        insertUser(
                OTHER_USER_ID,
                "정산 생성자"
        );

        insertSettlement(
                9751L,
                OTHER_USER_ID,
                "초대 대기 정산"
        );

        insertSettlement(
                9752L,
                OTHER_USER_ID,
                "초대 거절 정산"
        );

        insertInvitation(
                9761L,
                9751L,
                USER_ID,
                "INVITED"
        );

        insertInvitation(
                9762L,
                9752L,
                USER_ID,
                "REJECTED"
        );

        /*
         * participant 행이 존재하더라도
         * invitation_status가 ACCEPTED가 아니므로
         * 목록에서 조회되면 안 된다.
         */
        insertParticipant(
                9771L,
                9761L,
                "ACTIVE"
        );

        insertParticipant(
                9772L,
                9762L,
                "ACTIVE"
        );

        List<SettlementListResponse> result =
                settlementMapper.findAllByUserId(USER_ID);

        assertThat(result)
                .extracting(
                        SettlementListResponse::settlementId
                )
                .doesNotContain(
                        9751L,
                        9752L
                );
    }


    @Test
    @Transactional
    @DisplayName("ACTIVE가 아닌 참여자는 MEMBER 목록에서 제외한다")
    void findAllByUserIdExcludesInactiveParticipant() {

        insertUser(
                USER_ID,
                "참여자"
        );

        insertUser(
                OTHER_USER_ID,
                "정산 생성자"
        );

        insertSettlement(
                9781L,
                OTHER_USER_ID,
                "비활성 참여 정산"
        );

        insertInvitation(
                9791L,
                9781L,
                USER_ID,
                "ACCEPTED"
        );

        insertParticipant(
                9801L,
                9791L,
                "REMOVED"
        );

        List<SettlementListResponse> result =
                settlementMapper.findAllByUserId(USER_ID);

        assertThat(result)
                .extracting(
                        SettlementListResponse::settlementId
                )
                .doesNotContain(
                        9781L
                );
    }


    private void insertUser(
            Long userId,
            String name
    ) {
        String unique =
                UUID.randomUUID().toString();

        jdbcTemplate.update(
                """
                INSERT INTO users (
                    user_id,
                    user_token,
                    email,
                    password,
                    name,
                    birth_date
                )
                VALUES (
                    ?, ?, ?, ?, ?, '2000-01-01'
                )
                """,
                userId,
                unique,
                unique + "@example.com",
                "password",
                name
        );
    }


    private void insertSettlement(
            Long settlementId,
            Long ownerId,
            String title
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement (
                    settlement_id,
                    owner_id,
                    settlement_type,
                    settlement_status,
                    settlement_category,
                    title,
                    split_type,
                    due_date,
                    total_amount,
                    created_at
                )
                VALUES (
                    ?,
                    ?,
                    'SHARED',
                    'IN_PROGRESS',
                    'FOOD',
                    ?,
                    'EQUAL',
                    '2099-12-31',
                    100000.00,
                    NOW()
                )
                """,
                settlementId,
                ownerId,
                title
        );
    }


    private void insertInvitation(
            Long invitationId,
            Long settlementId,
            Long userId,
            String invitationStatus
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement_invitation (
                    invitation_id,
                    settlement_id,
                    user_id,
                    invitation_status,
                    invited_at,
                    accepted_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    NOW(),
                    CASE
                        WHEN ? = 'ACCEPTED'
                        THEN NOW()
                        ELSE NULL
                    END
                )
                """,
                invitationId,
                settlementId,
                userId,
                invitationStatus,
                invitationStatus
        );
    }


    private void insertParticipant(
            Long participantId,
            Long invitationId,
            String participantStatus
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement_participant (
                    participant_id,
                    invitation_id,
                    participant_role,
                    participant_status,
                    joined_at
                )
                VALUES (
                    ?,
                    ?,
                    'MEMBER',
                    ?,
                    NOW()
                )
                """,
                participantId,
                invitationId,
                participantStatus
        );
    }
}