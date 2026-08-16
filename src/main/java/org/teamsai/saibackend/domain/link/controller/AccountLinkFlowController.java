package org.teamsai.saibackend.domain.link.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.identity.service.IdentityValidator;
import org.teamsai.saibackend.domain.link.service.AccountLinkService;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.exception.DomainException;
import org.teamsai.saibackend.global.jwt.JwtTokenProvider;
import org.teamsai.saibackend.global.security.CustomUserDetails;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AccountLinkFlowController {

    @Value("${sai.mock-bank.base-url}")
    private String mockBankBaseUrl;

    @Value("${sai.backend.base-url}")
    private String backendBaseUrl;

    private final JwtTokenProvider jwtTokenProvider;
    private final LinkedBankAccountService linkedBankAccountService;
    private final AccountLinkService accountLinkService;
    private final UserService userService;
    private final IdentityValidator identityValidator;

    @PostMapping("/api/accounts/link/start")
    public ResponseEntity<Map<String, String>> startLink(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();

        UserDTO myInfo =
                userService.getUser(userId);

        identityValidator.validateUserInformation(
                myInfo
        );

        String state =
                jwtTokenProvider.createLinkStateToken(
                        userId,
                        myInfo.getName(),
                        myInfo.getBirthDate()
                );

        List<Long> alreadyLinkedAccountIds =
                linkedBankAccountService.getLinkedAccountIds(
                        userId
                );

        String linkedIdsParam =
                alreadyLinkedAccountIds.stream()
                        .map(String::valueOf)
                        .collect(
                                Collectors.joining(",")
                        );

        String redirectUrl =
                UriComponentsBuilder
                        .fromUriString(
                                mockBankBaseUrl
                                        + "/link/start"
                        )
                        .queryParam(
                                "returnUrl",
                                backendBaseUrl
                                        + "/accounts/link/callback"
                        )
                        .queryParam(
                                "state",
                                state
                        )
                        .queryParam(
                                "excludeAccountIds",
                                linkedIdsParam
                        )
                        .toUriString();

        return ResponseEntity.ok(
                Map.of(
                        "redirectUrl",
                        redirectUrl
                )
        );
    }

    @GetMapping("/accounts/link/callback")
    public String linkCallback(
            @RequestParam String state,
            @RequestParam String userKey,
            @RequestParam(required = false) String accountIds,
            Model model
    ) {
        Long userId;
        try {
            userId = jwtTokenProvider.getUserIdFromLinkState(state)
                    .orElseThrow(UserErrorCode.INVALID_LINK_STATE::toException);
        } catch (DomainException e) {
            log.warn("[AccountLinkFlowController] 유효하지 않은 state - reason: {}", e.getMessage());
            return errorView(model, "유효하지 않거나 만료된 요청입니다.");
        }

        if (accountIds == null || accountIds.isBlank()) {
            log.info("[AccountLinkFlowController] 선택된 계좌 없이 콜백 진입 - userId: {}", userId);
            return errorView(model, "선택된 계좌가 없습니다.");
        }

        List<Long> ids;
        try {
            ids = Arrays.stream(accountIds.split(","))
                    .map(String::trim)
                    .filter(token -> !token.isEmpty())
                    .map(Long::parseLong)
                    .distinct()
                    .toList();
        } catch (NumberFormatException e) {
            log.warn("[AccountLinkFlowController] accountIds 파싱 실패 - userId: {}, accountIds: {}", userId, accountIds);
            return errorView(model, "계좌 연동에 실패했습니다.");
        }

        if (ids.isEmpty()) {
            return errorView(model, "선택된 계좌가 없습니다.");
        }

        try {
            accountLinkService.completeLink(userId, userKey, ids);
        } catch (DomainException e) {
            log.warn(
                    "[AccountLinkFlowController] 계좌 연동 실패 - userId: {}, accountIds: {}, errorCode: {}",
                    userId, ids, e.getErrorCode()
            );
            return errorView(model, "계좌 연동에 실패했습니다.");
        }

        model.addAttribute("success", true);
        return "link/link-complete";
    }

    private String errorView(Model model, String message) {
        model.addAttribute("success", false);
        model.addAttribute("errorMessage", message);
        return "link/link-complete";
    }
}