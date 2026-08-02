package org.teamsai.saibackend.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "계좌 연동 요청 DTO")
public record LinkAccountRequest(
        @Schema(description = "선택된 계좌 목록")
        List<SelectedAccount> selectedAccounts
) {
    @Schema(description = "개별 선택 계좌 정보")
    public record SelectedAccount(
            Long accountId,
            String bankCode,
            String accountNumber,
            String accountName,
            String accountHolderName,
            String accountAlias,
            Long balance
    ) {}
}
