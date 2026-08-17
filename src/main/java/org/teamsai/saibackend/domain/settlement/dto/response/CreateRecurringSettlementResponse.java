package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.settlement.type.CycleRule;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRecurringSettlementResponse {

    private Long recurringSettlementId;

    private Long firstSettlementId;

    private SettlementType settlementType;

    private String title;

    private CycleRule cycleRule;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDateTime createdAt;
}