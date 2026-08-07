package org.teamsai.saibackend.domain.contract.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.contract.dto.request.ContractAccountChangeRequest;
import org.teamsai.saibackend.domain.contract.service.ContractAccountService;

import java.util.List;

@Tag(
        name = "차용증 연동 계좌 API",
        description = "차용증 작성 및 연동 계좌 변경/해제 관련 API"
)
@RestController
@RequestMapping("/api/contracts/accounts")
@RequiredArgsConstructor
public class ContractAccountController {

    private final ContractAccountService contractAccountService;

    @Operation(
            summary = "차용증 작성용 선택 가능 계좌 목록 조회",
            description = "본인이 연동한 계좌 중 현재 활성상태인 계좌 목록을 조회합니다. " +
                    "차용증 작성 화면의 계좌번호/예금주 드롭다운에 사용됩니다."
    )
    @GetMapping
    public ResponseEntity<List<LinkedBankAccountResponse>> getSelectableAccounts(
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return ResponseEntity.ok(contractAccountService.getSelectableAccounts(userId));
    }

    @Operation(
            summary = "차용증 연결 계좌 변경",
            description = "차용증에 연결된 기존 활성 계좌를 REPLACED 처리하고, 새로 선택한 활성 계좌를 연결합니다."
    )
    @PatchMapping("/{contractId}/changeaccount")
    public ResponseEntity<Void> changeContractAccount(
            @PathVariable("contractId") Long contractId,
            @Valid @RequestBody ContractAccountChangeRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        contractAccountService.changeContractAccount(contractId, userId, request.linkedAccountId());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "차용증 연결 계좌 해제",
            description = "차용증에 연결된 기존 활성 계좌를 DISABLED 처리합니다."
    )
    @DeleteMapping("/{contractId}/deactivateaccount")
    public ResponseEntity<Void> deactivateContractAccount(
            @PathVariable("contractId") Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        contractAccountService.deleteContractAccount(contractId, userId);
        return ResponseEntity.noContent().build();
    }
}