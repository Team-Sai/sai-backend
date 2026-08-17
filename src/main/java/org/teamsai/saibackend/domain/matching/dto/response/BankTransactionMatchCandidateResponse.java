package org.teamsai.saibackend.domain.matching.dto.response;

import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateQueryDTO;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;

import java.math.BigDecimal;

public record BankTransactionMatchCandidateResponse(
        Long matchCandidateId,
        MatchingTargetType targetType,
        Long targetId,
        Long aggregateId,
        String targetName,
        String participantName,
        BigDecimal expectedRemainingAmount,
        MatchingAmountType amountMatchType
) {

    public static BankTransactionMatchCandidateResponse from(
            BankTransactionMatchCandidateQueryDTO candidate
    ) {
        return new BankTransactionMatchCandidateResponse(
                candidate.getMatchCandidateId(),
                candidate.getTargetType(),
                candidate.getTargetId(),
                candidate.getAggregateId(),
                candidate.getTargetName(),
                candidate.getParticipantName(),
                candidate.getExpectedRemainingAmount(),
                candidate.getAmountMatchType()
        );
    }
}
