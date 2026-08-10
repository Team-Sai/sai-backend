package org.teamsai.saibackend.domain.settlement.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectSettlementAccountRequest {

    @NotNull(message = "정산 수취 계좌를 선택해 주세요.")
    private Long linkedAccountId;
}
