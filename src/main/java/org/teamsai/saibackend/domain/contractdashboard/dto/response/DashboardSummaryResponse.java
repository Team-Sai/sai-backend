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

    private BigDecimal thisMonthDueAmount;
    private Integer dueMonth;

    private BigDecimal receivableThisMonthAmount;
    private Integer receivableDueMonth;

    private BigDecimal payableThisMonthAmount;
    private Integer payableDueMonth;

    private LocalDate nearestDueDate;
    private String defaultFilter;
}