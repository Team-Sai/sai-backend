package org.teamsai.saibackend.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LinkableAccountResponse(
        Long accountId,
        String accountNumber,
        String accountName,
        Long balance,
        @JsonProperty("accountHolderName")
        String ownerName,
        Boolean isSelected
) {
    public LinkableAccountResponse {
        if (isSelected == null) {
            isSelected = false;
        }
    }
}
