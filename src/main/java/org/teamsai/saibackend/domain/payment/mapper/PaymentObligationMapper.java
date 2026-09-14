package org.teamsai.saibackend.domain.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.matching.service.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PaymentObligationMapper {

    List<PaymentObligationDTO> findLatestByParticipantIdsIncludingWrittenOff(
            @Param("participantIds") List<Long> participantIds
    );

    List<Long> findWriteOffCandidateIds(@Param("cutoffDateTime") LocalDateTime cutoffDateTime);

    int writeOffBulk(@Param("paymentObligationIds") List<Long> paymentObligationIds);

    List<Long> findSettlementIdsByObligationIds(@Param("obligationIds") List<Long> obligationIds);

    List<PaymentObligationDTO> findByParticipantIds(@Param("participantIds") List<Long> participantIds);

    Optional<PaymentObligationDTO> findByIdForUpdate(
            @Param("paymentObligationId") Long paymentObligationId
    );

    int updatePaymentStatus(
            @Param("paymentObligationId") Long paymentObligationId,
            @Param("paymentStatus") PaymentStatus paymentStatus
    );

    default List<MatchingCandidate> findMatchCandidatesByLinkedAccountId(
            @Param("linkedAccountId") Long linkedAccountId,
            @Param("transactionAt") LocalDateTime transactionAt
    ) {
        return findMatchCandidatesByLinkedAccountIdAndTarget(
                linkedAccountId,
                transactionAt,
                null,
                null
        );
    }

    List<MatchingCandidate> findMatchCandidatesByLinkedAccountIdAndTarget(
            @Param("linkedAccountId") Long linkedAccountId,
            @Param("transactionAt") LocalDateTime transactionAt,
            @Param("targetType") MatchingTargetType targetType,
            @Param("aggregateId") Long aggregateId
    );

    int insert(PaymentObligationDTO paymentObligation);

    List<PaymentObligationDTO> findUnpaidByParticipantIds(@Param("participantIds") List<Long> participantIds);

    int updateOverdueSinceBulk(@Param("paymentObligationIds") List<Long> paymentObligationIds,
                               @Param("overdueSince") LocalDateTime overdueSince);

    int clearOverdueSince(@Param("paymentObligationId") Long paymentObligationId);
}

