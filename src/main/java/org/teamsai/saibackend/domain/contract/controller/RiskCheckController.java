package org.teamsai.saibackend.domain.contract.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.contract.service.RiskCheckService;
import org.teamsai.saibackend.global.security.CustomUserDetails;

@Slf4j
@RequiredArgsConstructor
@Controller
public class RiskCheckController {

    private final RiskCheckService riskCheckService;

    @Operation(
            summary = "로그인한 사용자의 이전 차용금 합계 조회",
            description = "세금 안심 가이드 모달 계산을 위해 로그인된 사용자의 이전 차용금 총액을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @ResponseBody
    @GetMapping("/api/contracts/previous-sum")
    public Long getPreviousTotalAmount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return riskCheckService.getPreviousTotalAmount(userDetails.getUserId());
    }
}
