package org.teamsai.saibackend.domain.account.dto;

import lombok.Builder;
import org.teamsai.saibackend.domain.account.util.BankCodeResolver;

import static org.teamsai.saibackend.global.util.MaskingUtil.maskAccountNumber;

@Builder
public record LinkedBankAccountResponse(
        Long linkedAccountId,
        String bankCode,
        String bankName,
        String maskedAccountNumber,
        String accountAlias,
        String accountHolderName,
        Long balance,
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
    private static String maskAccountNumber(String raw) {
        if (raw == null || raw.length() < 4) {
            return raw;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return raw;
        }
        String prefix = digits.substring(0, Math.min(3, digits.length() - 4));
        String tail = digits.substring(digits.length() - 4);
        return prefix + "-***-" + tail;
    }
}
