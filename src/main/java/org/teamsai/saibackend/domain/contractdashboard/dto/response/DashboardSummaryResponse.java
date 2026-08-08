package org.teamsai.saibackend.domain.contractdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class DashboardSummaryResponse {

    private int totalContractCount;
    private BigDecimal totalLentAmount;
    private BigDecimal totalBorrowedAmount;
    private LocalDate nearestDueDate;
    private String defaultFilter;
    private BigDecimal thisMonthDueAmount;
}
