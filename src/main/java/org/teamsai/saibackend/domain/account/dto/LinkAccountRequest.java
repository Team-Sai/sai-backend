package org.teamsai.saibackend.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "계좌 연동 요청 DTO")
public record LinkAccountRequest(
        @Schema(description = "선택된 계좌 목록")
        @NotEmpty(message = "연동할 계좌를 하나 이상 선택해주세요.")
        List<@Valid SelectedAccount> selectedAccounts
) {
    @Schema(description = "개별 선택 계좌 정보")
    public record SelectedAccount(
            @NotNull Long accountId,
            String accountAlias   // 사용자가 직접 입력하는 값만 클라이언트 신뢰
    ) {}
}
