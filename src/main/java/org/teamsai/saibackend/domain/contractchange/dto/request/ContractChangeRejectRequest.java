package org.teamsai.saibackend.domain.contractchange.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter@Builder
@NoArgsConstructor@AllArgsConstructor
public class ContractChangeRejectRequest {

    @NotBlank(message = "반려 사유는 필수입니다.")
    private String returnReason;
}
