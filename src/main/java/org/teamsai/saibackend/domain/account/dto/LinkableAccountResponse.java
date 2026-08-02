package org.teamsai.saibackend.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LinkableAccountResponse(
        Long accountId,
        String accountNumber,
        String accountName,
        String bankCode,
        Long balance,
        String accountHolderName
) {
}
