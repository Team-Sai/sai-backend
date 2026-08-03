package org.teamsai.saibackend.domain.contract.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractService;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.security.CustomUserDetails;


@Tag(
        name = "차용증 API",
        description = "차용증 작성과 저장,채무자에게 전송까지 API"
)
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/contracts")
public class LoanContractController {

    private final LoanContractService contractService;

    @Operation(hidden = true)
    @GetMapping
    public String contractFormPage() {
        return "contract/contract-form";
    }

    @Operation(hidden = true)
    @GetMapping("/signature")
    public String contractSignaturePage() {
        return "contract/contract-signature";
    }


    @Operation(
            summary = "차용증 최초 생성",
            description = "채권자가 입력한 정보로 차용증 계약서를 최초 생성합니다."
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
    @PostMapping
    public Long createContract(
            @Valid @RequestBody LoanContractRequest request,
            @Parameter(hidden = true) Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return contractService.createContract(request, userDetails.getUserId());
    }

    @Operation(
            summary = "채권자 전자서명 제출 및 전송",
            description = "채권자가 수기로 남긴 서명 이미지를 저장하고, 상태를 대기(PENDING)로 변경하여 채무자에게 전송합니다."
    )
    @ResponseBody
    @PatchMapping(value = "/{contractId}/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractStatus submitSignature(
            @PathVariable Long contractId,
            @RequestParam("signature") MultipartFile signature
    ) {
        return contractService.submitCreditorSignature(contractId, signature);
    }

    @Operation(
            summary = "채무자 승인 및 전자서명 제출",
            description = "채무자가 수기로 남긴 서명 이미지를 저장하고, 상태를 완료(COMPLETED)로 변경합니다."
    )
    @ResponseBody
    @PatchMapping(value = "/{contractId}/approve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractStatus approveByDebtor(
            @PathVariable Long contractId,
            @RequestParam("signature") MultipartFile signature
    ) {
        return contractService.submitDebtorSignature(contractId, signature);
    }


    @Operation(
            summary = "차용증 상세 조회",
            description = "차용증 ID로 차용증 상세 내용을 조회합니다."
    )
    @ResponseBody
    @GetMapping("/{contractId}")
    public LoanContractResponse getContract(
            @PathVariable Long contractId,
            @Parameter(hidden = true) Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return contractService.findContract(contractId, userDetails.getUserId());
    }


}
