package org.teamsai.saibackend.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record LinkableAccountResponse(
        Long accountId,
        String accountNumber,
        String accountName,
        String bankCode,
        BigDecimal balance,
        String accountHolderName
) {
}
