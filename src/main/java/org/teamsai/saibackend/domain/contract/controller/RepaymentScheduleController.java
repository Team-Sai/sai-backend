package org.teamsai.saibackend.domain.contract.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.contract.dto.response.RepaymentScheduleSummaryResponse;
import org.teamsai.saibackend.domain.contract.service.RepaymentScheduleService;

@Tag(
        name = "차용증 API",
        description = "계약의 회차별 상환 스케줄고 ㅏ진행 현황을 조회하는 API"
)
@Controller
@RequiredArgsConstructor
public class RepaymentScheduleController {

    private final RepaymentScheduleService repaymentScheduleService;

    @Hidden
    @GetMapping("/contracts/{contractId}/schedule")
    public String schedulePage(
            @PathVariable Long contractId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        return "contract/schedule";
    }

    @Operation(
            summary = "상환 스케줄 요약 조회",
            description = "계약의 회차별 상환 스케줄 전체 목록과, 총 상환예정액, 누적 납부액, 잔여 상환액, 진행률 등" +
                    "요약 정보를 함께 조회합니다."
    )

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "계약 당사자가 아님"),
            @ApiResponse(responseCode = "404", description = "계약을 찾을 수 없음")
    })

    @ResponseBody
    @GetMapping("/api/contracts/{contractId}/schedules")
    public RepaymentScheduleSummaryResponse getScheduleSummary(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userID
    ) {
        return repaymentScheduleService.getScheduleSummary(contractId, userID);
    }
}