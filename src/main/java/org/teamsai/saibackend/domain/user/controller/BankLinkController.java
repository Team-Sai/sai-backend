package org.teamsai.saibackend.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.user.dto.LinkAccountRequest;
import org.teamsai.saibackend.domain.user.dto.UserKeyResponse;
import org.teamsai.saibackend.domain.user.service.AccountService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-bank")
public class BankLinkController {
    private final AccountService accountService;

    @PostMapping("/link")
    public ResponseEntity<UserKeyResponse> createUserKey(@RequestBody LinkAccountRequest request) {
        UserKeyResponse response = accountService.issueOrGetUserKey(
                request.userId(), request.name(), request.email()
        );
        return ResponseEntity.ok(response);
    }
}
