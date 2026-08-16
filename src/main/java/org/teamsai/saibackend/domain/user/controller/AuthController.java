package org.teamsai.saibackend.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.user.dto.UserLoginDTO;
import org.teamsai.saibackend.domain.user.dto.request.UserLoginRequest;
import org.teamsai.saibackend.domain.user.dto.request.UserSignUpRequest;
import org.teamsai.saibackend.domain.user.dto.response.AccessTokenResponse;
import org.teamsai.saibackend.domain.user.dto.response.UserLoginResponse;
import org.teamsai.saibackend.domain.user.dto.response.UserSignUpResponse;
import org.teamsai.saibackend.domain.user.service.AuthService;

import java.time.Duration;

@Tag(
        name = "인증/인가 API",
        description = "회원가입, 로그인 등 인증 관련 API"
)
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;

    @GetMapping("/login")
    public String loginPage() {
        return "user/login";
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "user/signup";
    }

    @Operation(
            summary = "회원가입",
            description = "새로운 회원의 정보를 입력받아 계정을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 (이메일 형식이 아니거나 필수값 누락)"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 존재하는 이메일"
            )
    })
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/auth/signup")
    public UserSignUpResponse signUp(
            @Valid @RequestBody UserSignUpRequest request
    ) {
        return authService.signUp(request);
    }

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 검증하여 인증 토큰 및 로그인 정보를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "비밀번호 불일치 또는 존재하지 않는 회원"
            )
    })
    @ResponseBody
    @PostMapping("/api/auth/login")
    public ResponseEntity<UserLoginResponse> login(
            @Valid @RequestBody UserLoginRequest request
    ) {
        UserLoginDTO loginDTO =
                authService.login(request);

        ResponseCookie refreshTokenCookie =
                ResponseCookie.from(
                                "refreshToken",
                                loginDTO.getRefreshToken()
                        )
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite("Lax")
                        .path("/api/auth")
                        .maxAge(Duration.ofDays(14))
                        .build();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .body(
                        UserLoginResponse.from(
                                loginDTO
                        )
                );
    }

    @Operation(
            summary = "Access Token 재발급",
            description = "Refresh Token을 검증하여 새로운 Access Token을 발급합니다."
    )
    @ResponseBody
    @PostMapping("/api/auth/reissue")
    public AccessTokenResponse reissue(
            @CookieValue(
                    value = "refreshToken",
                    required = false
            )
            String refreshToken
    ) {
        return authService.reissue(
                refreshToken
        );
    }

    @Operation(
            summary = "로그아웃",
            description = "Refresh Token을 삭제하여 로그아웃합니다."
    )
    @ResponseBody
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(
                    value = "refreshToken",
                    required = false
            )
            String refreshToken
    ) {
        authService.logout(refreshToken);

        ResponseCookie expiredCookie =
                ResponseCookie.from(
                                "refreshToken",
                                ""
                        )
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite("Lax")
                        .path("/api/auth")
                        .maxAge(Duration.ZERO)
                        .build();

        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        expiredCookie.toString()
                )
                .build();
    }
}