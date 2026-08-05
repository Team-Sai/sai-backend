package org.teamsai.saibackend.domain.link.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.exception.DomainException;
import org.teamsai.saibackend.global.jwt.JwtTokenProvider;
import org.teamsai.saibackend.global.security.CustomUserDetails;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class AccountLinkFlowController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final LinkedBankAccountService linkedBankAccountService;

    @PostMapping("/api/accounts/link/start")
    public ResponseEntity<Map<String, String>> startLink(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        String state = jwtTokenProvider.createLinkStateToken(userId);

        List<Long> alreadyLinkedAccountIds = linkedBankAccountService
                .getLinkedAccountIds(userId);

        String linkedIdsParam = alreadyLinkedAccountIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String redirectUrl = UriComponentsBuilder
                .fromUriString("http://localhost:8081/link/start")
                .queryParam("returnUrl", "http://localhost:8080/accounts/link/callback")
                .queryParam("state", state)
                .queryParam("excludeAccountIds", linkedIdsParam)
                .toUriString();

        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }


    @GetMapping("/accounts/link/callback")
    public String linkCallback(
            @RequestParam String state,
            @RequestParam String userKey,
            @RequestParam String accountIds
    ) {
        Long userId;
        try {
            userId = jwtTokenProvider.getUserIdFromLinkState(state)
                    .orElseThrow(UserErrorCode.INVALID_LINK_STATE::toException);
        } catch (DomainException e) {
            return "redirect:/mypage?error=invalid_link_state";
        }

        int updated = userMapper.updateUserKeyByUserId(userId, userKey);
        if (updated == 0) {
            return "redirect:/mypage?error=link_failed";
        }

        try {
            List<Long> ids = Arrays.stream(accountIds.split(",")).map(Long::parseLong).toList();
            linkedBankAccountService.linkAccountsByIds(userId, userKey, ids);
        } catch (DomainException e) {
            return "redirect:/mypage?error=account_link_failed";
        }

        return "redirect:/mypage";
    }
}
