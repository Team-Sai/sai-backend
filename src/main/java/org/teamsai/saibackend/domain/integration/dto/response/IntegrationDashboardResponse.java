package org.teamsai.saibackend.domain.integration.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.YearMonth;
import java.util.List;

@Getter
@Builder
public class IntegrationDashboardResponse {

    private YearMonth yearMonth;

    private DashboardAmountSummaryResponse amountSummary;

    private DashboardMonthlySummaryResponse monthlySummary;

    private List<DashboardAttentionItemResponse> attentionItems;

    private List<DashboardRecentTransactionResponse> recentTransactions;

    private List<DashboardCalendarDayResponse> calendarDays;
}
