package org.teamsai.saibackend.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.teamsai.saibackend.domain.user.dto.request.UserLoginRequest;
import org.teamsai.saibackend.domain.user.dto.request.UserSignUpRequest;
import org.teamsai.saibackend.domain.user.dto.response.UserLoginResponse;
import org.teamsai.saibackend.domain.user.dto.response.UserSignUpResponse;
import org.teamsai.saibackend.domain.user.service.AuthService;

@Tag(
        name = "인증/인가 API",
        description = "회원가입, 로그인 등 인증 관련 API"
)
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
    public UserLoginResponse login(
            @Valid @RequestBody UserLoginRequest request
    ) {
        return authService.login(request);
    }
}