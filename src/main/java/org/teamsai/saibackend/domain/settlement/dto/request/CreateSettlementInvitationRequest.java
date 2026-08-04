package org.teamsai.saibackend.domain.settlement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSettlementInvitationRequest {
    @NotBlank(message = "초대할 회원토큰을 입력해 주세요.")
    private String userToken;
}
