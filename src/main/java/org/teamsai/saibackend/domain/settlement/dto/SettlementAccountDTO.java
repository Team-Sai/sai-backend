package org.teamsai.saibackend.domain.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.settlement.type.SettlementAccountStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementAccountDTO {

    private Long settlementAccountId;
    private Long settlementId;
    private Long linkedAccountId;
    private SettlementAccountStatus accountStatus;
    private LocalDateTime selectedAt;
    private LocalDateTime endedAt;
}
