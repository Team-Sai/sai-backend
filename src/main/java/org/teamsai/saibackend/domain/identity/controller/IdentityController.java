package org.teamsai.saibackend.domain.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.identity.dto.request.IdentityPrepareRequest;
import org.teamsai.saibackend.domain.identity.dto.response.IdentityCompleteResponse;
import org.teamsai.saibackend.domain.identity.dto.response.IdentityPrepareResponse;
import org.teamsai.saibackend.domain.identity.service.IdentityService;

@Tag(
        name = "본인인증 API",
        description = "포트원 본인인증 요청 준비 및 결과 검증 API"
)
@Controller
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;


    @GetMapping("/identity-test")
    public String identityTestPage() {
        return "identity/identity-test";
    }

    @Operation(
            summary = "본인인증 요청 준비",
            description = "본인인증 요청을 생성하고 포트원 SDK 호출에 필요한 정보를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "본인인증 요청 생성 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 본인인증 목적"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            )
    })
    @ResponseBody
    @PostMapping("/api/identity-verifications")
    public ResponseEntity<IdentityPrepareResponse> prepare(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId")
            Long userId,

            @Valid
            @RequestBody
            IdentityPrepareRequest request
    ) {
        IdentityPrepareResponse response =
                identityService.prepare(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "본인인증 완료 처리",
            description = "포트원 서버에서 인증 결과를 조회하고 현재 로그인 회원과 동일인인지 검증합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "본인인증 완료"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "본인인증 실패 또는 회원정보 불일치"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "다른 사용자의 본인인증 요청"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "본인인증 요청을 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "본인인증 미완료 또는 처리할 수 없는 상태"
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "포트원 API 호출 실패"
            )
    })
    @ResponseBody
    @PostMapping(
            "/api/identity-verifications/{identityVerificationId}/complete"
    )
    public ResponseEntity<IdentityCompleteResponse> complete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId")
            Long userId,

            @Parameter(
                    description = "본인인증 요청 식별값",
                    example = "identity-verification-a1b2c3d4"
            )
            @PathVariable
            String identityVerificationId
    ) {
        IdentityCompleteResponse response =
                identityService.complete(
                        userId,
                        identityVerificationId
                );

        return ResponseEntity.ok(response);
    }
}