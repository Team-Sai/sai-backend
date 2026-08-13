package org.teamsai.saibackend.domain.integration.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardAmountSummaryResponse {

    private DashboardMoneyBreakdownResponse receivable;
    private DashboardMoneyBreakdownResponse payable;
}