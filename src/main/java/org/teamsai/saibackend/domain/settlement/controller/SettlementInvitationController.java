package org.teamsai.saibackend.domain.settlement.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementInvitationRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSettlementInvitationResponse;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationService;

@Tag(
        name = "정산 초대 API",
        description = "정산 참여자 초대 전송 및 관리 API"
)
@Controller
@RequiredArgsConstructor
public class SettlementInvitationController {
    private final SettlementInvitationService service;


    @ResponseBody
    @PostMapping("/api/settlements/{settlementId}/invitations")
    public ResponseEntity<CreateSettlementInvitationResponse>invite(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId")Long userId,
            @Parameter(description = "초대를 보낼 정산 ID", example = "1")
            @PathVariable Long settlementId,
            @Valid
            @RequestBody CreateSettlementInvitationRequest request
            ){
        CreateSettlementInvitationResponse response =
                service.invite(
                        userId, settlementId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

}
