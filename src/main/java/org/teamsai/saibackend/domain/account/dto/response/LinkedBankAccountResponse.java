package org.teamsai.saibackend.domain.account.dto.response;

import lombok.Builder;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.dto.type.ConnectionStatus;
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
    public static LinkedBankAccountResponse from(LinkedBankAccountDTO dto) {
        return LinkedBankAccountResponse.builder()
                .linkedAccountId(dto.getLinkedAccountId())
                .bankCode(dto.getBankCode())
                .bankName(BankCodeResolver.resolveBankName(dto.getBankCode()))
                .maskedAccountNumber(maskAccountNumber(dto.getAccountNumber()))
                .accountAlias(dto.getAccountAlias())
                .accountHolderName(dto.getAccountHolderName())
                .balance(dto.getBalance())
                .connectionStatus(dto.getConnectionStatus().name())
                .build();
    }
}
