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
                settlementMapper.findAllByUserId(
                        USER_ID
                );


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
    @DisplayName("ACTIVE 참여자는 정산을 MEMBER로 조회한다")
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

        insertParticipant(
                9741L,
                9721L,
                USER_ID,
                "ACTIVE"
        );


        List<SettlementListResponse> result =
                settlementMapper.findAllByUserId(
                        USER_ID
                );


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

        insertParticipant(
                9801L,
                9781L,
                USER_ID,
                "REMOVED"
        );


        List<SettlementListResponse> result =
                settlementMapper.findAllByUserId(
                        USER_ID
                );


        assertThat(result)
                .extracting(
                        SettlementListResponse::settlementId
                )
                .doesNotContain(
                        9781L
                );
    }


    @Test
    @Transactional
    @DisplayName("정산과 관계없는 사용자의 목록에는 정산이 포함되지 않는다")
    void findAllByUserIdExcludesUnrelatedSettlement() {

        insertUser(
                USER_ID,
                "조회 사용자"
        );

        insertUser(
                OTHER_USER_ID,
                "정산 생성자"
        );

        insertSettlement(
                9811L,
                OTHER_USER_ID,
                "관계없는 정산"
        );


        List<SettlementListResponse> result =
                settlementMapper.findAllByUserId(
                        USER_ID
                );


        assertThat(result)
                .extracting(
                        SettlementListResponse::settlementId
                )
                .doesNotContain(
                        9811L
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


    private void insertParticipant(
            Long participantId,
            Long settlementId,
            Long userId,
            String participantStatus
    ) {

        jdbcTemplate.update(
                """
                INSERT INTO settlement_participant (
                    participant_id,
                    settlement_id,
                    user_id,
                    participant_role,
                    participant_status,
                    joined_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'MEMBER',
                    ?,
                    NOW()
                )
                """,
                participantId,
                settlementId,
                userId,
                participantStatus
        );
    }
}