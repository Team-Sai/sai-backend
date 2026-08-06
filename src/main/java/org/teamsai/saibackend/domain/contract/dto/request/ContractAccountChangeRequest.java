package org.teamsai.saibackend.domain.contract.dto.request;

import jakarta.validation.constraints.NotNull;

public record ContractAccountChangeRequest(
        @NotNull(message = "변경할 계좌를 선택해주세요.")
        Long linkedAccountId
) {
}
