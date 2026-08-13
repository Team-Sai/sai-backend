package org.teamsai.saibackend.domain.integration.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DashboardMoneyBreakdownResponse {

    private BigDecimal totalAmount;

    private BigDecimal settlementAmount;

    private BigDecimal loanAmount;
}
