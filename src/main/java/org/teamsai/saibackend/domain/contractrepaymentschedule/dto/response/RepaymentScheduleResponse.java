package org.teamsai.saibackend.domain.contractrepaymentschedule.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.RepaymentScheduleDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class RepaymentScheduleResponse {
    private Long scheduleId;
    private Integer sequence;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;
    private BigDecimal totalPaymentDue;
    private BigDecimal remainingPrincipal;
    private String status;
    private LocalDateTime paidAt;

    public static RepaymentScheduleResponse from(RepaymentScheduleDTO dto) {
        return RepaymentScheduleResponse.builder()
                .scheduleId(dto.getScheduleId())
                .sequence(dto.getSequence())
                .dueDate(dto.getDueDate())
                .principalDue(dto.getPrincipalDue())
                .interestDue(dto.getInterestDue())
                .totalPaymentDue(dto.getTotalPaymentDue())
                .remainingPrincipal(dto.getRemainingPrincipal())
                .status(dto.getStatus())
                .paidAt(dto.getPaidAt())
                .build();
    }
}
