package org.teamsai.saibackend.domain.settlement.dto.response;

import java.math.BigDecimal;

public record SettlementSummaryResponse(
        BigDecimal receivableAmount,
        long receivableCount,
        BigDecimal payableAmount,
        long payableCount
) {
}
