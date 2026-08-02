package org.teamsai.saibackend.domain.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.account.dto.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.LinkableAccountResponse;
import org.teamsai.saibackend.domain.account.service.ExternalBankService;

import java.util.List;

@Tag(
        name="계좌 조회 API",
        description = "계좌 상세, 연동 가능 계좌 목록 조회 관련 API"
)
@RestController
@RequestMapping("/api/mock-bank/accounts")
@RequiredArgsConstructor
@Slf4j
public class ExternalBankController {

    private final ExternalBankService externalBankService;

    @Operation(summary = "연동 가능한 사이은행 계좌 목록 조회")
    @GetMapping("/available")
    public ResponseEntity<List<LinkableAccountResponse>> getAvailableAccounts(
            Authentication authentication
    ) {
        String userToken = authentication.getName();

        List<LinkableAccountResponse> accounts = externalBankService.fetchAvailableAccountsFromBank(userToken);

        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "계좌 상세 조회")
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailResponse> getAccount(
            @PathVariable Long accountId,
            @RequestParam("userKey") String userKey
    ) {
        log.info("[sai-member] 계좌 상세 조회 요청 전달 - accountId: {}, userKey: {}", accountId, userKey);

        AccountDetailResponse response = externalBankService.getAccountDetail(accountId, userKey);

        return ResponseEntity.ok(response);
    }
}
