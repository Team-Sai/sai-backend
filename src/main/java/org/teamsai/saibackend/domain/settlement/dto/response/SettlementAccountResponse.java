package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.settlement.dto.SettlementAccountDTO;
import org.teamsai.saibackend.domain.settlement.type.SettlementAccountStatus;

import java.time.LocalDateTime;

@Getter
@Builder
public class SettlementAccountResponse {
    private Long settlementAccountId;
    private Long settlementId;
    private Long linkedAccountId;
    private SettlementAccountStatus accountStatus;
    private LocalDateTime selectedAt;

    public static SettlementAccountResponse from(SettlementAccountDTO account){
        return SettlementAccountResponse.builder()
                .settlementAccountId(account.getSettlementAccountId())
                .settlementId(account.getSettlementId())
                .linkedAccountId(account.getLinkedAccountId())
                .accountStatus(account.getAccountStatus())
                .selectedAt(account.getSelectedAt())
                .build();
    }
}
