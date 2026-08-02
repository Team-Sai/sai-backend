package org.teamsai.saibackend.domain.account.dto;

import lombok.Builder;

@Builder
public record LinkedBankAccountResponse(
        Long linkedAccountId,
        String bankCode,
        String maskedAccountNumber,
        String accountAlias,
        String accountHolderName,
        String connectionStatus
) {
    public static LinkedBankAccountResponse from(LinkedBankAccountDTO entity) {
        return LinkedBankAccountResponse.builder()
                .linkedAccountId(entity.getLinkedAccountId())
                .bankCode(entity.getBankCode())
                .maskedAccountNumber(entity.getAccountNumber())
                .accountAlias(entity.getAccountAlias())
                .accountHolderName(entity.getAccountHolderName())
                .connectionStatus(entity.getConnectionStatus().name())
                .build();
    }

    public static LinkedBankAccountResponse from(LinkedBankAccountDTO entity, LinkableAccountResponse mockResponse) {
        return LinkedBankAccountResponse.builder()
                .linkedAccountId(entity.getLinkedAccountId())
                .bankCode(entity.getBankCode())
                .maskedAccountNumber(entity.getAccountNumber())
                .accountAlias(entity.getAccountAlias())
                .accountHolderName(entity.getAccountHolderName())
                .connectionStatus(entity.getConnectionStatus().name())
                .build();
    }
}
