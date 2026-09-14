package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.Builder;
import org.teamsai.saibackend.domain.payment.type.SourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record SettlementPaymentHistoryResponse(
        Long paymentRecordId,
        LocalDateTime recordedAt,
        String payerName,
        BigDecimal amount,
        SourceType sourceType,
        Long bankTransactionId,
        String counterpartyName,
        String externalTransactionId
) {
}
