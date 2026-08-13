package org.teamsai.saibackend.domain.settlement.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementListResponse(
        Long settlementId,
        String title,
        String role,
        String settlementCategory,
        String settlementType,
        String splitType,
        String settlementStatus,
        LocalDate dueDate,
        @JsonIgnore LocalDateTime createdAt
) {
}
