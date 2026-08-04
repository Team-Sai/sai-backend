package org.teamsai.saibackend.domain.contract.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoanContractDebtorLinkRequest(
        @NotBlank(message = "본인인증 식별값은 필수입니다.")
        String identityVerificationId
) {
}
