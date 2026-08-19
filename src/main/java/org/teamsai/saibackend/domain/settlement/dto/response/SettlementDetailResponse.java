package org.teamsai.saibackend.domain.settlement.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementDetailResponse(
        Long settlementId,
        String title,
        String ownerName,
        String settlementCategory,
        String settlementType,
        String settlementStatus,
        String splitType,
        LocalDate dueDate,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        String role
) {
}