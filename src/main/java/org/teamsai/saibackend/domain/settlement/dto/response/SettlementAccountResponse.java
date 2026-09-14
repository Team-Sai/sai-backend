package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
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

    private String bankName;
    private String maskedAccountNumber;
    private String accountHolderName;

    private LocalDateTime selectedAt;

    public static SettlementAccountResponse from(
            SettlementAccountDTO account,
            LinkedBankAccountResponse linkedAccount
    ) {
        return SettlementAccountResponse.builder()
                .settlementAccountId(account.getSettlementAccountId())
                .settlementId(account.getSettlementId())
                .linkedAccountId(account.getLinkedAccountId())
                .accountStatus(account.getAccountStatus())
                .bankName(linkedAccount.bankName())
                .maskedAccountNumber(linkedAccount.maskedAccountNumber())
                .accountHolderName(linkedAccount.accountHolderName())
                .selectedAt(account.getSelectedAt())
                .build();
    }
}