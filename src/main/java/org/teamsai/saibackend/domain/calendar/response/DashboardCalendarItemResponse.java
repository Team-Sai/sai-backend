package org.teamsai.saibackend.domain.calendar.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter@Builder
public class DashboardCalendarItemResponse {

    private Long targetId;
    private PaymentTargetType type;
    private String title;
    private String subLabel;
    private BigDecimal amount;
    private String detailUrl;
    private String counterpartyName;
    private String categoryLabel;
    private String installmentInfo;    
    private boolean overdue;

    private LocalDate maturityDate;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;

    private String settlementTypeLabel;
    private String splitTypeLabel;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
}
