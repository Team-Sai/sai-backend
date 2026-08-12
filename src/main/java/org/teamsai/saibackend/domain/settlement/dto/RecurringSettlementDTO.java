package org.teamsai.saibackend.domain.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.settlement.type.CycleRule;
import org.teamsai.saibackend.domain.settlement.type.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringSettlementDTO {

    private Long recurringSettlementId;
    private Long ownerId;
    private String settlementCategory;
    private String title;

    private SplitType splitType;
    private BigDecimal totalAmount;

    private CycleRule cycleRule;
    private LocalDate startDate;
    private LocalDate endDate;

    private LocalDateTime createdAt;
}
