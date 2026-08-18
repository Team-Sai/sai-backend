package org.teamsai.saibackend.domain.contractrepaymentschedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.contractrepaymentschedule.type.RepaymentScheduleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentScheduleDTO {

    private Long scheduleId;
    private Long contractId;
    private Integer sequence;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;
    private BigDecimal totalPaymentDue;
    private BigDecimal remainingPaymentAmount;
    private BigDecimal remainingPrincipal;
    private RepaymentScheduleStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
