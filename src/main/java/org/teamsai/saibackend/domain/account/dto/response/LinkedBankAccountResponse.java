package org.teamsai.saibackend.domain.account.dto.response;

import lombok.Builder;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.util.BankCodeResolver;

import java.math.BigDecimal;

import static org.teamsai.saibackend.global.util.MaskingUtil.maskAccountNumber;

@Builder
public record LinkedBankAccountResponse(
        Long linkedAccountId,
        String bankCode,
        String bankName,
        String maskedAccountNumber,
        String accountAlias,
        String accountHolderName,
        BigDecimal balance,
        String connectionStatus
) {
    public static LinkedBankAccountResponse from(LinkedBankAccountDTO entity) {
        return LinkedBankAccountResponse.builder()
                .linkedAccountId(entity.getLinkedAccountId())
                .bankCode(entity.getBankCode())
                .bankName(BankCodeResolver.resolveBankName(entity.getBankCode()))
                .maskedAccountNumber(maskAccountNumber(entity.getAccountNumber()))
                .accountAlias(entity.getAccountAlias())
                .accountHolderName(entity.getAccountHolderName())
                .balance(entity.getBalance())
                .connectionStatus(entity.getConnectionStatus().name())
                .build();
    }
}
