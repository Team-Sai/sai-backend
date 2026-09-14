package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record SettlementArchivePreviewResponse(
        Long settlementId,
        String settlementDisplayId,
        String title,
        String ownerName,
        String settlementType,
        String settlementCategory,
        String settlementStatus,
        String splitType,
        LocalDate dueDate,
        LocalDateTime createdAt,
        SettlementPaymentStatusResponse paymentStatus,
        List<SettlementPaymentHistoryResponse> paymentHistory,
        SettlementAccountResponse settlementAccount,
        String documentVersion
) {
}
