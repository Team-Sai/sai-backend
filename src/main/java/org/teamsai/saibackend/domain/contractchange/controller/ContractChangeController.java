package org.teamsai.saibackend.domain.contractchange.controller;


import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRejectRequest;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;

@Tag(
        name = "차용증 API",
        description = "채권자가 만기일, 이율, 상환방식, 특약사항 등 계약 조건 변경을 요청하는 API"
)
@Controller
@RequiredArgsConstructor
public class ContractChangeController {

    private final ContractChangeService contractChangeService;

    @Hidden
    @GetMapping("/contracts/{contractId}/change-request")
    public String changeRequestPage(
            @PathVariable Long contractId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        return "contractchange/request";
    }

    @Operation(
            summary = "계약 조건 변경 요청",
            description = "채권자가 기존 계약의 만기일, 이율, 상환방식, 상환일, 특약사항 중 하나 이상을 변경 요청합니다." +
                    "변경하지 않는 항목은 값을 비워두면 기존 계약 값이 그대로 유지됩니다." +
                    "요청이 등록되면 채무자의 승인을 거쳐 계약에 반영됩니다." +
                    "이미 처리 대기 중인 변경 요청이 있으면 새 요청을 등록할 수 없습니다."
    )

    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "변경 요청 등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값이 유효하지 않음 (이율 범위, 상환일 범위 등"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "계약 당사자(채권자)가 아님"),
            @ApiResponse(responseCode = "404", description = "계약을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 처리 대기 중인 변경 요청이 있음")

    })

    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/contracts/{contractId}/change-requests")
    public LoanContractChangeDTO requestChange(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractChangeRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ){
        return contractChangeService.requestChange(contractId, request, userId);
    }

    @Operation(
            summary = "계약 변경 요청 반려",
            description = "채무자가 대기 중인 계약 변경 요청을 반려 사유와 함께 거절합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 처리 성공"),
            @ApiResponse(responseCode = "400", description = "반려 사유가 비어있음"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "계약 당사자(채무자)가 아님"),
            @ApiResponse(responseCode = "404", description = "계약 또는 변경 요청을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 처리된 요청임")
    })
    @ResponseBody
    @PatchMapping("/api/contracts/{contractId}/change-requests/{changeRequestId}/reject")
    public LoanContractChangeDTO rejectChange(
            @PathVariable Long contractId,
            @PathVariable Long changeRequestId,
            @Valid @RequestBody ContractChangeRejectRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return contractChangeService.rejectChange(contractId, changeRequestId, request.getReturnReason(), userId);
    }
}
