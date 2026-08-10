package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCloseResponse {

    private Long settlementId;
    private SettlementStatus settlementStatus;
    private LocalDateTime closedAt;
}
