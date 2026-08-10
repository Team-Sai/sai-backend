package org.teamsai.saibackend.domain.contractrepaymentschedule.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class RepaymentScheduleSummaryResponse {

    private String creditorName;
    private String debtorName;

    private BigDecimal totalScheduledAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private Integer paidCount;
    private Integer totalCount;
    private List<RepaymentScheduleResponse> schedules;
}
