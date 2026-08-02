package org.teamsai.saibackend.domain.contract.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractService;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.service.UserService;


@Tag(
        name = "차용증 API",
        description = "차용증 작성과 저장,채무자에게 전송까지 API"
)
@Controller
@RequiredArgsConstructor
public class LoanContractController {

    private final LoanContractService contractService;


    @Operation(
            summary = "차용증 최초 생성",
            description = "채권자가 입력한 정보로 차용증 계약서를 최초 생성(임시저장)합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "차용증 생성 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "잘못된 입력값 요청"
            ),

    })


    @ResponseBody
    @PostMapping("/api/contracts")
    public Long createContract(
            @Valid @RequestBody LoanContractRequest request,
            @Parameter(hidden = true) Authentication authentication
    ) {
        String userKey = authentication.getName();
        return contractService.createContract(request, userKey);
    }

    @Operation(
            summary = "차용증 임시저장",
            description = "차용증 상태를 임시저장(DRAFT) 상태로 변경합니다."
    )
    @ResponseBody
    @PatchMapping("/{contractId}/draft")
    public ContractStatus saveDraft(@PathVariable Long contractId) {
        return contractService.saveDraftContract(contractId);
    }


    @Operation(
            summary = "차용증 전송",
            description = "차용증을 채권자에게 전송하고 대기(PENDING) 상태로 변경합니다."
    )
    @ResponseBody
    @PatchMapping("/{contractId}/send")
    public ContractStatus sendToDebtor(@PathVariable Long contractId){
        return contractService.approveByDebtor(contractId);
    }

    @Operation(
            summary = "채무자 승인",
            description = "채무자가 차용증을 최종 동의하여 완료(COMPLETED) 상태로 변경합니다."
    )
    @ResponseBody
    @PatchMapping("/{contractId}/approve")
    public ContractStatus approveByDebtor(@PathVariable Long contractId) {
        return contractService.approveByDebtor(contractId);
    }


    @Operation(
            summary = "차용증 상세 조회",
            description = "차용증 ID로 차용증 상세 내용을 조회합니다."
    )
    @ResponseBody
    @GetMapping("/{contractId}")
    public LoanContractResponse getContract(@PathVariable Long contractId) {
        return contractService.findContract(contractId);
    }


}
