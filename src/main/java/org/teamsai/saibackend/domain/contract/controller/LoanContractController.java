package org.teamsai.saibackend.domain.contract.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractDebtorLinkRequest;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.global.security.CustomUserDetails;

import java.util.List;

@Tag(
        name = "차용증 API",
        description = "차용증 작성과 저장, 채무자에게 전송 API"
)
@Controller
@RequiredArgsConstructor
@Slf4j
public class LoanContractController {

    private final LoanContractService contractService;

    @Operation(hidden = true)
    @GetMapping("/contracts/new")
    public String contractFormPage() {
        return "contract/contract-form";
    }

    @Operation(hidden = true)
    @GetMapping("/contracts/signature")
    public String contractSignaturePage() {
        return "contract/contract-signature";
    }

    @Operation(hidden = true)
    @GetMapping("/contracts/{contractId}/approve")
    public String contractDebtorApprovePage(
            @PathVariable Long contractId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        return "contract/contract-debtor-form";
    }

    @Operation(hidden = true)
    @GetMapping("/contracts/{contractId}/approve/signature")
    public String contractDebtorSignaturePage(
            @PathVariable Long contractId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        return "contract/contract-debtor-signature";
    }

    @Operation(hidden = true)
    @GetMapping("/notifications")
    public String notificationCenterPage() {
        return "notification/notification-center";
    }


    @Operation(
            summary = "차용증 최초 생성",
            description = "채권자가 입력한 정보로 차용증 계약서를 최초 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "차용증 생성 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "잘못된 입력값 요청")
    })
    @ResponseBody
    @PostMapping("/api/contracts/write")
    public Long createContract(
            @Valid @RequestBody LoanContractRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return contractService.createContract(request, userDetails.getUserId());
    }

    @Operation(
            summary = "채권자 전자서명 제출 및 전송",
            description = "채권자가 수기로 남긴 서명 이미지를 저장하고, 상태를 대기(PENDING)로 변경하여 채무자에게 전송합니다."
    )
    @ResponseBody
    @PatchMapping(value = "/api/contracts/{contractId}/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractStatus submitSignature(
            @PathVariable Long contractId,
            @RequestParam("signature") MultipartFile signature,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return contractService.submitCreditorSignature(contractId, userDetails.getUserId(), signature);
    }

    @Operation(
            summary = "채무자 계약 합류",
            description = "본인인증을 완료한 채무자가 차용증에 채무자로 연결됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "채무자 연결 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "차용증을 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 채무자가 연결되었거나 본인인증이 완료되지 않음")
    })
    @ResponseBody
    @PatchMapping("/api/contracts/{contractId}/debtor")
    public void linkDebtor(
            @PathVariable Long contractId,
            @Valid @RequestBody LoanContractDebtorLinkRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        contractService.linkDebtor(contractId, userDetails.getUserId(), request);
    }

    @Operation(
            summary = "수신한 차용증 목록 조회",
            description = "로그인한 사용자가 채무자로 지정되어 서명 대기 중인(PENDING) 차용증 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @ResponseBody
    @GetMapping("/api/contracts/incoming")
    public ResponseEntity<List<LoanContractResponse>> getIncomingContracts(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<LoanContractResponse> incomingContracts = contractService.findPendingContractsByDebtorId(userDetails.getUserId());
        return ResponseEntity.ok(incomingContracts);
    }

    @Operation(
            summary = "채무자 승인 및 전자서명 제출",
            description = "채무자가 본인 주소를 입력하고 수기로 남긴 서명 이미지를 저장한 뒤, 상태를 완료(COMPLETED)로 변경합니다."
    )
    @ResponseBody
    @PatchMapping(value = "/api/contracts/{contractId}/approve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractStatus approveByDebtor(
            @PathVariable Long contractId,
            @Parameter(description = "채무자 본인 주소") @RequestParam("debtorAddress") String debtorAddress,
            @Parameter(description = "채무자 서명 이미지 파일") @RequestParam("signature") MultipartFile signature,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return contractService.submitDebtorSignature(contractId, userDetails.getUserId(), debtorAddress, signature);
    }

    @Operation(
            summary = "차용증 상세 조회",
            description = "로그인이 된 사용자가 차용증 ID로 차용증 상세 내용을 조회합니다."
    )
    @ResponseBody
    @GetMapping("/api/contracts/{contractId}/listdetails")
    public LoanContractResponse getContractDetails(
            @PathVariable Long contractId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return contractService.findContract(contractId, userDetails.getUserId());
    }
}