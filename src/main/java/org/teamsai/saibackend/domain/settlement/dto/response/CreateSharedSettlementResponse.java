package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSharedSettlementResponse {
    private Long settlementId;
    private SettlementType settlementType;
    private SettlementStatus settlementStatus;
    private String title;
    private LocalDateTime createdAt;
}
