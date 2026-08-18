package org.teamsai.saibackend.domain.settlement.dto.response;

public record SettlementArchiveDetailResponse(
        Long settlementId,
        String title,
        String settlementType,
        String ownerName,
        String role
) {
}
