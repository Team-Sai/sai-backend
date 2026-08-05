package org.teamsai.saibackend.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@Sql(scripts = {
        "/db/user.sql",
        "/db/linked_bank_account.sql",
        "/db/settlement.sql",
        "/db/settlement_invitation.sql",
        "/db/settlement_participant.sql",
        "/db/settlement_account.sql",
        "/db/payment.sql"
})
@DisplayName("PaymentObligationMapper 통합 테스트")
class PaymentObligationMapperTest {

    private static final Long LINKED_ACCOUNT_ID = 9001L;
    private static final Long OTHER_LINKED_ACCOUNT_ID = 9002L;
    private static final LocalDateTime MATCHING_TRANSACTION_AT =
            LocalDateTime.of(2099, 1, 1, 0, 0);

    @Autowired
    private PaymentObligationMapper paymentObligationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("연결 계좌를 사용하는 진행 중 정산의 유효 매칭 후보를 조회한다")
    void findMatchCandidatesByLinkedAccountIdReturnsValidCandidates() {
        insertUser(9101L, "Owner");
        insertUser(9102L, "Hong Gil Dong");
        insertUser(9103L, "Kim Chul Soo");
        insertLinkedAccount(LINKED_ACCOUNT_ID, 9101L);
        insertLinkedAccount(OTHER_LINKED_ACCOUNT_ID, 9101L);

        insertSettlement(9201L, 9101L, "IN_PROGRESS");
        insertSettlement(9202L, 9101L, "IN_PROGRESS");
        insertSettlement(9203L, 9101L, "IN_PROGRESS");
        insertSettlement(9204L, 9101L, "CLOSED");
        insertActiveSettlementAccount(9201L, LINKED_ACCOUNT_ID);
        insertActiveSettlementAccount(9202L, LINKED_ACCOUNT_ID);
        insertActiveSettlementAccount(9203L, OTHER_LINKED_ACCOUNT_ID);
        insertActiveSettlementAccount(9204L, LINKED_ACCOUNT_ID);

        insertAcceptedParticipant(9301L, 9201L, 9102L, 9401L, "ACTIVE");
        insertAcceptedParticipant(9302L, 9202L, 9103L, 9402L, "ACTIVE");
        insertAcceptedParticipant(9303L, 9203L, 9103L, 9403L, "ACTIVE");
        insertAcceptedParticipant(9304L, 9204L, 9103L, 9404L, "ACTIVE");
        insertAcceptedParticipant(9305L, 9201L, 9103L, 9405L, "REMOVED");

        insertPaymentObligation(9501L, 9401L, "10000.00", "UNPAID", "ACTIVE");
        insertPaymentObligation(9502L, 9402L, "20000.00", "PARTIALLY_PAID", "ACTIVE");
        insertPaymentObligation(9503L, 9403L, "30000.00", "UNPAID", "ACTIVE");
        insertPaymentObligation(9504L, 9404L, "40000.00", "UNPAID", "ACTIVE");
        insertPaymentObligation(9505L, 9405L, "50000.00", "UNPAID", "ACTIVE");
        insertPaymentObligation(9506L, 9401L, "10000.00", "PAID", "ACTIVE");
        insertPaymentObligation(9507L, 9401L, "10000.00", "UNPAID", "EXCLUDED");
        insertConfirmedPaymentRecord(9601L, 9502L, "5000.00");
        insertCancelledPaymentRecord(9602L, 9502L, "3000.00");

        List<MatchingCandidate> result =
                paymentObligationMapper.findMatchCandidatesByLinkedAccountId(
                        LINKED_ACCOUNT_ID,
                        MATCHING_TRANSACTION_AT
                );

        assertThat(result)
                .extracting(
                        MatchingCandidate::targetType,
                        MatchingCandidate::obligationId,
                        MatchingCandidate::participantId,
                        MatchingCandidate::participantName,
                        candidate -> candidate.remainingAmount()
                                .stripTrailingZeros()
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                MatchingTargetType.SETTLEMENT,
                                9501L,
                                9401L,
                                "Hong Gil Dong",
                                new BigDecimal("10000").stripTrailingZeros()
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                MatchingTargetType.SETTLEMENT,
                                9502L,
                                9402L,
                                "Kim Chul Soo",
                                new BigDecimal("15000").stripTrailingZeros()
                        )
                );
    }

    private void insertUser(Long userId, String name) {
        String unique = UUID.randomUUID().toString();
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
                VALUES (?, ?, ?, ?, ?, '2000-01-01')
                """,
                userId,
                unique,
                unique + "@example.com",
                "password",
                name
        );
    }

    private void insertLinkedAccount(Long linkedAccountId, Long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO linked_bank_account (
                    linked_account_id,
                    user_id,
                    bank_code,
                    account_number,
                    account_holder_name,
                    connection_status,
                    account_id
                )
                VALUES (?, ?, '001', ?, 'Owner', 'AVAILABLE', ?)
                """,
                linkedAccountId,
                userId,
                "account-" + linkedAccountId,
                linkedAccountId
        );
    }

    private void insertSettlement(
            Long settlementId,
            Long ownerId,
            String settlementStatus
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
                    created_at
                )
                VALUES (?, ?, 'SHARED', ?, 'FOOD', ?, NOW())
                """,
                settlementId,
                ownerId,
                settlementStatus,
                "settlement-" + settlementId
        );
    }

    private void insertActiveSettlementAccount(
            Long settlementId,
            Long linkedAccountId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO settlement_account (
                    settlement_id,
                    linked_account_id,
                    account_status,
                    selected_at
                )
                VALUES (?, ?, 'ACTIVE', NOW())
                """,
                settlementId,
                linkedAccountId
        );
    }

    private void insertAcceptedParticipant(
            Long invitationId,
            Long settlementId,
            Long userId,
            Long participantId,
            String participantStatus
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
                VALUES (?, ?, ?, 'ACCEPTED', NOW(), NOW())
                """,
                invitationId,
                settlementId,
                userId
        );
        jdbcTemplate.update(
                """
                INSERT INTO settlement_participant (
                    participant_id,
                    invitation_id,
                    participant_role,
                    participant_status,
                    joined_at
                )
                VALUES (?, ?, 'MEMBER', ?, NOW())
                """,
                participantId,
                invitationId,
                participantStatus
        );
    }

    private void insertPaymentObligation(
            Long obligationId,
            Long participantId,
            String expectedAmount,
            String paymentStatus,
            String obligationStatus
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_obligation (
                    payment_obligation_id,
                    participant_id,
                    expected_amount,
                    payment_status,
                    review_status,
                    obligation_status
                )
                VALUES (?, ?, ?, ?, 'NORMAL', ?)
                """,
                obligationId,
                participantId,
                new BigDecimal(expectedAmount),
                paymentStatus,
                obligationStatus
        );
    }

    private void insertConfirmedPaymentRecord(
            Long paymentRecordId,
            Long obligationId,
            String amount
    ) {
        insertPaymentRecord(
                paymentRecordId,
                obligationId,
                amount,
                "CONFIRMED"
        );
    }

    private void insertCancelledPaymentRecord(
            Long paymentRecordId,
            Long obligationId,
            String amount
    ) {
        insertPaymentRecord(
                paymentRecordId,
                obligationId,
                amount,
                "CANCELLED"
        );
    }

    private void insertPaymentRecord(
            Long paymentRecordId,
            Long obligationId,
            String amount,
            String recordStatus
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_record (
                    payment_record_id,
                    bank_transaction_id,
                    obligation_id,
                    amount,
                    source_type,
                    record_status,
                    recorded_at
                )
                VALUES (?, ?, ?, ?, 'AUTO_MATCH', ?, NOW())
                """,
                paymentRecordId,
                paymentRecordId,
                obligationId,
                new BigDecimal(amount),
                recordStatus
        );
    }
}
