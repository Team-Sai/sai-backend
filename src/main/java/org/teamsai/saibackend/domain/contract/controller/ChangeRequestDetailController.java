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
import org.teamsai.saibackend.domain.contract.dto.ChangeRequestDetailDTO;
import org.teamsai.saibackend.domain.contract.service.ChangeRequestDetailService;

@Tag(
        name = "차용증 API",
        description = "채무자가 수신한 계약 변경 요청의 변경 전/후 조건을 비교하여 조회하는 API"
)
@Controller
@RequiredArgsConstructor
public class ChangeRequestDetailController {

    private final ChangeRequestDetailService changeRequestDetailService;

    @Hidden
    @GetMapping("/contracts/{contractId}/change-requests/{changeRequestId}")
    public String changeRequestDetailPage(
            @PathVariable Long contractId,
            @PathVariable Long changeRequestId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        model.addAttribute("changeRequestId", changeRequestId);
        return "contract/request-detail";
    }

    @Operation(
            summary = "계약 변경 요청 상세 조회",
            description = "특정 변경 요청 건의 현재 조건과 변경 요청 조건을 비교하여 조회합니다." +
                    "예상 월 상환액, 상환 기간 변화(연장/단축 개월 수), 변경 사유를 함께 제출합니다."
    )

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "계약 당사자가 아님"),
            @ApiResponse(responseCode = "404", description = "계약 또는 변경 요청을 찾을 수 없음")
    })

    @ResponseBody
    @GetMapping("/api/contracts/{contractId}/change-requests/{changeRequestId}")
    public ChangeRequestDetailDTO requestDetail(
        @PathVariable Long contractId,
        @PathVariable Long changeRequestId,
        @AuthenticationPrincipal(expression = "userId") Long userId
    ){
        return changeRequestDetailService.getDetail(contractId, changeRequestId, userId);
    }
}
