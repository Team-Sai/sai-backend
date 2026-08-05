package org.teamsai.saibackend.domain.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PaymentObligationMapper {
    Optional<PaymentObligationDTO> findByIdForUpdate(
            @Param("paymentObligationId") Long paymentObligationId
    );

    int updatePaymentStatus(
            @Param("paymentObligationId") Long paymentObligationId,
            @Param("paymentStatus") PaymentStatus paymentStatus
    );

    List<MatchingCandidate> findMatchCandidatesByLinkedAccountId(
            @Param("linkedAccountId") Long linkedAccountId,
            @Param("transactionAt") LocalDateTime transactionAt
    );
}

