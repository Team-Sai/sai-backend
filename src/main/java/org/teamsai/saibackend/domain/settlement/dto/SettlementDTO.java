package org.teamsai.saibackend.domain.settlement.dto;

import lombok.*;
import org.teamsai.saibackend.domain.settlement.type.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementDTO {
    private Long settlementId;
    private Long ownerId;

    private SettlementType settlementType;
    private SettlementStatus settlementStatus;

    private String settlementCategory;
    private String title;
    private SplitType splitType;
    private LocalDate dueDate;

    // 정기정산에서 사용
    private CycleRule cycleRule;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate cycleDate;

    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    private SettlementDirection status;
}
