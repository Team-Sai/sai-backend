package org.teamsai.saibackend.domain.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransactionMatchCandidateQueryDTO {

    private Long matchCandidateId;
    private Long bankTransactionId;
    private MatchingTargetType targetType;
    private Long targetId;
    private Long aggregateId;
    private String targetName;
    private String participantName;
    private BigDecimal expectedRemainingAmount;
    private MatchingAmountType amountMatchType;
    private LocalDateTime createdAt;
}
