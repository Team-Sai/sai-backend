package org.teamsai.saibackend.domain.link.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.link.dto.response.UserKeyResponse;
import org.teamsai.saibackend.domain.user.service.AccountService;


@Tag(
        name="은행 연동 API",
        description = "사용자 연동키 요청 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-bank")
public class BankLinkController {
    private final AccountService accountService;

    @Operation(summary = "연동키 생성 요청")
    @PostMapping("/link")
    public ResponseEntity<UserKeyResponse> createUserKey(Authentication authentication) {
        String userToken = authentication.getName();
        UserKeyResponse response = accountService.issueOrGetUserKey(userToken);
        return ResponseEntity.ok(response);
    }
}
