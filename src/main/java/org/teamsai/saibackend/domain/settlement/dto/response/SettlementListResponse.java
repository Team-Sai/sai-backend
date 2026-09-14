package org.teamsai.saibackend.domain.settlement.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
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
        BigDecimal totalAmount,
        LocalDate dueDate,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate cycleDate,
        @JsonIgnore LocalDateTime createdAt
) {
}
