package org.teamsai.saibackend.domain.integration.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DashboardMonthlySummaryResponse {

    private int completedTransactionCount;

    private int inProgressSettlementCount;

    private int inProgressLoanRepaymentCount;

    private BigDecimal transactionCompletionRate;
}
