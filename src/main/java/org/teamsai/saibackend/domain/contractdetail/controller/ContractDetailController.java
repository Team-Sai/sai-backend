package org.teamsai.saibackend.domain.contractdetail.controller;


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
import org.teamsai.saibackend.domain.contractdetail.dto.response.ContractDetailResponse;
import org.teamsai.saibackend.domain.contractdetail.service.ContractDetailService;

@Tag(
        name = "차용증 API",
        description = "체결이 완료된 계약서 원문 내용을 조회하는 API"
)
@Controller
@RequiredArgsConstructor
public class ContractDetailController {

    private final ContractDetailService contractDetailService;

    @Hidden
    @GetMapping("/contracts/{contractId}/contract-detail")
    public String detailResponsePage(
            @PathVariable Long contractId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        return "contractdetail/detail";
    }

    @Operation(
            summary = "계약서 상세 조회",
            description = "계약 다상자(채권자 또는 채무자)가 계약서의 원금, 이자율, 기간, 상환방식, 특약사항 등 전체 내용을 조회합니다." +
                    "당사자가 아니면 조회할 수 없습니다."
    )

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "계약 당사자가 아님"),
            @ApiResponse(responseCode = "404", description = "계약을 찾을 수 없음")
    })
    @ResponseBody
    @GetMapping("/api/contracts/{contractId}/contract-detail")
    public ContractDetailResponse responseDetail(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return contractDetailService.getCheck(contractId, userId);
    }


}

