package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementPaymentStatusResponse {

    private Long settlementId;

    private List<SettlementPaymentObligationResponse> obligations;

    private BigDecimal totalExpectedAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal totalRemainingAmount;

    private long paidCount;
    private long partiallyPaidCount;
    private long unpaidCount;

    private BigDecimal progressRate;
    private boolean closable;
}
