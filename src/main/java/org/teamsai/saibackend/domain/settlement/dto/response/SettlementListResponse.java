package org.teamsai.saibackend.domain.settlement.dto.response;

import java.time.LocalDate;

public record SettlementListResponse(
        Long settlementId,
        String title,
        String role,
        String settlementCategory,
        String settlementType,
        String splitType,
        String settlementStatus,
        LocalDate dueDate
) {
}