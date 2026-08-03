package org.teamsai.saibackend.domain.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.account.dto.request.LinkAccountRequest;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.global.security.CustomUserDetails;

import java.util.List;

@Tag(
        name="은행 연동 완료 계좌 API",
        description = "연결된 계좌 API"
)
@RestController
@RequestMapping("/api/linked-accounts")
@RequiredArgsConstructor
@Slf4j
public class LinkedBankAccountController {

    private final LinkedBankAccountService linkedBankAccountService;

    @Operation(summary = "선택한 계좌들 등록", description = "사용자가 선택한 모의 은행 계좌들을 사이원장에 연동.")
    @PostMapping
    public ResponseEntity<List<LinkedBankAccountResponse>> linkAccounts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody LinkAccountRequest request
    ) {
        List<LinkedBankAccountResponse> responses =
                linkedBankAccountService.linkSelectedAccounts(userDetails.getUserId(), request);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "연동 완료된 계좌 목록 조회")
    @GetMapping
    public ResponseEntity<List<LinkedBankAccountResponse>> getMyLinkedAccounts(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        List<LinkedBankAccountResponse> response =
                linkedBankAccountService.getLinkedAccounts(userId);
        return ResponseEntity.ok(response);
    }
}