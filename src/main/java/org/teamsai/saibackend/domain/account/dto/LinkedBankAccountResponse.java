package org.teamsai.saibackend.domain.account;

import lombok.Builder;

@Builder
public record LinkedBankAccountResponse(
        Long linkedAccountId,       // 연동 계좌 식별자 (PK)
        String bankCode,            // 은행 코드
        String maskedAccountNumber,  // 마스킹된 계좌번호
        String accountAlias,        // 계좌 별칭
        String accountHolderName,   // 예금주명
        String connectionStatus     // 연동 상태
) {
    public static LinkedBankAccountResponse from(LinkedBankAccountDTO entity) {
        return LinkedBankAccountResponse.builder()
                .linkedAccountId(entity.getLinkedAccountId())
                .bankCode(entity.getBankCode())
                .maskedAccountNumber(entity.getMaskedAccountNumber())
                .accountAlias(entity.getAccountAlias())
                .accountHolderName(entity.getAccountHolderName())
                .connectionStatus(entity.getConnectionStatus().name())
                .build();
    }
}
